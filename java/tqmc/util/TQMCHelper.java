package tqmc.util;

import com.google.common.collect.Lists;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.ExceptionConverter;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.ListItem;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.mail.Session;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kotlin.Pair;
import org.apache.commons.lang.text.StrSubstitutor;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import prerna.om.Insight;
import prerna.sablecc2.om.GenRowStruct;
import prerna.sablecc2.om.NounStore;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.AssetUtility;
import prerna.util.EmailUtility;
import prerna.util.SocialPropertiesUtil;
import prerna.util.Utility;
import prerna.util.sql.RdbmsTypeEnum;
import tqmc.domain.base.CaseDetails;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.Filter;
import tqmc.domain.base.Filter.Comparison;
import tqmc.domain.base.GetListPayload;
import tqmc.domain.base.MTFData;
import tqmc.domain.base.ProductTables;
import tqmc.domain.base.QualityReviewEvent;
import tqmc.domain.base.Record;
import tqmc.domain.base.RecordFile;
import tqmc.domain.base.Sort;
import tqmc.domain.base.TQMCException;
import tqmc.domain.gtt.GttCaseTime;
import tqmc.domain.gtt.GttIrrMatch;
import tqmc.domain.gtt.GttRecordCaseEvent;
import tqmc.domain.gtt.GttWorkflow;
import tqmc.domain.mapper.CustomMapper;
import tqmc.domain.qu.dp.DpCase;
import tqmc.domain.qu.dp.DpWorkflow;
import tqmc.domain.qu.mcsc.McscCase;
import tqmc.domain.qu.mcsc.McscWorkflow;
import tqmc.domain.user.TQMCUserInfo;

public class TQMCHelper {

  private static final Logger LOGGER = LogManager.getLogger(TQMCHelper.class);

  public static String getGroupConcatFunctionSyntax(RdbmsTypeEnum engineType) {
    if (engineType == RdbmsTypeEnum.SQL_SERVER) {
      return "STRING_AGG";
    } else {
      return "LISTAGG";
    }
  }

  public static Map<String, Object> getQuRecord(Connection con, String recordId)
      throws SQLException {
    String query =
        "SELECT TOP(1) qr.RECORD_ID, qr.ALIAS_RECORD_ID, qr.CLAIM_NUMBER, qr.PATIENT_LAST_NAME, qr.PATIENT_FIRST_NAME, qr.REQUESTED_AT, qr.RECEIVED_AT, qr.MISSING_REQUESTED_AT, qr.MISSING_RECEIVED_AT, qr.CASE_LENGTH_DAYS, qr.UPDATED_AT FROM QU_RECORD qr WHERE qr.RECORD_ID = ?";

    Map<String, Object> output = new HashMap<>();

    try (PreparedStatement ps = con.prepareStatement(query)) {
      ps.setString(1, recordId);
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        output.put("record_id", rs.getString(TQMCConstants.QU_RECORD_RECORD_ID));
        output.put("alias_record_id", rs.getString(TQMCConstants.ALIAS_RECORD_ID));
        output.put("claim_number", rs.getString(TQMCConstants.CLAIM_NUMBER));
        output.put("patient_last_name", rs.getString(TQMCConstants.PATIENT_LAST_NAME));
        output.put("patient_first_name", rs.getString(TQMCConstants.PATIENT_FIRST_NAME));
        output.put(
            "requested_at",
            ConversionUtils.getLocalDateStringFromDate(rs.getDate(TQMCConstants.REQUESTED_AT)));
        output.put(
            "received_at",
            ConversionUtils.getLocalDateStringFromDate(rs.getDate(TQMCConstants.RECEIVED_AT)));
        output.put(
            "missing_requested_at",
            ConversionUtils.getLocalDateStringFromDate(
                rs.getDate(TQMCConstants.MISSING_REQUESTED_AT)));
        output.put(
            "missing_received_at",
            ConversionUtils.getLocalDateStringFromDate(
                rs.getDate(TQMCConstants.MISSING_RECEIVED_AT)));
        output.put("case_length_days", rs.getString(TQMCConstants.CASE_LENGTH_DAYS));
        output.put("updated_at", rs.getString(TQMCConstants.QU_RECORD_UPDATED_AT));

        // Retrieve the product table
        ProductTables product = getQuProductTable(con, recordId);

        // Retrieve the list of files
        List<RecordFile> files = getRecordFiles(con, product, recordId);
        List<Map<String, Object>> outFiles = new ArrayList<>();
        for (RecordFile rf : files) {
          Map<String, Object> innerMap = new HashMap<>();
          innerMap.put("record_file_id", rf.getRecordFileId());
          innerMap.put("file_name", rf.getFileName());
          innerMap.put("category", rf.getCategory());
          outFiles.add(innerMap);
        }
        output.put("files", outFiles);

        // Return whether the record has assigned cases
        output.put("case_status", getQuCaseStatus(con, recordId, product));

      } else {
        throw new TQMCException(ErrorCode.NOT_FOUND);
      }
    }

    return output;
  }

  public static String getQuCaseStatus(Connection con, String recordId, ProductTables product)
      throws SQLException {
    String query =
        "SELECT w.STEP_STATUS FROM "
            + product.getWorkflowTable()
            + " w INNER JOIN "
            + product.getCaseTable()
            + " c ON c.CASE_ID = w.CASE_ID INNER JOIN "
            + product.getRecordTable()
            + " qr ON qr.RECORD_ID = c.RECORD_ID WHERE qr.RECORD_ID = ? AND w.IS_LATEST = 1";
    try (PreparedStatement ps = con.prepareStatement(query)) {
      ps.setString(1, recordId);
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        return rs.getString("STEP_STATUS");
      }
      throw new TQMCException(ErrorCode.BAD_REQUEST);
    }
  }

  public static String getCaseStatus(
      Connection con, String caseId, ProductTables product, String caseType) throws SQLException {
    String query =
        "SELECT STEP_STATUS FROM "
            + product.getWorkflowTable()
            + " wf WHERE CASE_ID = ? AND IS_LATEST = 1"
            + (caseType != null ? " AND CASE_TYPE = ? " : "");
    try (PreparedStatement ps = con.prepareStatement(query)) {
      ps.setString(1, caseId);
      if (caseType != null) {
        ps.setString(2, caseType);
      }
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        return rs.getString("STEP_STATUS");
      }
      throw new TQMCException(ErrorCode.BAD_REQUEST);
    }
  }

  public static ProductTables getQuProductTable(Connection con, String recordId)
      throws SQLException {
    String careContractorType = null;
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT CARE_CONTRACTOR_TYPE FROM MN_CARE_CONTRACTOR mcc INNER JOIN QU_RECORD qr ON qr.CARE_CONTRACTOR_ID = mcc.CARE_CONTRACTOR_ID AND qr.RECORD_ID = ?")) {
      ps.setString(1, recordId);
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        careContractorType = rs.getString("CARE_CONTRACTOR_TYPE");
      } else {
        throw new TQMCException(
            ErrorCode.INTERNAL_SERVER_ERROR, "No care contractor associated with this record.");
      }
      if (rs.next()) {
        throw new TQMCException(
            ErrorCode.INTERNAL_SERVER_ERROR,
            "Multiple care contractors associated with this record.");
      }
    }
    if (careContractorType == null || careContractorType.equalsIgnoreCase("Other")) {
      throw new TQMCException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Invalid care contractor entry in DB");
    }
    return (careContractorType.equalsIgnoreCase(TQMCConstants.MCSC))
        ? ProductTables.MCSC
        : ProductTables.DP;
  }

  public static <T> T getPayloadObject(
      NounStore nounStore, String[] keysToGet, Class<T> targetClass) {
    Map<String, Object> payloadMap = new HashMap<>();
    for (String key : keysToGet) {
      if ("no keys defined".equals(key)) {
        break;
      }
      GenRowStruct grs = nounStore.getNoun(key);
      if (grs == null || grs.isEmpty()) {
        payloadMap.put(key, null);
      } else {
        payloadMap.put(key, grs.getAllValues());
      }
    }
    T payload;
    try {
      payload = CustomMapper.PAYLOAD_MAPPER.convertValue(payloadMap, targetClass);
    } catch (IllegalArgumentException e) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST, "Invalid request: inputs could not be parsed", e);
    }
    return payload;
  }

  public static GetListPayload getListPayloadObject(
      NounStore nounStore, List<String> validSorts, List<String> validFilters) {
    GetListPayload payload =
        getPayloadObject(nounStore, TQMCConstants.LIST_REACTOR_ARGUMENTS, GetListPayload.class);

    if (payload.getSort() != null) {
      Iterator<Sort> iter = payload.getSort().iterator();
      while (iter.hasNext()) {
        Sort sort = iter.next();
        if (sort.getField() == null) {
          iter.remove();
          continue;
        }

        String fieldName = sort.getField().toUpperCase();
        if (!validSorts.contains(fieldName)) {
          throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid sort field");
        }
      }
    }
    if (payload.getFilter() != null) {
      Map<String, Map<Comparison, List<String>>> collatedFilters = new LinkedHashMap<>();

      Iterator<Filter> iter = payload.getFilter().iterator();
      while (iter.hasNext()) {
        Filter filter = iter.next();

        if (filter.getField() == null) {
          iter.remove();
          continue;
        }

        String fieldName = filter.getField().toUpperCase();
        if (!validFilters.contains(fieldName)) {
          throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid filter field");
        }

        if (collatedFilters.containsKey(fieldName)) {
          Map<Comparison, List<String>> collatedFilter = collatedFilters.get(fieldName);
          List<String> orValues = collatedFilter.get(filter.getOperator());
          if (orValues == null) {
            collatedFilter.put(filter.getOperator(), Lists.newArrayList(filter.getValue()));
          } else {
            orValues.add(filter.getValue());
          }
        } else {
          Map<Comparison, List<String>> collatedFilter = new LinkedHashMap<>();
          collatedFilter.put(filter.getOperator(), Lists.newArrayList(filter.getValue()));
          collatedFilters.put(fieldName, collatedFilter);
        }
      }

      payload.setCollatedFilters(collatedFilters);
    }

    return payload;
  }

  /**
   * Check the return of a reactor for an error rather than proper value. If an error is found,
   * throw said error.
   *
   * @param value return from a reactor which may or may not contain an error
   */
  public static void checkIfTqmcException(Object reactorExecuteValue) {
    /**
     * TODO: May need to turn this into a boolean so that instead of creating a new error here, we
     * pass back that same thing that triggered this method
     */
    if (reactorExecuteValue instanceof Map) {
      Map<?, ?> errorMap = (Map<?, ?>) reactorExecuteValue;
      Set<?> expectedKeys = new HashSet<>(Arrays.asList("code", "message"));
      if (errorMap.keySet().equals(expectedKeys)) {
        int errorCode = Integer.parseInt(errorMap.get("code").toString());
        String errorMessage = errorMap.get("message").toString();
        throw new TQMCException(ErrorCode.fromCode(errorCode), errorMessage);
      }
    }
  }

  /**
   * Gets a list of MTFNames based on recordId
   *
   * @param con connection to DB
   * @param recordId the recordId to filter on
   * @return a list of MTF Names
   * @throws SQLException
   */
  public static List<String> getMTFNamesForSocRecordId(Connection con, String recordId)
      throws SQLException {

    String query =
        "Select MTF_NAME from MTF mtf inner join SOC_RECORD_MTF sf on sf.DMIS_ID = mtf.DMIS_ID inner join SOC_RECORD sr on sf.RECORD_ID = sr.RECORD_ID where sr.RECORD_ID=?";

    List<String> outMTFs = new ArrayList<>();

    try (PreparedStatement ps = con.prepareStatement(query)) {
      ps.setString(1, recordId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          outMTFs.add(rs.getString("MTF_NAME"));
        }
      }
    }

    return outMTFs;
  }

  public static List<MTFData> getMtfs(Connection con) throws SQLException {
    List<MTFData> output = new ArrayList<>();
    try (PreparedStatement mtfQuery =
        con.prepareStatement(
            "SELECT DMIS_ID, MTF_NAME, COALESCE(alias_mtf_name, mtf_name) AS ALIAS_MTF_NAME FROM MTF ORDER BY LOWER(COALESCE(alias_mtf_name, mtf_name)) ASC"); ) {
      if (mtfQuery.execute()) {
        ResultSet rs = mtfQuery.getResultSet();
        while (rs.next()) {
          String id = rs.getString("DMIS_ID");
          String name = rs.getString("MTF_NAME");
          String alias = rs.getString("ALIAS_MTF_NAME");
          MTFData row = new MTFData(name, id, alias);
          output.add(row);
        }
      }
    }
    return output;
  }

  public static Set<String> getAssignedCPEIDs(Connection con, String recordId) throws SQLException {

    Set<String> assignedCPEIDs = new HashSet<>();

    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT scpe.CASE_PROVIDER_EVALUATION_ID AS CPEID FROM SOC_CASE_PROVIDER_EVALUATION scpe INNER JOIN SOC_WORKFLOW sw ON scpe.CASE_ID = sw.CASE_ID INNER JOIN SOC_CASE sc ON sc.CASE_ID = scpe.CASE_ID AND sc.RECORD_ID = ? WHERE sw.is_latest = 1 AND sw.STEP_STATUS IN ('not_started', 'in_progress', 'completed')")) {

      ps.setString(1, recordId);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        assignedCPEIDs.add(rs.getString("CPEID"));
      }
    }
    return assignedCPEIDs;
  }

  /**
   * @deprecated Similar to getListQuery() but returns a modified query with sort, filter and
   *     pagination options specific to GetSocCases as each operation has to be carried out on a
   *     RECORD_ID level granularity.
   * @param userId
   * @param payload collection of operations a user wants to execute
   * @param baseQuery CTEs to use to base this query.
   * @param arguments values of arguments for each operation
   * @param defaultSort list of default columns to sort on if nothing specified
   * @return a String consisting of the final query ready for execution
   */
  public static String getListQuerySoc(
      String userId, GetListPayload payload, List<String> arguments, List<String> defaultSort) {

    if (userId != null) {
      arguments.add(userId);
    }

    StringBuilder sb = new StringBuilder();

    // StepStatusSummary subquery
    sb.append("WITH StepStatusSummary AS (")
        .append("SELECT sr.RECORD_ID, ")
        .append("COUNT(CASE WHEN scw.STEP_STATUS = 'completed' THEN 1 END) AS COMPLETED_COUNT, ")
        .append("COUNT(CASE WHEN scw.STEP_STATUS = 'unassigned' THEN 1 END) AS UNASSIGNED_COUNT, ")
        .append("COUNT(DISTINCT s.CASE_ID) AS TOTAL_CASE_COUNT ")
        .append("FROM SOC_RECORD sr ")
        .append("INNER JOIN (")
        .append("SELECT sc.RECORD_ID, sc.CASE_ID FROM SOC_CASE sc ")
        .append(
            "UNION SELECT snr.RECORD_ID, snr.NURSE_REVIEW_ID AS CASE_ID FROM SOC_NURSE_REVIEW snr) s ")
        .append("ON sr.RECORD_ID = s.RECORD_ID ")
        .append("INNER JOIN SOC_WORKFLOW scw ON s.CASE_ID = scw.CASE_ID AND scw.IS_LATEST = 1 ")
        .append("GROUP BY sr.RECORD_ID), ");

    // CaseQuery subquery
    sb.append("CaseQuery AS (")
        .append("SELECT sc.RECORD_ID, sc.CASE_ID, ")
        .append("spec.SPECIALTY_NAME, sc.SPECIALTY_ID, ")
        .append("CASE WHEN COUNT(DISTINCT srf.FILE_NAME) > 0 THEN 1 ELSE 0 END AS HAS_FILES, ")
        .append("sc.DUE_DATE_REVIEW, sc.DUE_DATE_DHA, ")
        .append("sc.ASSIGNED_AT, ")
        .append("scw2.STEP_STATUS, scw2.CASE_TYPE, sc.USER_ID, ")
        .append("sr.ALIAS_RECORD_ID, ")
        .append("CONCAT(sr.PATIENT_LAST_NAME, ', ', sr.PATIENT_FIRST_NAME) AS PATIENT_NAME, ")
        .append("sr.PATIENT_FIRST_NAME, sr.PATIENT_LAST_NAME ")
        .append("FROM SOC_CASE sc ")
        .append("LEFT JOIN TQMC_SPECIALTY spec ON sc.SPECIALTY_ID = spec.SPECIALTY_ID ")
        .append("LEFT JOIN SOC_RECORD sr ON sc.RECORD_ID = sr.RECORD_ID ")
        .append(
            "LEFT JOIN SOC_RECORD_FILE srf ON sr.RECORD_ID = srf.RECORD_ID AND srf.DELETED_AT IS NULL ")
        .append("LEFT JOIN SOC_WORKFLOW scw2 ON sc.CASE_ID = scw2.CASE_ID AND scw2.IS_LATEST = 1 ")
        .append("WHERE sc.DELETED_AT IS NULL ")
        .append("GROUP BY sc.RECORD_ID, sc.CASE_ID, spec.SPECIALTY_NAME, sc.SPECIALTY_ID, ")
        .append("sc.DUE_DATE_REVIEW, sc.DUE_DATE_DHA, ")
        .append("sc.ASSIGNED_AT, scw2.STEP_STATUS, scw2.CASE_TYPE, sc.USER_ID), ");

    // InnerQuery subquery
    sb.append("InnerQuery AS (")
        .append("SELECT * FROM CaseQuery ")
        .append("UNION ALL ")
        .append("SELECT snr.RECORD_ID, snr.NURSE_REVIEW_ID AS CASE_ID, ")
        .append("NULL AS SPECIALTY_NAME, NULL AS SPECIALTY_ID, NULL AS HAS_FILES, ")
        .append("snr.DUE_DATE_REVIEW, snr.DUE_DATE_DHA, ")
        .append("snr.ASSIGNED_AT, ")
        .append("scw2.STEP_STATUS, scw2.CASE_TYPE, snr.USER_ID, ")
        .append("sr.ALIAS_RECORD_ID, ")
        .append("CONCAT(sr.PATIENT_LAST_NAME, ', ', sr.PATIENT_FIRST_NAME) AS PATIENT_NAME, ")
        .append("sr.PATIENT_FIRST_NAME, sr.PATIENT_LAST_NAME ")
        .append("FROM SOC_NURSE_REVIEW snr ")
        .append("LEFT JOIN SOC_RECORD sr ON snr.RECORD_ID = sr.RECORD_ID ")
        .append(
            "LEFT JOIN SOC_WORKFLOW scw2 ON snr.NURSE_REVIEW_ID = scw2.CASE_ID AND scw2.IS_LATEST = 1 ")
        .append("WHERE snr.DELETED_AT IS NULL), ");

    // OuterQuery subquery
    sb.append("OuterQuery AS (")
        .append("SELECT InnerQuery.RECORD_ID, InnerQuery.CASE_ID, ")
        .append(
            "InnerQuery.SPECIALTY_NAME AS CASE_SPECIALTY_NAME, InnerQuery.SPECIALTY_ID AS CASE_SPECIALTY_ID, ")
        .append("InnerQuery.HAS_FILES, ")
        .append(
            "InnerQuery.ASSIGNED_AT AS ASSIGNED_DATE, InnerQuery.STEP_STATUS AS CASE_STATUS, InnerQuery.CASE_TYPE, ")
        .append("InnerQuery.DUE_DATE_REVIEW, InnerQuery.DUE_DATE_DHA, ")
        .append("CASE WHEN ss.COMPLETED_COUNT = ss.TOTAL_CASE_COUNT THEN 'completed' ")
        .append(
            "WHEN ss.UNASSIGNED_COUNT > 0 THEN 'awaiting_assignment' ELSE 'assigned' END AS RECORD_STATUS_STR, ")
        .append("CASE WHEN ss.COMPLETED_COUNT = ss.TOTAL_CASE_COUNT THEN 2 ")
        .append("WHEN ss.UNASSIGNED_COUNT > 0 THEN 0 ELSE 1 END AS RECORD_STATUS, ")
        .append("InnerQuery.USER_ID AS ASSIGNED_TO_USER_USER_ID, InnerQuery.ALIAS_RECORD_ID, ")
        .append(
            "InnerQuery.PATIENT_NAME, InnerQuery.PATIENT_FIRST_NAME, InnerQuery.PATIENT_LAST_NAME, ")
        .append(
            "CONCAT(InnerQuery.PATIENT_LAST_NAME, ', ', InnerQuery.PATIENT_FIRST_NAME, ' ', InnerQuery.PATIENT_LAST_NAME, ' ', InnerQuery.PATIENT_FIRST_NAME) AS PATIENT_NAME_QUERY ")
        .append("FROM InnerQuery ")
        .append("LEFT JOIN StepStatusSummary ss ON InnerQuery.RECORD_ID = ss.RECORD_ID) ");

    // Final query
    sb.append("SELECT OuterQuery.*, r.TOTAL_ROW_COUNT FROM (")
        .append(
            "SELECT RECORD_ID, RECORD_STATUS_STR, RECORD_STATUS, ALIAS_RECORD_ID, PATIENT_NAME, COUNT(*) OVER() AS TOTAL_ROW_COUNT FROM (")
        .append(
            "SELECT RECORD_ID, RECORD_STATUS_STR, RECORD_STATUS, ALIAS_RECORD_ID, PATIENT_NAME, CASE WHEN SUM(IS_MATCH) > 0 THEN 1 ELSE 0 END AS IS_MATCH FROM (")
        .append("SELECT oq.RECORD_ID, oq.CASE_SPECIALTY_NAME, mtf.MTF_NAME, ")
        .append(
            "oq.CASE_STATUS, oq.RECORD_STATUS_STR, oq.RECORD_STATUS, oq.CASE_TYPE, oq.CASE_ID, oq.ALIAS_RECORD_ID, ")
        .append("oq.PATIENT_FIRST_NAME, oq.PATIENT_LAST_NAME, oq.PATIENT_NAME, ");

    StringBuilder isMatchColumn =
        new StringBuilder("(1)"); // Filters go here, default as 1 meaning all rows OK
    getFilterStringSoc(payload, isMatchColumn, arguments);
    sb.append(isMatchColumn).append(" AS IS_MATCH ");

    sb.append("FROM OuterQuery oq ")
        .append("LEFT JOIN SOC_RECORD_MTF sf ON oq.RECORD_ID = sf.RECORD_ID ")
        .append("LEFT JOIN MTF mtf ON sf.DMIS_ID = mtf.DMIS_ID) ")
        .append(
            "GROUP BY RECORD_ID, RECORD_STATUS, RECORD_STATUS_STR, ALIAS_RECORD_ID, PATIENT_NAME) ")
        .append("WHERE IS_MATCH = 1 ");

    StringBuilder orderBy = new StringBuilder(""); // Sorts go here, record and case level
    appendOrderByString(payload, orderBy, defaultSort);
    StringBuilder orderByInner = new StringBuilder("");
    appendOrderByString(payload, orderByInner, defaultSort);
    sb.append(orderByInner);

    StringBuilder offset = new StringBuilder(""); // Pagination goes here
    appendPaginationString(payload, offset);
    sb.append(offset);

    sb.append(") r INNER JOIN OuterQuery ON OuterQuery.RECORD_ID = r.RECORD_ID ");
    sb.append(orderBy);

    return sb.toString();
  }

  public static String getListQuery(
      String userId, GetListPayload payload, String baseQuery, List<String> arguments) {
    return getListQuery(userId, payload, baseQuery, arguments, null);
  }

  public static String getListQuery(
      String userId,
      GetListPayload payload,
      String baseQuery,
      List<String> arguments,
      List<String> defaultSort) {
    StringBuilder sb = new StringBuilder(baseQuery);

    if (userId != null) {
      arguments.add(userId);
    }

    StringBuilder filter = new StringBuilder();
    appendFilterString(payload, arguments, filter);
    if (filter.length() > 0) {
      sb.append(" WHERE ");
      sb.append(filter);
    }

    appendOrderByString(payload, sb, defaultSort);

    appendPaginationString(payload, sb);

    return sb.toString();
  }

  private static void getFilterStringSoc(
      GetListPayload payload, StringBuilder sb, List<String> arguments) {
    if (payload.getCollatedFilters() != null && !payload.getCollatedFilters().isEmpty()) {
      boolean first = true;
      sb.setLength(0);
      for (String fieldName : payload.getCollatedFilters().keySet()) {
        Map<Comparison, List<String>> collatedFilter = payload.getCollatedFilters().get(fieldName);

        fieldName = fieldName.equals("RECORD_ID") ? "oq.RECORD_ID" : fieldName;

        for (Comparison comparison : collatedFilter.keySet()) {
          List<String> fieldValues = collatedFilter.get(comparison);

          if (first) {
            sb.append(" ( ( ");
            first = false;
          } else {
            sb.append(" AND ( ");
          }

          if (Filter.Comparison.LIKE == comparison) {
            for (int i = 0; i < fieldValues.size(); i++) {
              if (i != 0) {
                sb.append(" OR ");
              }

              String value = fieldValues.get(i);
              if (value == null) {
                sb.append(fieldName).append(comparison.getNullSql());
              } else {
                sb.append(fieldName).append(comparison.getSql()).append(" ?");
                arguments.add("%" + fieldValues.get(i) + "%");
              }
            }
          } else {
            for (int i = 0; i < fieldValues.size(); i++) {
              if (i != 0) {
                sb.append(" OR ");
              }

              String value = fieldValues.get(i);
              if (value == null) {
                sb.append(fieldName).append(comparison.getNullSql());
              } else {
                sb.append(fieldName).append(comparison.getSql()).append(" ?");
                arguments.add(fieldValues.get(i));
              }
            }
          }
          sb.append(" ) ");
        }
      }
      sb.append(")");
    }
  }

  public static void appendFilterString(
      GetListPayload payload, List<String> arguments, StringBuilder sb) {
    if (payload.getCollatedFilters() != null && !payload.getCollatedFilters().isEmpty()) {
      boolean first = true;
      for (String fieldName : payload.getCollatedFilters().keySet()) {
        Map<Comparison, List<String>> collatedFilter = payload.getCollatedFilters().get(fieldName);

        for (Comparison comparison : collatedFilter.keySet()) {
          List<String> fieldValues = collatedFilter.get(comparison);

          if (first) {
            sb.append(" (");
            first = false;
          } else {
            sb.append(" AND (");
          }

          if (Filter.Comparison.LIKE == comparison) {
            for (int i = 0; i < fieldValues.size(); i++) {
              if (i != 0) {
                sb.append(" OR ");
              }

              String value = fieldValues.get(i);
              if (value == null) {
                sb.append(fieldName).append(comparison.getNullSql());
              } else {
                sb.append(fieldName).append(comparison.getSql()).append(" ?");
                arguments.add("%" + fieldValues.get(i) + "%");
              }
            }
          } else {
            for (int i = 0; i < fieldValues.size(); i++) {
              if (i != 0) {
                sb.append(" OR ");
              }

              String value = fieldValues.get(i);
              if (value == null) {
                sb.append(fieldName).append(comparison.getNullSql());
              } else {
                sb.append(fieldName).append(comparison.getSql()).append(" ?");
                arguments.add(fieldValues.get(i));
              }
            }
          }
          sb.append(")");
        }
      }
    }
  }

  public static void appendOrderByString(GetListPayload payload, StringBuilder sb) {
    appendOrderByString(payload, sb, null);
  }

  public static void appendOrderByString(
      GetListPayload payload, StringBuilder sb, List<String> defaultSort) {
    if (payload.getSort() != null) {
      Sort s = payload.getSort().get(0);
      sb.append(" ORDER BY ");
      if (s.getCaseInsensitive()) {
        sb.append("LOWER(")
            .append(s.getField().toUpperCase())
            .append(") ")
            .append(s.getSort().name())
            .append(", ");
      }
      sb.append(s.getField().toUpperCase()).append(" ").append(s.getSort().name());
      for (int i = 1; i < payload.getSort().size(); i++) {
        s = payload.getSort().get(i);
        if (s.getCaseInsensitive()) {
          sb.append(", LOWER(")
              .append(s.getField().toUpperCase())
              .append(") ")
              .append(s.getSort().name());
        }
        sb.append(", ").append(s.getField()).append(" ").append(s.getSort().name());
      }
    }
    if (defaultSort != null) {
      int index = 0;
      while (index < defaultSort.size() && sb.indexOf(defaultSort.get(index)) != -1) {
        index++;
      }
      if (payload.getSort() == null && index < defaultSort.size()) {
        sb.append(" ORDER BY ");
        sb.append(defaultSort.get(index++));
      }
      for (int i = index; i < defaultSort.size(); i++) {
        if (sb.indexOf(defaultSort.get(i)) == -1) {
          sb.append(", ").append(defaultSort.get(i));
        }
      }
    }
  }

  public static void appendPaginationString(GetListPayload payload, StringBuilder sb) {
    if (payload.getPagination() != null
        && payload.getPagination().getPageSize() != null
        && payload.getPagination().getPage() != null) {
      int pageSize = payload.getPagination().getPageSize();
      sb.append(" OFFSET ")
          .append(Integer.toString(payload.getPagination().getPage() * pageSize))
          .append(" ROWS");
      sb.append(" FETCH NEXT ").append(Integer.toString(pageSize)).append(" ROWS ONLY");
    }
  }

  public static TQMCUserInfo getTQMCUserInfo(Connection con, String userId) throws SQLException {
    TQMCUserInfo tui = null;

    try (PreparedStatement ps =
        con.prepareStatement(
            "WITH UserProducts AS "
                + "(SELECT tu.USER_ID, STRING_AGG(tp.PRODUCT_ID, '||') "
                + "AS product_ids FROM TQMC_USER tu "
                + "LEFT OUTER JOIN TQMC_USER_PRODUCT tup ON tup.USER_ID = tu.USER_ID "
                + "LEFT OUTER JOIN TQMC_PRODUCT tp ON tp.PRODUCT_ID = tup.PRODUCT_ID AND tp.IS_ACTIVE = 1 "
                + "WHERE tu.USER_ID = ? GROUP BY tu.USER_ID) "
                + "SELECT tu.USER_ID, tu.IS_ACTIVE, tu.ROLE, tu.EMAIL, tu.FIRST_NAME, tu.LAST_NAME, "
                + "tu.PHONE, tu.NPI, tu.CREATED_AT, tu.UPDATED_AT, up.product_ids, "
                + "tus.specialty_id "
                + "FROM TQMC_USER tu LEFT OUTER JOIN UserProducts up "
                + "ON up.USER_ID = tu.USER_ID "
                + "LEFT OUTER JOIN TQMC_USER_SPECIALTY tus "
                + "ON tus.user_id = tu.USER_ID "
                + "WHERE tu.USER_ID = ?")) {
      ps.setString(1, userId);
      ps.setString(2, userId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();

        if (rs.next()) {
          tui = new TQMCUserInfo();

          tui.setUserId(rs.getString("USER_ID"));
          tui.setIsActive(rs.getInt("IS_ACTIVE") == 1);
          tui.setRole(rs.getString("ROLE"));
          tui.setEmail(rs.getString("EMAIL"));
          tui.setFirstName(rs.getString("FIRST_NAME"));
          tui.setLastName(rs.getString("LAST_NAME"));
          tui.setPhone(rs.getString("PHONE"));
          tui.setNpi(rs.getString("NPI"));
          tui.setCreatedAt(
              ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("CREATED_AT")));
          tui.setUpdatedAt(
              ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("UPDATED_AT")));

          String productIds = rs.getString("product_ids");
          if (productIds != null) {
            for (String productId : productIds.split("\\|\\|")) {
              tui.getProducts().add(productId);
            }
          }
          String specialtyId = rs.getString("specialty_id");

          if (specialtyId != null) {
            tui.getSpecialtyIds().add(specialtyId);
          }
        }

        while (rs.next()) {
          String specialtyId = rs.getString("specialty_id");
          if (specialtyId != null) {
            tui.getSpecialtyIds().add(specialtyId);
          }
        }
      }
    }
    return tui;
  }

  public static void createTQMCUser(Connection con, TQMCUserInfo tqmcUserInfo) throws SQLException {
    try (PreparedStatement ps =
        con.prepareStatement(
            "INSERT INTO TQMC_USER "
                + "(CREATED_AT, NPI, EMAIL, FIRST_NAME, IS_ACTIVE, LAST_NAME, PHONE, \"ROLE\", UPDATED_AT, USER_ID) "
                + "VALUES (?,?,?,?,?, ?,?,?,?,?)")) {
      int parameterIndex = 1;
      ps.setObject(parameterIndex++, tqmcUserInfo.getCreatedAt());
      ps.setString(parameterIndex++, tqmcUserInfo.getNpi());
      ps.setString(parameterIndex++, tqmcUserInfo.getEmail());
      ps.setString(parameterIndex++, tqmcUserInfo.getFirstName());
      ps.setInt(parameterIndex++, 1);
      ps.setString(parameterIndex++, tqmcUserInfo.getLastName());
      ps.setString(parameterIndex++, tqmcUserInfo.getPhone());
      ps.setString(parameterIndex++, tqmcUserInfo.getRole());
      ps.setObject(parameterIndex++, tqmcUserInfo.getUpdatedAt());
      ps.setString(parameterIndex++, tqmcUserInfo.getUserId());
      ps.execute();
    }

    if (tqmcUserInfo.getSpecialtyIds() != null && !tqmcUserInfo.getSpecialtyIds().isEmpty()) {
      try (PreparedStatement ps =
          con.prepareStatement(
              "INSERT INTO TQMC_USER_SPECIALTY "
                  + "(SPECIALTY_ID, USER_ID, USER_SPECIALTY_ID) "
                  + "VALUES (?,?,?)")) {
        for (String specialtyId : tqmcUserInfo.getSpecialtyIds()) {
          int parameterIndex = 1;
          ps.setString(parameterIndex++, specialtyId);
          ps.setString(parameterIndex++, tqmcUserInfo.getUserId());
          ps.setString(parameterIndex++, UUID.randomUUID().toString());
          ps.addBatch();
        }
        ps.executeBatch();
      }
    }

    if (tqmcUserInfo.getProducts() != null && !tqmcUserInfo.getProducts().isEmpty()) {
      try (PreparedStatement ps =
          con.prepareStatement(
              "INSERT INTO TQMC_USER_PRODUCT "
                  + "(PRODUCT_ID, USER_ID, USER_PRODUCT_ID) "
                  + "VALUES (?,?,?)")) {
        for (String productId : tqmcUserInfo.getProducts()) {
          int parameterIndex = 1;
          ps.setString(parameterIndex++, productId);
          ps.setString(parameterIndex++, tqmcUserInfo.getUserId());
          ps.setString(parameterIndex++, UUID.randomUUID().toString());
          ps.addBatch();
        }
        ps.executeBatch();
      }
    }
  }

  public static GttCaseTime getGttCaseTime(Connection con, String caseId) throws SQLException {
    GttCaseTime gct = null;

    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT TOP(1) CASE_TIME_ID, CASE_ID, CASE_TYPE, START_TIME, "
                + "STOP_TIME, CUMULATIVE_TIME FROM GTT_CASE_TIME WHERE CASE_ID = ? ORDER BY START_TIME DESC")) {
      ps.setString(1, caseId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {

          gct = new GttCaseTime();
          gct.setCaseTimeId(rs.getString("CASE_TIME_ID"));
          gct.setCaseId(rs.getString("CASE_ID"));
          gct.setCaseType(rs.getString("CASE_TYPE"));
          gct.setStartTime(
              ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("START_TIME")));
          gct.setEndTime(
              ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("STOP_TIME")));
          long lValue = rs.getLong("CUMULATIVE_TIME");
          if (!rs.wasNull()) {
            gct.setCumulativeTime(lValue);
          }
        }
      }
    }

    return gct;
  }

  public static Map<String, List<GttRecordCaseEvent>>
      getGttRecordCaseEventsForPairedCompletedAbstractorCase(Connection con, String caseId)
          throws SQLException {
    Map<String, List<GttRecordCaseEvent>> events = new LinkedHashMap<>();

    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT gcc.CASE_ID, gcc.ABS_1_CASE_ID, gcc.ABS_2_CASE_ID, grce.RECORD_CASE_EVENT_ID, grce.CASE_ID AS GRCE_CASE_ID, grce.EVENT_TYPE_TEMPLATE_ID, grce.TRIGGER_TEMPLATE_ID, "
                + "grce.HARM_CATEGORY_TEMPLATE_ID, grce.CASE_TYPE, grce.CONSENSUS_LINK_CASE_EVENT_ID, grce.PRESENT_ON_ADMISSION, grce.EVENT_DESCRIPTION, grce.UPDATED_AT "
                + "FROM GTT_ABSTRACTOR_CASE gac "
                + "INNER JOIN GTT_CONSENSUS_CASE gcc ON gac.CASE_ID IN (gcc.ABS_1_CASE_ID, gcc.ABS_2_CASE_ID) "
                + "LEFT OUTER JOIN GTT_WORKFLOW gw1 ON gw1.CASE_ID <> gac.CASE_ID AND gw1.CASE_ID = gcc.ABS_1_CASE_ID AND gw1.IS_LATEST = 1 AND gw1.STEP_STATUS = 'completed' "
                + "LEFT OUTER JOIN GTT_WORKFLOW gw2 ON gw2.CASE_ID <> gac.CASE_ID AND gw2.CASE_ID = gcc.ABS_2_CASE_ID AND gw2.IS_LATEST = 1 AND gw2.STEP_STATUS = 'completed' "
                + "LEFT OUTER JOIN GTT_RECORD_CASE_EVENT grce ON grce.CASE_ID IN (gw1.CASE_ID, gw2.CASE_ID) "
                + "WHERE gac.CASE_ID = ?")) {
      ps.setString(1, caseId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {

          if (events.isEmpty()) {
            String caseId0 = rs.getString("CASE_ID");
            String caseId1 = rs.getString("ABS_1_CASE_ID");
            String caseId2 = rs.getString("ABS_2_CASE_ID");
            events.put(caseId0, null);
            events.put(caseId1, new ArrayList<>());
            events.put(caseId2, new ArrayList<>());
          }

          String caseEventId = rs.getString("RECORD_CASE_EVENT_ID");
          if (caseEventId == null) {
            continue;
          }

          GttRecordCaseEvent e = new GttRecordCaseEvent();
          e.setRecordCaseEventId(caseEventId);
          e.setCaseId(rs.getString("GRCE_CASE_ID"));
          e.setEventTypeTemplateId(rs.getString("EVENT_TYPE_TEMPLATE_ID"));
          e.setTriggerTemplateId(rs.getString("TRIGGER_TEMPLATE_ID"));
          e.setHarmCategoryTemplateId(rs.getString("HARM_CATEGORY_TEMPLATE_ID"));
          e.setCaseType(rs.getString("CASE_TYPE"));
          e.setConsensusLinkCaseEventId(rs.getString("CONSENSUS_LINK_CASE_EVENT_ID"));
          e.setPresentOnAdmission(rs.getInt("PRESENT_ON_ADMISSION") == 1);
          e.setEventDescription(rs.getString("EVENT_DESCRIPTION")); // TODO: this is a clob
          e.setUpdatedAt(
              ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("UPDATED_AT")));
          events.get(e.getCaseId()).add(e);
        }
      }
    }

    return events;
  }

  public static void createRecordFiles(
      Connection con, ProductTables product, List<RecordFile> recordFiles) throws SQLException {
    if (product == null || recordFiles == null || recordFiles.isEmpty()) {
      return;
    }

    boolean isSoc = product.equals(ProductTables.SOC);

    String fieldsString = " (RECORD_FILE_ID, RECORD_ID, FILE_NAME, CATEGORY) VALUES (?, ?, ?, ?)";
    if (isSoc) {
      fieldsString =
          " (RECORD_FILE_ID, RECORD_ID, FILE_NAME, CATEGORY, SHOW_NURSE_REVIEW) VALUES (?, ?, ?, ?, ?)";
    }

    try (PreparedStatement ps =
        con.prepareStatement("INSERT INTO " + product.getFileTable() + fieldsString)) {
      for (RecordFile f : recordFiles) {
        int parameterIndex = 1;
        ps.setString(parameterIndex++, f.getRecordFileId());
        ps.setString(parameterIndex++, f.getRecordId());
        ps.setString(parameterIndex++, f.getFileName());
        ps.setString(parameterIndex++, f.getCategory());
        if (isSoc) {
          ps.setBoolean(parameterIndex++, f.getShowNurseReview());
        }
        ps.addBatch();
      }
      ps.executeBatch();
    }

    if (isSoc) {
      try (PreparedStatement ps =
          con.prepareStatement(
              "INSERT INTO SOC_CASE_FILE (RECORD_FILE_ID, CASE_FILE_ID, CASE_ID) VALUES (?, ?, ?)")) {
        for (RecordFile f : recordFiles) {
          for (String caseId : f.getCaseIds()) {
            int parameterIndex = 1;
            ps.setString(parameterIndex++, f.getRecordFileId());
            ps.setString(parameterIndex++, UUID.randomUUID().toString());
            ps.setString(parameterIndex++, caseId);
            ps.addBatch();
          }
        }
        ps.executeBatch();
      }
    }
  }

  public static void deleteRecordFiles(
      Connection con,
      ProductTables product,
      LocalDateTime currentTimestamp,
      List<RecordFile> recordFiles)
      throws SQLException {
    if (product == null || recordFiles == null || recordFiles.isEmpty()) {
      return;
    }

    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE " + product.getFileTable() + " SET DELETED_AT = ? WHERE RECORD_FILE_ID = ?")) {
      for (RecordFile f : recordFiles) {
        int parameterIndex = 1;
        ps.setObject(parameterIndex++, currentTimestamp);
        ps.setString(parameterIndex++, f.getRecordFileId());
        ps.addBatch();
      }
      ps.executeBatch();
    }
  }

  public static void updateCategoryRecordFiles(
      Connection con, ProductTables product, List<RecordFile> recordFiles) throws SQLException {
    if (product == null || recordFiles == null || recordFiles.isEmpty()) {
      return;
    }

    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE "
                + product.getFileTable()
                + " SET CATEGORY = ? "
                + " WHERE RECORD_FILE_ID = ? ")) {
      for (RecordFile f : recordFiles) {
        int parameterIndex = 1;
        ps.setString(parameterIndex++, f.getCategory());
        ps.setString(parameterIndex++, f.getRecordFileId());
        ps.addBatch();
      }
      ps.executeBatch();
    }
  }

  public static RecordFile getRecordFile(Connection con, ProductTables product, String recordFileId)
      throws SQLException {
    if (product == null) {
      return null;
    }
    RecordFile recordFile = null;
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT FILE_NAME, RECORD_ID, CATEGORY FROM "
                + product.getFileTable()
                + " WHERE RECORD_FILE_ID = ? AND DELETED_AT IS NULL")) {
      ps.setString(1, recordFileId);
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        recordFile = new RecordFile();
        recordFile.setRecordFileId(recordFileId);
        recordFile.setFileName(rs.getString(1));
        recordFile.setRecordId(rs.getString(2));
        recordFile.setCategory(rs.getString(3));
      }
    }
    return recordFile;
  }

  public static List<RecordFile> getRecordFiles(
      Connection con, ProductTables product, String recordId) throws SQLException {
    List<RecordFile> recordFiles = new ArrayList<>();

    if (product.equals(ProductTables.SOC)) {
      String query =
          "SELECT srf.FILE_NAME, srf.RECORD_FILE_ID, srf.CATEGORY, srf.SHOW_NURSE_REVIEW, sc.SPECIALTY_ID\r\n"
              + "FROM SOC_RECORD_FILE srf\r\n"
              + "INNER JOIN SOC_RECORD sr ON sr.RECORD_ID = srf.RECORD_ID\r\n"
              + "LEFT JOIN SOC_CASE_FILE scf ON scf.RECORD_FILE_ID = srf.RECORD_FILE_ID\r\n"
              + "LEFT JOIN SOC_CASE sc ON sc.CASE_ID = scf.CASE_ID\r\n"
              + "WHERE srf.RECORD_ID = ? AND srf.DELETED_AT IS NULL AND sc.DELETED_AT IS NULL AND sc.SUBMISSION_GUID IS NULL ";

      try (PreparedStatement ps = con.prepareStatement(query)) {
        ps.setString(1, recordId);
        ResultSet rs = ps.executeQuery();
        Map<String, RecordFile> recordFileMap = new HashMap<>();
        while (rs.next()) {
          String recordFileId = rs.getString("RECORD_FILE_ID");
          RecordFile recordFile = recordFileMap.getOrDefault(recordFileId, null);
          if (recordFile == null) {
            recordFile = new RecordFile();
            recordFile.setFileName(rs.getString("FILE_NAME"));
            recordFile.setRecordFileId(rs.getString("RECORD_FILE_ID"));
            recordFile.setCategory(rs.getString("CATEGORY"));
            recordFile.setShowNurseReview(rs.getInt("SHOW_NURSE_REVIEW") == 1);
            recordFileMap.put(recordFileId, recordFile);
          }
          String specialtyId = rs.getString("SPECIALTY_ID");
          if (specialtyId != null) {
            recordFileMap.get(recordFileId).addSpecialtyId(specialtyId);
          }
        }
        return new ArrayList<>(recordFileMap.values());
      }
    }

    String query =
        "SELECT FILE_NAME, RECORD_FILE_ID, CATEGORY FROM "
            + product.getFileTable()
            + " WHERE RECORD_ID = ? AND DELETED_AT IS NULL";

    try (PreparedStatement ps = con.prepareStatement(query)) {
      ps.setString(1, recordId);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        RecordFile recordFile = new RecordFile();
        recordFile.setRecordId(recordId);
        recordFile.setFileName(rs.getString(1));
        recordFile.setRecordFileId(rs.getString(2));
        recordFile.setCategory(rs.getString(3));
        recordFiles.add(recordFile);
      }
    }
    return recordFiles;
  }

  public static void updateRecordFiles(
      Connection con,
      Insight insight,
      String projectId,
      ProductTables product,
      String recordId,
      List<RecordFile> inputFiles)
      throws SQLException {
    updateRecordFiles(con, insight, projectId, product, recordId, inputFiles, null);
  }

  /**
   * @param con
   * @param insight
   * @param projectId
   * @param product
   * @param recordId
   * @param inputFiles
   * @throws SQLException
   */
  public static void updateRecordFiles(
      Connection con,
      Insight insight,
      String projectId,
      ProductTables product,
      String recordId,
      List<RecordFile> inputFiles,
      List<RecordFile> recordFiles)
      throws SQLException {
    if (recordFiles == null) {
      recordFiles = getRecordFiles(con, product, recordId);
    }

    Map<String, RecordFile> currentFiles = new HashMap<>();
    recordFiles.stream().forEach(rf -> currentFiles.put(rf.getRecordFileId(), rf));

    List<RecordFile> filesToAdd = new ArrayList<>();
    List<RecordFile> filesToDelete = new ArrayList<>();
    List<RecordFile> filesToUpdateCategory = new ArrayList<>();
    List<RecordFile> filesToUpdateSpecialty = new ArrayList<>();
    if (inputFiles != null) {
      for (RecordFile rf : inputFiles) {
        rf.setRecordId(recordId);
        if (rf.getCategory() == null) {
          throw new TQMCException(ErrorCode.BAD_REQUEST, "File is missing category");
        }
        if (StringUtils.trimToNull(rf.getRecordFileId()) == null) {
          rf.setRecordFileId(UUID.randomUUID().toString());

          String fileName = StringUtils.trimToEmpty(Utility.normalizePath(rf.getFileName()));
          if (fileName.startsWith("/")) {
            fileName = fileName.substring(1);
          }
          if (fileName.isEmpty()) {
            throw new TQMCException(ErrorCode.BAD_REQUEST, "Empty fileName given");
          }
          rf.setFileName(fileName);

          filesToAdd.add(rf);
        } else if (currentFiles.containsKey(rf.getRecordFileId())) {
          if (currentFiles.get(rf.getRecordFileId()).getCategory() != rf.getCategory()) {
            filesToUpdateCategory.add(rf);
          }
          if (product.equals(ProductTables.SOC)) {
            if (currentFiles.get(rf.getRecordFileId()).getShowNurseReview() == null
                || currentFiles.get(rf.getRecordFileId()).getSpecialtyIdList() == null
                || !currentFiles
                    .get(rf.getRecordFileId())
                    .getShowNurseReview()
                    .equals(rf.getShowNurseReview())
                || !currentFiles
                    .get(rf.getRecordFileId())
                    .getSpecialtyIdList()
                    .equals(rf.getSpecialtyIdList())) {
              filesToUpdateSpecialty.add(rf);
            }
          }
          currentFiles.remove(rf.getRecordFileId());
        } else {
          throw new TQMCException(ErrorCode.BAD_REQUEST, "Given file not found on record");
        }
      }
    }
    if (!currentFiles.isEmpty()) {
      filesToDelete.addAll(currentFiles.values());
    }

    Set<RecordFile> curFiles = new HashSet<>(recordFiles);
    if (hasAssignedCases(con, recordId, product)) {
      curFiles.addAll(filesToAdd);
      curFiles.removeAll(filesToDelete);
      if (curFiles.size() < 1) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "At least one file should remain while record is assigned");
      }
    }

    FileUtils fileUtils = new FileUtils(projectId);
    if (!filesToAdd.isEmpty()) {
      if (fileListsHaveDuplicates(filesToAdd, curFiles)) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "Multiple files with the same name cannot be uploaded");
      }
      fileUtils.uploadFilesToRecord(
          insight.getInsightFolder(), product.getProductId(), recordId, filesToAdd);
      createRecordFiles(con, product, filesToAdd);
    }

    if (!filesToDelete.isEmpty()) {
      fileUtils.deleteFilesFromRecord(insight, product.getProductId(), recordId, filesToDelete);
      deleteRecordFiles(con, product, ConversionUtils.getUTCFromLocalNow(), filesToDelete);
    }
    if (!filesToUpdateCategory.isEmpty()) {
      updateCategoryRecordFiles(con, product, filesToUpdateCategory);
    }
    if (!filesToUpdateSpecialty.isEmpty()) {
      updateSocSpecialtyRecordFiles(con, filesToUpdateSpecialty);
    }
  }

  private static void updateSocSpecialtyRecordFiles(Connection con, List<RecordFile> recordFiles)
      throws SQLException {
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE SOC_RECORD_FILE SET SHOW_NURSE_REVIEW = ? WHERE RECORD_FILE_ID = ? ")) {
      for (RecordFile f : recordFiles) {
        int parameterIndex = 1;
        ps.setBoolean(parameterIndex++, f.getShowNurseReview());
        ps.setString(parameterIndex++, f.getRecordFileId());
        ps.addBatch();
      }
      ps.executeBatch();
    }

    try (PreparedStatement delete =
            con.prepareStatement("DELETE FROM SOC_CASE_FILE scf WHERE scf.RECORD_FILE_ID = ?");
        PreparedStatement update =
            con.prepareStatement(
                "INSERT INTO SOC_CASE_FILE (RECORD_FILE_ID, CASE_FILE_ID, CASE_ID) VALUES (?, ?, ?)")) {
      for (RecordFile f : recordFiles) {
        delete.setString(1, f.getRecordFileId());
        delete.addBatch();
      }
      delete.executeBatch();

      for (RecordFile f : recordFiles) {
        for (String caseId : f.getCaseIds()) {
          int parameterIndex = 1;
          update.setString(parameterIndex++, f.getRecordFileId());
          update.setString(parameterIndex++, UUID.randomUUID().toString());
          update.setString(parameterIndex++, caseId);
          update.addBatch();
        }
      }

      update.executeBatch();
    }
  }

  public static boolean fileListsHaveDuplicates(
      List<RecordFile> incomingFiles, Set<RecordFile> existingFiles) {

    // Extract file names from existingFiles in parallel
    Set<String> existingNames =
        existingFiles.parallelStream().map(RecordFile::getFileName).collect(Collectors.toSet());

    // Extract file names from incomingFiles in parallel
    List<String> incomingNamesList =
        incomingFiles.parallelStream().map(RecordFile::getFileName).collect(Collectors.toList());

    // Check for duplicates within incomingFiles
    boolean incomingHasDuplicates =
        incomingNamesList
            .parallelStream()
            .collect(Collectors.groupingBy(name -> name, Collectors.counting()))
            .values()
            .parallelStream()
            .anyMatch(count -> count > 1);

    // Check for duplicates between incomingFiles and existingFiles
    boolean overlapExists = incomingNamesList.parallelStream().anyMatch(existingNames::contains);

    // Return true if any duplicates found
    return incomingHasDuplicates || overlapExists;
  }

  public static boolean hasRecordAccess(
      Connection con, String userId, String recordId, ProductTables product) throws SQLException {
    boolean result = false;
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT 1 FROM "
                + product.getCaseTable()
                + " WHERE RECORD_ID = ? AND USER_ID = ? AND DELETED_AT IS NULL AND SUBMISSION_GUID IS NULL "
                + "UNION SELECT 1 FROM SOC_NURSE_REVIEW WHERE RECORD_ID = ? AND USER_ID = ? AND DELETED_AT IS NULL AND SUBMISSION_GUID IS NULL")) {
      ps.setString(1, recordId);
      ps.setString(2, userId);
      ps.setString(3, recordId);
      ps.setString(4, userId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          result = rs.getInt(1) == 1;
        }
      }
    }
    return result;
  }

  public static boolean recordHasCompletedCase(
      Connection con, String recordId, ProductTables product) throws SQLException {
    boolean result = false;
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT 1 "
                + "FROM "
                + product.getCaseTable()
                + " rc LEFT JOIN "
                + product.getWorkflowTable()
                + " wf "
                + "ON rc.CASE_ID = wf.CASE_ID AND wf.IS_LATEST = 1 "
                + "WHERE rc.RECORD_ID = ? AND rc.DELETED_AT IS NULL AND wf.STEP_STATUS = ?")) {
      ps.setString(1, recordId);
      ps.setString(2, TQMCConstants.CASE_STEP_STATUS_COMPLETED);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          result = rs.getInt(1) == 1;
        }
      }
    }
    return result;
  }

  public static boolean hasCaseAccess(Connection con, String userId, String caseId, String product)
      throws SQLException {
    boolean result = false;
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT 1 FROM " + product.toUpperCase() + "_CASE WHERE CASE_ID = ? AND USER_ID = ?")) {
      ps.setString(1, caseId);
      ps.setString(2, userId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          result = rs.getInt(1) == 1;
        }
      }
    }
    return result;
  }

  public static String getNurseReviewRecordId(Connection con, String reviewId) throws SQLException {
    String result = null;
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT record_id from SOC_NURSE_REVIEW WHERE nurse_review_id = ? AND DELETED_AT IS NULL")) {
      ps.setString(1, reviewId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          result = rs.getString(1);
        }
      }
    }
    if (result == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND);
    }

    return result;
  }

  public static boolean isNurseReviewer(Connection con, String userId, String nurseReviewId)
      throws SQLException {
    boolean result = false;
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT 1 from SOC_NURSE_REVIEW where nurse_review_id = ? AND user_id = ? AND deleted_at is null")) {
      ps.setString(1, nurseReviewId);
      ps.setString(2, userId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          result = rs.getInt(1) == 1;
        }
      }
    }
    return result;
  }

  public static boolean caseCompleted(Connection con, String caseId, ProductTables product)
      throws SQLException {
    boolean result = false;
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT 1 from "
                + product.getWorkflowTable()
                + " where case_id = ? AND step_status = ?")) {
      ps.setString(1, caseId);
      ps.setString(2, TQMCConstants.CASE_STEP_STATUS_COMPLETED);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          result = rs.getInt(1) == 1;
        }
      }
    }
    return result;
  }

  public static boolean hasNurseReviewEditAccess(
      Connection con, String userId, String nurseReviewId) throws SQLException {
    boolean result = false;
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT 1 FROM soc_nurse_review snr WHERE nurse_review_id = ? AND user_id = ?")) {
      ps.setString(1, nurseReviewId);
      ps.setString(2, userId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          result = rs.getInt(1) == 1;
        }
      }
    }
    return result;
  }

  public static GttWorkflow getLatestGttWorkflow(Connection con, String caseId)
      throws SQLException {
    GttWorkflow result = new GttWorkflow();

    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT gw.CASE_ID, gw.CASE_TYPE, gw.GUID, gw.IS_LATEST, gw.RECIPIENT_STAGE, gw.RECIPIENT_USER_ID, "
                + "gw.SENDING_STAGE, gw.SENDING_USER_ID, gw.SMSS_TIMESTAMP, gw.STEP_STATUS, COALESCE(gw1.RECIPIENT_USER_ID, gw.RECIPIENT_USER_ID) AS VISIBLE_BY "
                + "FROM GTT_WORKFLOW gw "
                + "LEFT OUTER JOIN GTT_CONSENSUS_CASE gcc ON gw.CASE_TYPE = 'consensus' AND gcc.CASE_ID = gw.CASE_ID "
                + "LEFT OUTER JOIN GTT_WORKFLOW gw1 ON gw1.CASE_ID IN (gcc.ABS_1_CASE_ID, gcc.ABS_2_CASE_ID) AND gw1.IS_LATEST = 1 AND gw1.STEP_STATUS = 'completed' "
                + "LEFT OUTER JOIN GTT_PHYSICIAN_CASE gpc ON gw.CASE_TYPE = 'physician' AND gpc.CASE_ID = gw.CASE_ID "
                + "LEFT OUTER JOIN GTT_WORKFLOW gw3 ON gw3.CASE_ID = gpc.CONSENSUS_CASE_ID AND gw3.IS_LATEST = 1 AND gw3.STEP_STATUS = 'completed' "
                + "WHERE gw.CASE_ID = ? AND gw.IS_LATEST = 1 ")) {
      ps.setString(1, caseId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          if (result.getCaseId() == null) {
            result.setCaseId(rs.getString("CASE_ID"));
            result.setCaseType(rs.getString("CASE_TYPE"));
            result.setGuid(rs.getString("GUID"));
            result.setIsLatest(rs.getInt("IS_LATEST") == 1);
            result.setRecipientStage(rs.getString("RECIPIENT_STAGE"));
            result.setRecipientUserId(rs.getString("RECIPIENT_USER_ID"));
            result.setSendingStage(rs.getString("SENDING_STAGE"));
            result.setSendingUserId(rs.getString("SENDING_USER_ID"));
            result.setSmssTimestamp(
                ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("SMSS_TIMESTAMP")));
            result.setStepStatus(rs.getString("STEP_STATUS"));
          }
          result.getVisibleTo().add(rs.getString("VISIBLE_BY"));
        }
      }
    }

    return result;
  }

  public static void onReassignUpdateGttWorkflowAndEditCases(
      Connection con, String caseId, String caseType, String userId, String assignedUserId)
      throws SQLException {
    // Getting the case from the GTT workflow table
    GttWorkflow gw = TQMCHelper.getLatestGttWorkflow(con, caseId);

    // Update details to advance the case
    gw.setSendingStage(gw.getRecipientStage());
    gw.setSendingUserId(gw.getRecipientUserId());
    gw.setGuid(UUID.randomUUID().toString());
    gw.setRecipientUserId(assignedUserId);
    gw.setIsLatest(true);

    if (TQMCConstants.GTT_CASE_TYPE_PHYSICIAN.equals(caseType)) {

      gw.setStepStatus(TQMCConstants.CASE_STEP_STATUS_NOT_STARTED);
      gw.setRecipientStage(TQMCConstants.GTT_STAGE_PHYSICIAN_REVIEW);

      TQMCHelper.createNewGttWorkflowEntry(con, gw);

      String consensusId = null;

      // Getting the consensus case id associated with the old physician case
      try (PreparedStatement getConsensusId =
          con.prepareStatement(
              "SELECT CONSENSUS_CASE_ID " + "FROM GTT_PHYSICIAN_CASE " + "WHERE CASE_ID = ?")) {
        getConsensusId.setString(1, caseId);
        ResultSet rs = getConsensusId.executeQuery();
        if (rs.next()) {
          consensusId = rs.getString("CONSENSUS_CASE_ID");
        }
      }
      // Updating the current Physician case
      TQMCHelper.reassignGttPhysicianCase(con, caseId, consensusId, userId, assignedUserId);
    } else {

      gw.setStepStatus(TQMCConstants.CASE_STEP_STATUS_NOT_STARTED);
      gw.setRecipientStage(TQMCConstants.GTT_STAGE_ABSTRACTION);

      TQMCHelper.createNewGttWorkflowEntry(con, gw);

      // Updating the current Abstractor case
      TQMCHelper.reassignGttAbstractorCase(con, caseId, userId, assignedUserId);
    }
  }

  public static McscCase getMcscCase(Connection con, String caseId) throws SQLException {
    McscCase result = null;

    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT mc.ASSIGNED_AT, mc.CREATED_AT, mc.UPDATED_AT, mc.DELETED_AT, mc.RECORD_ID, mc.USER_ID, qr.REQUESTED_AT, qr.RECEIVED_AT, mc.REOPENING_REASON FROM MCSC_CASE mc INNER JOIN QU_RECORD qr ON qr.RECORD_ID = mc.RECORD_ID WHERE mc.CASE_ID = ?")) {
      ps.setString(1, caseId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          result =
              new McscCase(
                  caseId,
                  ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("ASSIGNED_AT")),
                  ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("CREATED_AT")),
                  ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("UPDATED_AT")),
                  ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("DELETED_AT")),
                  rs.getString("RECORD_ID"),
                  rs.getString("USER_ID"),
                  ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("REQUESTED_AT")),
                  ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("RECEIVED_AT")),
                  rs.getString("REOPENING_REASON"));
        }
      }
    }

    return result;
  }

  public static DpCase getDpCase(Connection con, String caseId) throws SQLException {
    DpCase result = null;

    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT dp.ASSIGNED_AT, dp.CREATED_AT, dp.UPDATED_AT, dp.DELETED_AT, dp.RECORD_ID, dp.USER_ID, qr.REQUESTED_AT, qr.RECEIVED_AT, dp.REOPENING_REASON FROM DP_CASE dp INNER JOIN QU_RECORD qr ON qr.RECORD_ID = dp.RECORD_ID WHERE dp.CASE_ID = ?")) {
      ps.setString(1, caseId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          result =
              new DpCase(
                  caseId,
                  ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("ASSIGNED_AT")),
                  ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("CREATED_AT")),
                  ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("UPDATED_AT")),
                  ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("DELETED_AT")),
                  rs.getString("RECORD_ID"),
                  rs.getString("USER_ID"),
                  ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("REQUESTED_AT")),
                  ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("RECEIVED_AT")),
                  rs.getString("REOPENING_REASON"));
        }
      }
    }

    return result;
  }

  public static void sendMNAssignmentEmail(
      String caseAssignmentType,
      String mnCaseType,
      String url,
      String caseId,
      boolean isReopened,
      String aliasRecordId,
      String emailAddress,
      String actionByEmailAddress,
      String dueDate) {
    String path = caseAssignmentType + "/review";
    if (!TQMCConstants.MN.equals(caseAssignmentType)) {
      throw new IllegalArgumentException(
          "Unsupported case type for operation: " + caseAssignmentType);
    }

    String upperCaseType = StringUtils.upperCase(caseAssignmentType);

    Map<String, String> placeholderValues = new HashMap<>();
    placeholderValues.put("CASE_TYPE", "Medical Necessity");
    placeholderValues.put("CASE_SUBTYPE", mnCaseType);
    placeholderValues.put("ACTION_PREFIX", isReopened ? "reopened and " : "");
    placeholderValues.put("PATH", path);
    placeholderValues.put("URL", url);
    placeholderValues.put("CASE_ID", caseId);
    placeholderValues.put("DUE_DATE", dueDate);

    StrSubstitutor sub = new StrSubstitutor(placeholderValues);
    String message = sub.replace(TQMCConstants.CASE_MN_ASSIGNMENT_EMAIL_TEMPLATE);
    String subject =
        "TQMC "
            + upperCaseType
            + " "
            + mnCaseType
            + " Case "
            + (isReopened ? "Reopened" : "Assignment")
            + " #"
            + aliasRecordId;

    sendEmail(subject, message, emailAddress, actionByEmailAddress);
  }

  public static void sendAssignmentEmail(
      String caseAssignmentType,
      String url,
      String caseId,
      boolean isReopened,
      String aliasRecordId,
      String emailAddress,
      String actionByEmailAddress,
      String dueDate) {

    String upperCaseType = StringUtils.upperCase(caseAssignmentType);
    String path = caseAssignmentType + "/review";
    String fullCaseType = "";
    switch (caseAssignmentType) {
      case TQMCConstants.SOC:
        fullCaseType = "Standard of Care";
        break;
      case TQMCConstants.MCSC:
        fullCaseType = "Managed Care Support Contractors";
        break;
      case TQMCConstants.DP:
        fullCaseType = "Designated Providers";
        break;
      case "soc chronology":
        fullCaseType = "Standard of Care Chronology";
        upperCaseType = "SOC Chronology";
        path = "soc/chronology";
        break;
      default:
        throw new IllegalArgumentException(
            "Unsupported case type for operation: " + caseAssignmentType);
    }

    Map<String, String> placeholderValues = new HashMap<>();
    placeholderValues.put("CASE_TYPE", fullCaseType);
    placeholderValues.put("ACTION_PREFIX", isReopened ? "reopened and " : "");
    placeholderValues.put("PATH", path);
    placeholderValues.put("URL", url);
    placeholderValues.put("CASE_ID", caseId);
    placeholderValues.put("DUE_DATE", dueDate);

    StrSubstitutor sub = new StrSubstitutor(placeholderValues);
    String message = sub.replace(TQMCConstants.CASE_ASSIGNMENT_EMAIL_TEMPLATE);
    String subject =
        "TQMC "
            + upperCaseType
            + " Case "
            + (isReopened ? "Reopened" : "Assignment")
            + " #"
            + aliasRecordId;

    sendEmail(subject, message, emailAddress, actionByEmailAddress);
  }

  public static void sendEmail(String subject, String message, String to, String cc) {
    //    System.out.println(subject + "\n");
    //    System.out.println(message + "\n");

    try {
      Session emailSession = SocialPropertiesUtil.getInstance().getEmailSession();
      if (emailSession == null) {
        throw new TQMCException(
            ErrorCode.INTERNAL_SERVER_ERROR, "Email not configured or disabled");
      }
      if (!EmailUtility.sendEmail(
          emailSession,
          new String[] {to},
          cc == null ? null : new String[] {cc},
          null,
          TQMCConstants.EMAIL_SENDER,
          subject,
          message,
          true,
          null)) {
        throw new TQMCException(
            ErrorCode.INTERNAL_SERVER_ERROR, "Failed to send email to " + to + " with cc " + cc);
      }
    } catch (Exception e) {
      throw new TQMCException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Failed to send email to " + to + " with cc " + cc, e);
    }
  }

  public static boolean hasGttCaseAccess(Connection con, String userId, String caseId)
      throws SQLException {
    GttWorkflow w = getLatestGttWorkflow(con, caseId);
    boolean result = w.getCaseId() != null && w.getVisibleTo().contains(userId);
    return result;
  }

  public static void createNewGttWorkflowEntry(Connection con, GttWorkflow w) throws SQLException {
    if (w == null) {
      return;
    }
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE GTT_WORKFLOW SET IS_LATEST = 0 WHERE CASE_ID = ? AND IS_LATEST = 1; "
                + "INSERT INTO GTT_WORKFLOW (CASE_ID, CASE_TYPE, GUID, IS_LATEST, RECIPIENT_STAGE, "
                + "RECIPIENT_USER_ID, SENDING_STAGE, SENDING_USER_ID, SMSS_TIMESTAMP, STEP_STATUS) "
                + "VALUES (?,?,?,?,?, ?,?,?,?,?)")) {
      int parameterIndex = 1;
      // update param
      ps.setString(parameterIndex++, w.getCaseId());

      // insert param
      ps.setString(parameterIndex++, w.getCaseId());
      ps.setString(parameterIndex++, w.getCaseType());
      ps.setString(parameterIndex++, w.getGuid());
      ps.setInt(parameterIndex++, Boolean.TRUE == w.getIsLatest() ? 1 : 0);
      ps.setString(parameterIndex++, w.getRecipientStage());
      ps.setString(parameterIndex++, w.getRecipientUserId());
      ps.setString(parameterIndex++, w.getSendingStage());
      ps.setString(parameterIndex++, w.getSendingUserId());
      ps.setObject(parameterIndex++, w.getSmssTimestamp());
      ps.setString(parameterIndex++, w.getStepStatus());
      ps.execute();
    }
  }

  public static void createNewMcscWorkflowEntry(Connection con, McscWorkflow w)
      throws SQLException {
    if (w == null) {
      return;
    }
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE MCSC_WORKFLOW SET IS_LATEST = 0 " + "WHERE CASE_ID = ? AND IS_LATEST = 1")) {
      ps.setString(1, w.getCaseId());
      ps.execute();
    }

    try (PreparedStatement ps =
        con.prepareStatement(
            "INSERT INTO MCSC_WORKFLOW (CASE_ID, GUID, IS_LATEST, "
                + "RECIPIENT_USER_ID, SENDING_USER_ID, SMSS_TIMESTAMP, STEP_STATUS, "
                + "WORKFLOW_NOTES) "
                + "VALUES (?,?,?,?,?, ?,?,?)")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, w.getCaseId());
      ps.setString(parameterIndex++, w.getGuid());
      ps.setInt(parameterIndex++, Boolean.TRUE == w.getIsLatest() ? 1 : 0);
      ps.setString(parameterIndex++, w.getRecipientUserId());
      ps.setString(parameterIndex++, w.getSendingUserId());
      ps.setObject(parameterIndex++, w.getSmssTimestamp());
      ps.setString(parameterIndex++, w.getStepStatus());
      ps.setString(parameterIndex++, w.getWorkflowNotes());
      ps.execute();
    }
  }

  public static void createNewDpWorkflowEntry(Connection con, DpWorkflow w) throws SQLException {
    if (w == null) {
      return;
    }
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE DP_WORKFLOW SET IS_LATEST = 0 " + "WHERE CASE_ID = ? AND IS_LATEST = 1")) {
      ps.setString(1, w.getCaseId());
      ps.execute();
    }

    try (PreparedStatement ps =
        con.prepareStatement(
            "INSERT INTO DP_WORKFLOW (CASE_ID, GUID, IS_LATEST, "
                + "RECIPIENT_USER_ID, SENDING_USER_ID, SMSS_TIMESTAMP, STEP_STATUS, "
                + "WORKFLOW_NOTES) "
                + "VALUES (?,?,?,?,?, ?,?,?)")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, w.getCaseId());
      ps.setString(parameterIndex++, w.getGuid());
      ps.setInt(parameterIndex++, Boolean.TRUE == w.getIsLatest() ? 1 : 0);
      ps.setString(parameterIndex++, w.getRecipientUserId());
      ps.setString(parameterIndex++, w.getSendingUserId());
      ps.setObject(parameterIndex++, w.getSmssTimestamp());
      ps.setString(parameterIndex++, w.getStepStatus());
      ps.setString(parameterIndex++, w.getWorkflowNotes());
      ps.execute();
    }
  }

  public static void deleteGttRecordCaseEventsForCase(
      Connection con, String caseId, List<GttRecordCaseEvent> events) throws SQLException {
    String query =
        "DELETE FROM GTT_RECORD_CASE_EVENT WHERE CASE_ID = ? AND RECORD_CASE_EVENT_ID NOT IN (%s)";
    String placeholders = String.join(",", Collections.nCopies(events.size(), "?"));
    int count = query.split("%s", -1).length - 1;
    String formattedQuery = query;
    for (int i = 0; i < count; i++) {
      formattedQuery = formattedQuery.replaceFirst("%s", placeholders);
    }
    try (PreparedStatement ps = con.prepareStatement(formattedQuery)) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, caseId);
      for (GttRecordCaseEvent e : events) {
        ps.setString(parameterIndex++, e.getRecordCaseEventId());
      }
      ps.execute();
    }
  }

  public static void updateGttRecordCaseEventsForCase(
      Connection con,
      List<GttRecordCaseEvent> events,
      List<GttIrrMatch> updateMatches,
      String caseId,
      String caseType)
      throws SQLException {

    if (events.size() >= 1) {

      Set<String> currentRecordCaseEventIds = new HashSet<>();
      try (PreparedStatement selectPs =
          con.prepareStatement(
              "SELECT RECORD_CASE_EVENT_ID FROM GTT_RECORD_CASE_EVENT WHERE case_id = ?")) {
        selectPs.setString(1, caseId);
        ResultSet selectRs = selectPs.executeQuery();
        while (selectRs.next()) {
          currentRecordCaseEventIds.add(selectRs.getString("RECORD_CASE_EVENT_ID"));
        }
      }

      Set<String> newEventIds =
          events.stream().map(GttRecordCaseEvent::getRecordCaseEventId).collect(Collectors.toSet());

      Set<String> updateIds = new HashSet<>(currentRecordCaseEventIds);
      Set<String> insertIds = new HashSet<>(newEventIds);
      insertIds.removeAll(currentRecordCaseEventIds);
      updateIds.retainAll(newEventIds);

      List<GttRecordCaseEvent> insertEvents = new ArrayList<>();
      List<GttRecordCaseEvent> updateEvents = new ArrayList<>();

      for (GttRecordCaseEvent e : events) {
        if (updateIds.contains(e.getRecordCaseEventId())) {
          updateEvents.add(e);
        }
        if (insertIds.contains(e.getRecordCaseEventId())) {
          insertEvents.add(e);
        }
      }

      createGttRecordCaseEvents(con, insertEvents);
      updateGttRecordCaseEvents(con, updateEvents);

      if (!TQMCConstants.GTT_CASE_TYPE_PHYSICIAN.equalsIgnoreCase(caseType)) {
        updateEvents.addAll(insertEvents);
        deleteGttRecordCaseEventsForCase(con, caseId, updateEvents);
      }
    }

    if (updateMatches != null && !updateMatches.isEmpty()) {
      updateSelectedGttIrrMatches(con, updateMatches);
    }
  }

  public static void updateGttRecordCaseEvents(Connection con, List<GttRecordCaseEvent> events)
      throws SQLException {
    if (events == null || events.isEmpty()) {
      return;
    }
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE GTT_RECORD_CASE_EVENT SET EVENT_TYPE_TEMPLATE_ID = ?, TRIGGER_TEMPLATE_ID = ?, HARM_CATEGORY_TEMPLATE_ID = ?, "
                + " PRESENT_ON_ADMISSION = ?, EVENT_DESCRIPTION = ?, UPDATED_AT = ?, IS_DELETED = ?"
                + " WHERE RECORD_CASE_EVENT_ID = ?")) {
      for (GttRecordCaseEvent e : events) {
        int parameterIndex = 1;
        ps.setString(parameterIndex++, e.getEventTypeTemplateId());
        ps.setString(parameterIndex++, e.getTriggerTemplateId());
        ps.setString(parameterIndex++, e.getHarmCategoryTemplateId());
        if (e.getPresentOnAdmission() == null) {
          ps.setNull(parameterIndex++, java.sql.Types.INTEGER);
        } else {
          ps.setInt(parameterIndex++, Boolean.TRUE.equals(e.getPresentOnAdmission()) ? 1 : 0);
        }
        ps.setString(parameterIndex++, e.getEventDescription());
        ps.setObject(parameterIndex++, e.getUpdatedAt());
        ps.setBoolean(
            parameterIndex++,
            Boolean.TRUE.equals(
                e.getIsDeleted())); // Need this equals bc using the Boolean class which can be null
        ps.setString(parameterIndex++, e.getRecordCaseEventId());
        ps.addBatch();
      }
      ps.executeBatch();
    }
  }

  public static void createGttRecordCaseEvents(Connection con, List<GttRecordCaseEvent> events)
      throws SQLException {
    if (events == null || events.isEmpty()) {
      return;
    }
    try (PreparedStatement ps =
        con.prepareStatement(
            "INSERT INTO GTT_RECORD_CASE_EVENT (RECORD_CASE_EVENT_ID, CASE_ID, EVENT_TYPE_TEMPLATE_ID, TRIGGER_TEMPLATE_ID, "
                + "HARM_CATEGORY_TEMPLATE_ID, CASE_TYPE, CONSENSUS_LINK_CASE_EVENT_ID, PRESENT_ON_ADMISSION, "
                + "EVENT_DESCRIPTION, UPDATED_AT) "
                + "VALUES (?,?,?,?,?, ?,?,?,?,?)")) {
      for (GttRecordCaseEvent e : events) {
        int parameterIndex = 1;
        ps.setString(parameterIndex++, e.getRecordCaseEventId());
        ps.setString(parameterIndex++, e.getCaseId());
        ps.setString(parameterIndex++, e.getEventTypeTemplateId());
        ps.setString(parameterIndex++, e.getTriggerTemplateId());
        ps.setString(parameterIndex++, e.getHarmCategoryTemplateId());
        ps.setString(parameterIndex++, e.getCaseType());
        ps.setString(parameterIndex++, e.getConsensusLinkCaseEventId());
        if (e.getPresentOnAdmission() == null) {
          ps.setNull(parameterIndex++, java.sql.Types.INTEGER);
        } else {
          ps.setInt(parameterIndex++, Boolean.TRUE.equals(e.getPresentOnAdmission()) ? 1 : 0);
        }
        ps.setString(parameterIndex++, e.getEventDescription()); // TODO: this is a clob
        ps.setObject(parameterIndex++, e.getUpdatedAt());
        ps.addBatch();
      }
      ps.executeBatch();
    }
  }

  public static void replaceMcscQualityReviewEventsForCase(
      Connection con, String caseId, List<QualityReviewEvent> events) throws SQLException {
    deleteMcscQualityReviewEventsForCase(con, caseId);
    createMcscQualityReviewEventsForCase(con, events);
  }

  public static void deleteMcscQualityReviewEventsForCase(Connection con, String caseId)
      throws SQLException {
    try (PreparedStatement ps =
        con.prepareStatement("DELETE FROM MCSC_QUALITY_REVIEW_EVENT WHERE CASE_ID = ?")) {
      ps.setString(1, caseId);
      ps.execute();
    }
  }

  public static void createMcscQualityReviewEventsForCase(
      Connection con, List<QualityReviewEvent> events) throws SQLException {
    if (events == null || events.isEmpty()) {
      return;
    }
    try (PreparedStatement ps =
        con.prepareStatement(
            "INSERT INTO MCSC_QUALITY_REVIEW_EVENT (QUALITY_REVIEW_EVENT_ID, CASE_ID, TRIGGER_TEMPLATE_ID, "
                + "HARM_CATEGORY_TEMPLATE_ID, "
                + "EVENT_DESCRIPTION) "
                + "VALUES (?,?,?,?,?)")) {
      for (QualityReviewEvent qre : events) {
        int parameterIndex = 1;
        ps.setString(parameterIndex++, qre.getQualityReviewEventId());
        ps.setString(parameterIndex++, qre.getCaseId());
        ps.setString(parameterIndex++, qre.getTriggerTemplateId());
        ps.setString(parameterIndex++, qre.getHarmCategoryTemplateId());
        ps.setString(parameterIndex++, qre.getEventDescription());
        ps.addBatch();
      }
      ps.executeBatch();
    }
  }

  public static void createGttIrrMatches(Connection con, List<GttIrrMatch> matches)
      throws SQLException {
    if (matches == null || matches.isEmpty()) {
      return;
    }
    try (PreparedStatement ps =
        con.prepareStatement(
            "INSERT INTO GTT_IRR_MATCH "
                + "(IRR_MATCH_ID, CONSENSUS_CASE_ID, ABSTRACTOR_ONE_RECORD_CASE_EVENT_ID, ABSTRACTOR_TWO_RECORD_CASE_EVENT_ID, ALTERNATE_RECORD_CASE_EVENT_ID, "
                + "SELECTED_CASE_EVENT_ID, IS_DELETED, IS_MATCH) "
                + "VALUES (?,?,?,?,?, ?,?,?)")) {
      for (GttIrrMatch e : matches) {
        int parameterIndex = 1;
        ps.setString(parameterIndex++, e.getIrrMatchId());
        ps.setString(parameterIndex++, e.getConsensusCaseId());
        ps.setString(parameterIndex++, e.getAbstractorOneRecordCaseEventId());
        ps.setString(parameterIndex++, e.getAbstractorTwoRecordCaseEventId());
        ps.setString(parameterIndex++, e.getAlternateRecordCaseEventId());
        ps.setString(parameterIndex++, e.getSelectedCaseEventId());
        ps.setObject(parameterIndex++, e.getIsDeleted());
        ps.setInt(parameterIndex++, Boolean.TRUE == e.getIsMatch() ? 1 : 0);
        ps.addBatch();
      }
      ps.executeBatch();
    }
  }

  public static String getSocCaseVersionsQuery() {
    String query =
        "SELECT sc.RECORD_ID, sc.CASE_ID, user.LAST_NAME, user.FIRST_NAME, user.EMAIL, "
            + "sc.ATTESTATION_SIGNATURE, sc.ATTESTED_AT, sc.DUE_DATE_REVIEW, sc.DUE_DATE_DHA, "
            + "sc.SPECIALTY_ID, "
            + "snr.NURSE_REVIEW_ID, "
            + "eval.CASE_SYSTEM_EVALUATION_ID, eval.REFERENCES, eval.SYSTEM_ISSUE, "
            + "eval.SYSTEM_ISSUE_RATIONALE, eval.SYSTEM_ISSUE_JUSTIFICATION, "
            + "sr.ALIAS_RECORD_ID AS ALIAS_RECORD_ID, "
            + "CONCAT(sr.PATIENT_LAST_NAME, ', ', sr.PATIENT_FIRST_NAME) AS PATIENT_NAME, "
            + "sw.RECIPIENT_USER_ID AS ACTIVE_USER_ID, "
            + "sc.USER_ID, sc.UPDATED_AT, sw.STEP_STATUS, sc.SUBMISSION_GUID, sw.CASE_ID AS BASE_CASE_ID, sc.REOPENING_REASON "
            + "FROM SOC_CASE sc "
            + "LEFT JOIN TQMC_USER user ON sc.USER_ID = user.USER_ID "
            + "LEFT JOIN SOC_RECORD sr ON sr.RECORD_ID = sc.RECORD_ID "
            + "LEFT JOIN SOC_NURSE_REVIEW snr ON sr.RECORD_ID = snr.RECORD_ID "
            + "LEFT JOIN SOC_CASE_SYSTEM_EVALUATION eval ON sc.CASE_ID = eval.CASE_ID "
            + "JOIN SOC_WORKFLOW sw\n"
            + "  ON ("
            + "      (sc.submission_guid IS NOT NULL AND sc.submission_guid = sw.guid) "
            + "      OR "
            + "      (sc.submission_guid IS NULL AND sc.case_id = sw.case_id AND sw.IS_LATEST = 1) "
            + "     )"
            + "WHERE sr.RECORD_ID = ? AND sc.DELETED_AT IS NULL AND snr.DELETED_AT IS NULL ";
    return query;
  }

  public static String getSocNurseReviewVersionsQuery() {
    String query =
        "SELECT "
            + "snr.nurse_review_id AS NURSE_REVIEW_ID, "
            + "sr.record_id AS RECORD_ID, "
            + "sr.alias_record_id AS ALIAS_RECORD_ID, "
            + "snr.SUBMISSION_GUID, "
            + "snr.user_id AS USER_ID, "
            + "CONCAT(sr.patient_last_name, ', ', sr.patient_first_name) AS PATIENT_NAME, "
            + "snr.DUE_DATE_DHA, "
            + "snr.DUE_DATE_REVIEW, "
            + "snr.period_of_care_start AS POC_START, "
            + "snr.period_of_care_end AS POC_END, "
            + "snr.injury AS INJURY, "
            + "snr.diagnoses AS DIAGNOSES, "
            + "snr.allegations AS ALLEGATIONS, "
            + "snr.summary_of_facts AS SUMMARY_OF_FACTS, "
            + "snr.updated_at AS UPDATED_AT, "
            + "sw.case_id AS BASE_NURSE_REVIEW_ID, "
            + "CONCAT(tu.FIRST_NAME, ' ', tu.LAST_NAME) AS NURSE_REVIEWER_NAME, "
            + "sw.step_status AS CASE_STATUS, "
            + "sw.RECIPIENT_USER_ID AS ACTIVE_USER_ID, "
            + "snr.reopening_reason AS REOPENING_REASON "
            + "FROM soc_record sr "
            + "RIGHT OUTER JOIN soc_nurse_review snr "
            + "ON snr.record_id = sr.record_id "
            + "JOIN SOC_WORKFLOW sw "
            + "  ON ("
            + "      (snr.submission_guid IS NOT NULL AND snr.submission_guid = sw.guid) "
            + "      OR "
            + "      (snr.submission_guid IS NULL AND snr.nurse_review_id = sw.case_id AND sw.IS_LATEST = 1) "
            + "     )"
            + "LEFT OUTER JOIN tqmc_user tu "
            + "ON snr.user_id = tu.user_id "
            + "WHERE sr.record_id = ? AND snr.DELETED_AT IS NULL";
    return query;
  }

  public static String getMnQuestionQuery() {
    String query =
        "SELECT CASE_QUESTION_ID, CASE_QUESTION, QUESTION_RESPONSE, QUESTION_RATIONALE, QUESTION_REFERENCE"
            + " FROM MN_CASE_QUESTION"
            + " WHERE CASE_ID = ? AND DELETED_AT IS NULL"
            + " ORDER BY QUESTION_NUMBER ";
    return query;
  }

  public static void updateSelectedGttIrrMatches(Connection con, List<GttIrrMatch> matches)
      throws SQLException {
    if (matches == null || matches.isEmpty()) {
      return;
    }
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE GTT_IRR_MATCH SET "
                + "ALTERNATE_RECORD_CASE_EVENT_ID = COALESCE(?, ALTERNATE_RECORD_CASE_EVENT_ID),"
                + "SELECTED_CASE_EVENT_ID = ?,"
                + "IS_DELETED = ? "
                + "WHERE IRR_MATCH_ID = ?")) {
      for (GttIrrMatch m : matches) {
        ps.setString(1, m.getAlternateRecordCaseEventId());
        ps.setString(2, m.getSelectedCaseEventId());
        ps.setInt(3, Boolean.TRUE == m.getIsDeleted() ? 1 : 0);
        ps.setString(4, m.getIrrMatchId());
        ps.addBatch();
      }
      ps.executeBatch();
    }
  }

  public static String getGttPhysicianCaseIdForConsensusCaseId(
      Connection con, String consensusCaseId) throws SQLException {
    String result = null;
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT CASE_ID FROM GTT_PHYSICIAN_CASE WHERE CONSENSUS_CASE_ID = ?")) {
      ps.setString(1, consensusCaseId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          result = rs.getString(1);
        }
      }
    }
    return result;
  }

  public static void updateSocCase(
      Connection con,
      String caseId,
      String attestationSignature,
      LocalDateTime attestedAt,
      String attestedSpecialty,
      String attestedSubspecialty,
      String reason,
      LocalDateTime time)
      throws SQLException {

    StringBuilder query = new StringBuilder();

    query.append(
        "UPDATE SOC_CASE SET ATTESTATION_SIGNATURE = ?, ATTESTED_AT = ?, UPDATED_AT = ?, REOPENING_REASON = ? ");

    if (attestedSpecialty != null && attestedSubspecialty != null) {
      query.append(", ATTESTATION_SPECIALTY = ?, ATTESTATION_SUBSPECIALTY = ? ");
    }

    query.append("WHERE CASE_ID = ?");

    try (PreparedStatement ps = con.prepareStatement(query.toString())) {
      // SET
      int parameterIndex = 1;
      ps.setString(parameterIndex++, attestationSignature);
      ps.setObject(parameterIndex++, attestedAt);
      ps.setObject(parameterIndex++, time);
      ps.setString(parameterIndex++, reason);
      if (attestedSpecialty != null && attestedSubspecialty != null) {
        ps.setObject(parameterIndex++, attestedSpecialty);
        ps.setObject(parameterIndex++, attestedSubspecialty);
      }
      // WHERE
      ps.setString(parameterIndex++, caseId);
      ps.execute();
    }
  }

  public static void updateSocCase(
      Connection con,
      String caseId,
      String attestationSignature,
      LocalDateTime attestedAt,
      LocalDateTime time)
      throws SQLException {

    updateSocCase(con, caseId, attestationSignature, attestedAt, null, null, null, time);
  }

  public static Map<String, Object> getGttAbstractionCaseMap(
      Connection con, String userId, String caseId) throws SQLException {
    Map<String, Object> c = new HashMap<>();

    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT "
                + "gac.CASE_ID AS CASE_ID, "
                + "gac.RECORD_ID AS RECORD_ID, "
                + "CONCAT(tu.FIRST_NAME, ' ', tu.LAST_NAME) AS ABSTRACTOR_2_USER_NAME, "
                + "tr.DISCHARGE_DATE AS DISCHARGE_DATE, "
                + "gac.UPDATED_AT AS UPDATED_AT, "
                + "DATEDIFF(DAY, gac.CREATED_AT, CURRENT_TIMESTAMP) AS TIME_IN_QUEUE_DAYS, "
                + "tr.LENGTH_OF_STAY AS LENGTH_OF_STAY_DAYS, "
                + "gw.STEP_STATUS AS CASE_STATUS, "
                + "e.RECORD_CASE_EVENT_ID, "
                + "gtt.BUCKET AS TRIGGER_CATEGORY, "
                + "gtt.NAME AS TRIGGER_DISPLAY, "
                + "gtt.TRIGGER_TEMPLATE_ID AS TRIGGER_VALUE, "
                + "gett.BUCKET AS EVENT_CATEGORY, "
                + "gett.NAME AS EVENT_DISPLAY, "
                + "gett.EVENT_TYPE_TEMPLATE_ID AS EVENT_VALUE, "
                + "ghct.NAME AS HARM_DISPLAY, "
                + "ghct.HARM_CATEGORY_TEMPLATE_ID AS HARM_VALUE, "
                + "e.PRESENT_ON_ADMISSION, "
                + "e.EVENT_DESCRIPTION "
                + "FROM "
                + "GTT_ABSTRACTOR_CASE gac "
                + "LEFT JOIN "
                + "GTT_ABSTRACTOR_CASE gac2 ON "
                + "gac.RECORD_ID = gac2.RECORD_ID "
                + "AND gac.USER_ID <> gac2.USER_ID "
                + "LEFT JOIN "
                + "TQMC_USER tu ON "
                + "gac2.USER_ID = tu.USER_ID "
                + "INNER JOIN GTT_WORKFLOW gw ON "
                + "gw.CASE_ID = gac.CASE_ID "
                + "AND (gac.USER_ID IS NULL OR gw.RECIPIENT_USER_ID = gac.USER_ID) "
                + "AND gw.IS_LATEST = 1 "
                + "INNER JOIN GTT_WORKFLOW gw2 ON "
                + "gw2.CASE_ID = gac2.CASE_ID "
                + "AND gw2.IS_LATEST = 1 "
                + "INNER JOIN TQMC_RECORD tr ON tr.RECORD_ID = gac.RECORD_ID "
                + "LEFT OUTER JOIN GTT_RECORD_CASE_EVENT e ON e.CASE_ID = gac.CASE_ID "
                + "LEFT OUTER JOIN GTT_TRIGGER_TEMPLATE gtt ON gtt.TRIGGER_TEMPLATE_ID = e.TRIGGER_TEMPLATE_ID "
                + "LEFT OUTER JOIN GTT_EVENT_TYPE_TEMPLATE gett ON gett.EVENT_TYPE_TEMPLATE_ID = e.EVENT_TYPE_TEMPLATE_ID "
                + "LEFT OUTER JOIN GTT_HARM_CATEGORY_TEMPLATE ghct ON ghct.HARM_CATEGORY_TEMPLATE_ID = e.HARM_CATEGORY_TEMPLATE_ID "
                + "WHERE gac.CASE_ID = ? AND (? IS NULL OR gac.USER_ID = ?) ORDER BY e.UPDATED_AT ASC")) {
      ps.setString(1, caseId);
      ps.setString(2, userId);
      ps.setString(3, userId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();

        List<Map<String, Object>> adverseEvents = new ArrayList<>();

        while (rs.next()) {
          if (adverseEvents.isEmpty()) {
            c.put("case_id", rs.getString("CASE_ID"));
            c.put("case_type", "abstraction");
            c.put("record_id", rs.getString("RECORD_ID"));
            c.put(
                "discharge_date",
                ConversionUtils.getLocalDateStringFromDate(rs.getDate("DISCHARGE_DATE")));
            c.put(
                "updated_at",
                ConversionUtils.getLocalDateTimeStringFromTimestamp(rs.getTimestamp("UPDATED_AT")));
            c.put("time_in_queue_days", rs.getInt("TIME_IN_QUEUE_DAYS"));
            c.put("length_of_stay_days", rs.getInt("LENGTH_OF_STAY_DAYS"));
            c.put("case_status", rs.getString("CASE_STATUS"));
            c.put("abstractor_2_user_name", rs.getString("ABSTRACTOR_2_USER_NAME"));
          }

          Map<String, Object> adverseEvent = new HashMap<>();
          adverseEvent.put("event_id", rs.getString("RECORD_CASE_EVENT_ID"));
          if (rs.wasNull()) {
            continue;
          }

          Map<String, Object> trigger = new HashMap<>();
          trigger.put("category", rs.getString("TRIGGER_CATEGORY"));
          trigger.put("display", rs.getString("TRIGGER_DISPLAY"));
          trigger.put("value", rs.getString("TRIGGER_VALUE"));
          adverseEvent.put("trigger", trigger);

          Map<String, Object> adverseEventType = new HashMap<>();
          adverseEventType.put("category", rs.getString("EVENT_CATEGORY"));
          adverseEventType.put("display", rs.getString("EVENT_DISPLAY"));
          adverseEventType.put("value", rs.getString("EVENT_VALUE"));
          adverseEvent.put(
              "adverse_event_type",
              (adverseEventType.get("value") != null) ? adverseEventType : null);

          Map<String, Object> harm = new HashMap<>();
          harm.put("display", rs.getString("HARM_DISPLAY"));
          harm.put("value", rs.getString("HARM_VALUE"));
          adverseEvent.put("level_of_harm", (harm.get("value") != null) ? harm : null);

          boolean res = rs.getInt("PRESENT_ON_ADMISSION") == 1;
          adverseEvent.put("present_on_admission", (rs.wasNull()) ? null : res);
          adverseEvent.put("description", rs.getString("EVENT_DESCRIPTION"));

          adverseEvents.add(adverseEvent);
        }

        if (!c.isEmpty()) {
          c.put("events", adverseEvents);
        }
      }
    }
    return c;
  }

  public static Map<String, Object> getGttConsensusCaseMap(
      Connection con, String userId, String caseId) throws SQLException {
    String abs1CaseId = null;
    String abs2CaseId = null;

    Map<String, Object> c = new HashMap<>();

    // base info
    LocalDateTime updatedAt = null;
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT "
                + "gcc.CASE_ID AS CASE_ID, "
                + "gcc.RECORD_ID AS RECORD_ID, "
                + "tr.DISCHARGE_DATE AS DISCHARGE_DATE, "
                + "DATEDIFF(DAY, gcc.CREATED_AT, CURRENT_TIMESTAMP) AS TIME_IN_QUEUE_DAYS, "
                + "tr.LENGTH_OF_STAY AS LENGTH_OF_STAY_DAYS, "
                + "gw.STEP_STATUS AS CASE_STATUS, "
                + "gcc.UPDATED_AT AS UPDATED_AT, "
                + "'consensus' AS CASE_TYPE, "
                + "tu1.USER_ID AS ABS_1_USER_ID, "
                + "CONCAT(tu1.FIRST_NAME, ' ', tu1.LAST_NAME) AS ABS_1_NAME, "
                + "tu1.EMAIL AS ABS_1_EMAIL, "
                + "tu2.USER_ID AS ABS_2_USER_ID, "
                + "CONCAT(tu2.FIRST_NAME, ' ', tu2.LAST_NAME) AS ABS_2_NAME, "
                + "tu2.EMAIL AS ABS_2_EMAIL, "
                + "gw1.SMSS_TIMESTAMP AS ABS_1_SUBMITTED, "
                + "gw2.SMSS_TIMESTAMP AS ABS_2_SUBMITTED, "
                + "gw1.CASE_ID AS ABS_1_CASE_ID, "
                + "gw2.CASE_ID AS ABS_2_CASE_ID "
                + "FROM GTT_CONSENSUS_CASE gcc "
                + "INNER JOIN GTT_WORKFLOW gw ON gw.CASE_ID = gcc.CASE_ID AND gw.IS_LATEST = 1 "
                + "INNER JOIN TQMC_RECORD tr ON tr.RECORD_ID = gcc.RECORD_ID "
                + "INNER JOIN GTT_WORKFLOW gw1 ON gw1.CASE_ID = gcc.ABS_1_CASE_ID AND gw1.IS_LATEST = 1 "
                + "INNER JOIN GTT_WORKFLOW gw2 ON gw2.CASE_ID = gcc.ABS_2_CASE_ID AND gw2.IS_LATEST = 1 "
                + "LEFT OUTER JOIN TQMC_USER tu1 ON tu1.USER_ID = gw1.RECIPIENT_USER_ID AND tu1.IS_ACTIVE = 1 "
                + "LEFT OUTER JOIN TQMC_USER tu2 ON tu2.USER_ID = gw2.RECIPIENT_USER_ID AND tu2.IS_ACTIVE = 1 "
                + "WHERE gcc.CASE_ID = ? AND (? IS NULL OR ? IN (gw1.RECIPIENT_USER_ID, gw2.RECIPIENT_USER_ID)) AND (? IS NULL OR (gw1.STEP_STATUS = 'completed' AND gw2.STEP_STATUS = 'completed'))")) {
      ps.setString(1, caseId);
      ps.setString(2, userId);
      ps.setString(3, userId);
      ps.setString(4, userId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          c.put("case_id", rs.getString("CASE_ID"));
          c.put("record_id", rs.getString("RECORD_ID"));
          c.put(
              "discharge_date",
              ConversionUtils.getLocalDateStringFromDate(rs.getDate("DISCHARGE_DATE")));
          c.put("time_in_queue_days", rs.getInt("TIME_IN_QUEUE_DAYS"));
          c.put("length_of_stay_days", rs.getInt("LENGTH_OF_STAY_DAYS"));
          c.put("case_status", rs.getString("CASE_STATUS"));
          updatedAt = ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("UPDATED_AT"));
          c.put("case_type", rs.getString("CASE_TYPE"));

          Map<String, Object> abstractorOne = new HashMap<>();
          abstractorOne.put("user_id", rs.getString("ABS_1_USER_ID"));
          abstractorOne.put("user_name", rs.getString("ABS_1_NAME"));
          abstractorOne.put("user_email", rs.getString("ABS_1_EMAIL"));
          c.put("abstractor_one", abstractorOne);

          Map<String, Object> abstractorTwo = new HashMap<>();
          abstractorTwo.put("user_id", rs.getString("ABS_2_USER_ID"));
          abstractorTwo.put("user_name", rs.getString("ABS_2_NAME"));
          abstractorTwo.put("user_email", rs.getString("ABS_2_EMAIL"));
          c.put("abstractor_two", abstractorTwo);

          c.put(
              "abstractor_one_submission_date",
              ConversionUtils.getLocalDateTimeStringFromTimestamp(
                  rs.getTimestamp("ABS_1_SUBMITTED")));
          c.put(
              "abstractor_two_submission_date",
              ConversionUtils.getLocalDateTimeStringFromTimestamp(
                  rs.getTimestamp("ABS_2_SUBMITTED")));

          abs1CaseId = rs.getString("ABS_1_CASE_ID");
          abs2CaseId = rs.getString("ABS_2_CASE_ID");
        }
      }
    }

    if (userId == null && c.isEmpty()) {
      return c;
    } else if (c.isEmpty()) {
      throw new TQMCException(ErrorCode.NOT_FOUND);
    }

    // notes
    List<Map<String, Object>> abs1Notes = new ArrayList<>();
    List<Map<String, Object>> abs2Notes = new ArrayList<>();
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT gcn.CASE_NOTE_ID, gcn.USER_ID, tu.FIRST_NAME, tu.LAST_NAME, gcn.NOTE, "
                + "gcn.IS_EXTERNAL, gcn.CREATED_AT, gcn.UPDATED_AT, gcn.CASE_ID "
                + "FROM GTT_CASE_NOTE gcn "
                + "LEFT OUTER JOIN TQMC_USER tu ON tu.USER_ID = gcn.USER_ID "
                + "WHERE gcn.CASE_ID IN (?, ?) ORDER BY gcn.CREATED_AT ASC")) {
      ps.setString(1, abs1CaseId);
      ps.setString(2, abs2CaseId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {

          Map<String, Object> note = new HashMap<>();
          note.put("note_id", rs.getString("CASE_NOTE_ID"));
          note.put("author_user_id", rs.getString("USER_ID"));
          note.put("author_first_name", rs.getString("FIRST_NAME"));
          note.put("author_last_name", rs.getString("LAST_NAME"));
          note.put("note", rs.getString("NOTE"));
          note.put("is_external", rs.getInt("IS_EXTERNAL") == 1);
          note.put(
              "created_at",
              ConversionUtils.getLocalDateTimeStringFromTimestamp(rs.getTimestamp("CREATED_AT")));
          note.put(
              "updated_at",
              ConversionUtils.getLocalDateTimeStringFromTimestamp(rs.getTimestamp("UPDATED_AT")));

          String noteCase = rs.getString("CASE_ID");
          if (abs1CaseId.equalsIgnoreCase(noteCase)) {
            abs1Notes.add(note);
          } else {
            abs2Notes.add(note);
          }
        }
      }
    }
    c.put("abstractor_one_notes", abs1Notes);
    c.put("abstractor_two_notes", abs2Notes);

    // events
    LocalDateTime latestEventCreatedAt = null;
    Map<String, Map<String, Object>> events = new HashMap<>();
    List<Map<String, Object>> abs1Events = new ArrayList<>();
    List<Map<String, Object>> abs2Events = new ArrayList<>();
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT "
                + "e.RECORD_CASE_EVENT_ID, "
                + "e.CASE_ID AS CASE_ID, "
                + "gtt.BUCKET AS TRIGGER_CATEGORY, "
                + "gtt.NAME AS TRIGGER_DISPLAY, "
                + "gtt.TRIGGER_TEMPLATE_ID AS TRIGGER_VALUE, "
                + "gett.BUCKET AS EVENT_CATEGORY, "
                + "gett.NAME AS EVENT_DISPLAY, "
                + "gett.EVENT_TYPE_TEMPLATE_ID AS EVENT_VALUE, "
                + "ghct.NAME AS HARM_DISPLAY, "
                + "ghct.HARM_CATEGORY_TEMPLATE_ID AS HARM_VALUE, "
                + "e.PRESENT_ON_ADMISSION,"
                + "e.EVENT_DESCRIPTION, "
                + "e.UPDATED_AT "
                + "FROM GTT_RECORD_CASE_EVENT e "
                + "LEFT OUTER JOIN GTT_TRIGGER_TEMPLATE gtt ON gtt.TRIGGER_TEMPLATE_ID = e.TRIGGER_TEMPLATE_ID "
                + "LEFT OUTER JOIN GTT_EVENT_TYPE_TEMPLATE gett ON gett.EVENT_TYPE_TEMPLATE_ID = e.EVENT_TYPE_TEMPLATE_ID "
                + "LEFT OUTER JOIN GTT_HARM_CATEGORY_TEMPLATE ghct ON ghct.HARM_CATEGORY_TEMPLATE_ID = e.HARM_CATEGORY_TEMPLATE_ID WHERE e.CASE_ID IN (?, ?, ?)")) {
      ps.setString(1, caseId);
      ps.setString(2, abs1CaseId);
      ps.setString(3, abs2CaseId);

      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          Map<String, Object> adverseEvent = new HashMap<>();

          String id = rs.getString("RECORD_CASE_EVENT_ID");
          String eventCaseId = rs.getString("CASE_ID");

          adverseEvent.put("event_id", id);

          Map<String, Object> trigger = new HashMap<>();
          trigger.put("category", rs.getString("TRIGGER_CATEGORY"));
          trigger.put("display", rs.getString("TRIGGER_DISPLAY"));
          trigger.put("value", rs.getString("TRIGGER_VALUE"));
          adverseEvent.put("trigger", trigger);

          Map<String, Object> adverseEventType = new HashMap<>();
          adverseEventType.put("category", rs.getString("EVENT_CATEGORY"));
          adverseEventType.put("display", rs.getString("EVENT_DISPLAY"));
          adverseEventType.put("value", rs.getString("EVENT_VALUE"));
          adverseEvent.put(
              "adverse_event_type",
              (adverseEventType.get("value") != null) ? adverseEventType : null);

          Map<String, Object> harm = new HashMap<>();
          harm.put("display", rs.getString("HARM_DISPLAY"));
          harm.put("value", rs.getString("HARM_VALUE"));
          adverseEvent.put("level_of_harm", (harm.get("value") != null) ? harm : null);

          boolean res = rs.getInt("PRESENT_ON_ADMISSION") == 1;
          adverseEvent.put("present_on_admission", (rs.wasNull()) ? null : res);
          adverseEvent.put("description", rs.getString("EVENT_DESCRIPTION"));

          LocalDateTime eventCreatedAt =
              ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("UPDATED_AT"));
          if (latestEventCreatedAt == null
              || eventCreatedAt != null && eventCreatedAt.isAfter(latestEventCreatedAt)) {
            latestEventCreatedAt = eventCreatedAt;
          }

          events.put(id, adverseEvent);

          if (abs1CaseId.equalsIgnoreCase(eventCaseId)) {
            abs1Events.add(adverseEvent);
          } else if (abs2CaseId.equalsIgnoreCase(eventCaseId)) {
            abs2Events.add(adverseEvent);
          }
        }
      }
    }
    c.put("abstractor_one_events", abs1Events);
    c.put("abstractor_two_events", abs2Events);

    c.put("updated_at", ConversionUtils.getLocalDateTimeString(updatedAt));

    // irr match
    int matchCount = 0;
    int irrCount = 0;
    Map<String, Map<String, Object>> irrs = new HashMap<>();
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT "
                + "ABSTRACTOR_ONE_RECORD_CASE_EVENT_ID, "
                + "ABSTRACTOR_TWO_RECORD_CASE_EVENT_ID, "
                + "ALTERNATE_RECORD_CASE_EVENT_ID, "
                + "SELECTED_CASE_EVENT_ID, "
                + "IS_DELETED, "
                + "IS_MATCH, "
                + "IRR_MATCH_ID "
                + "FROM GTT_IRR_MATCH WHERE CONSENSUS_CASE_ID = ?")) {
      ps.setString(1, caseId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {

          Map<String, Object> irr = new HashMap<>();

          irr.put(
              "abstractor_one_event",
              events.get(rs.getString("ABSTRACTOR_ONE_RECORD_CASE_EVENT_ID")));
          irr.put(
              "abstractor_two_event",
              events.get(rs.getString("ABSTRACTOR_TWO_RECORD_CASE_EVENT_ID")));
          irr.put(
              "alternate_record_case_event",
              events.get(rs.getString("ALTERNATE_RECORD_CASE_EVENT_ID")));
          irr.put("selected_event", events.get(rs.getString("SELECTED_CASE_EVENT_ID")));
          irr.put("is_deleted", rs.getInt("IS_DELETED") == 1);

          boolean isMatch = rs.getInt("IS_MATCH") == 1;
          irr.put("is_match", isMatch);

          irrs.put(rs.getString("IRR_MATCH_ID"), irr);

          if (isMatch) {
            matchCount++;
          }
          irrCount++;
        }
      }
    }
    if (irrCount > 0) {
      c.put("irr", Double.toString((double) matchCount / irrCount));
    } else {
      c.put("irr", "1");
    }
    c.put("abstracted_events_by_trigger", irrs);
    return c;
  }

  public static Map<String, Object> getGttPhysicianCaseMap(
      Connection con, String userId, String caseId) throws SQLException {
    String consensusCaseId = null;
    String abs1CaseId = null;
    String abs2CaseId = null;

    Map<String, Object> c = new HashMap<>();

    // base info
    LocalDateTime updatedAt = null;
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT "
                + "gpc.CASE_ID AS CASE_ID, "
                + "gpc.RECORD_ID AS RECORD_ID, "
                + "tr.DISCHARGE_DATE AS DISCHARGE_DATE, "
                + "DATEDIFF(DAY, gpc.CREATED_AT, CURRENT_TIMESTAMP) AS TIME_IN_QUEUE_DAYS, "
                + "tr.LENGTH_OF_STAY AS LENGTH_OF_STAY_DAYS, "
                + "gw.STEP_STATUS AS CASE_STATUS, "
                + "gpc.UPDATED_AT AS UPDATED_AT, "
                + "gwc.SMSS_TIMESTAMP AS CONSENSUS_SUBMISSION_DATE, "
                + "tu1.USER_ID AS ABS_1_USER_ID, "
                + "CONCAT(tu1.FIRST_NAME, ' ', tu1.LAST_NAME) AS ABS_1_NAME, "
                + "tu1.EMAIL AS ABS_1_EMAIL, "
                + "tu2.USER_ID AS ABS_2_USER_ID, "
                + "CONCAT(tu2.FIRST_NAME, ' ', tu2.LAST_NAME) AS ABS_2_NAME, "
                + "tu2.EMAIL AS ABS_2_EMAIL, "
                + "gpc.CONSENSUS_CASE_ID, "
                + "gcc.ABS_1_CASE_ID, "
                + "gcc.ABS_2_CASE_ID "
                + "FROM "
                + "GTT_PHYSICIAN_CASE gpc "
                + "INNER JOIN GTT_WORKFLOW gw ON "
                + "gw.CASE_ID = gpc.CASE_ID "
                + "AND (gpc.PHYSICIAN_USER_ID IS NULL OR gw.RECIPIENT_USER_ID = gpc.PHYSICIAN_USER_ID) "
                + "AND gw.RECIPIENT_STAGE ILIKE 'PHYSICIAN REVIEW' "
                + "AND gw.IS_LATEST = 1 "
                + "INNER JOIN TQMC_RECORD tr ON "
                + "tr.RECORD_ID = gpc.RECORD_ID "
                + "INNER JOIN GTT_WORKFLOW gwc ON "
                + "gwc.CASE_ID = gpc.CONSENSUS_CASE_ID "
                + "AND gwc.IS_LATEST = 1 "
                + "AND (? IS NULL OR gwc.STEP_STATUS = 'completed') "
                + "INNER JOIN GTT_CONSENSUS_CASE gcc ON "
                + "gcc.CASE_ID = gpc.CONSENSUS_CASE_ID "
                + "INNER JOIN GTT_WORKFLOW gw1 ON "
                + "gw1.CASE_ID = gcc.ABS_1_CASE_ID "
                + "AND gw1.IS_LATEST = 1 "
                + "AND (? IS NULL OR gw1.STEP_STATUS = 'completed') "
                + "INNER JOIN GTT_WORKFLOW gw2 ON "
                + "gw2.CASE_ID = gcc.ABS_2_CASE_ID "
                + "AND gw2.IS_LATEST = 1 "
                + "AND (? IS NULL OR gw2.STEP_STATUS = 'completed') "
                + "LEFT OUTER JOIN TQMC_USER tu1 ON "
                + "tu1.USER_ID = gw1.RECIPIENT_USER_ID "
                + "AND tu1.IS_ACTIVE = 1 "
                + "LEFT OUTER JOIN TQMC_USER tu2 ON "
                + "tu2.USER_ID = gw2.RECIPIENT_USER_ID "
                + "AND tu2.IS_ACTIVE = 1 "
                + "WHERE "
                + "(? IS NULL OR gpc.PHYSICIAN_USER_ID = ?) "
                + "AND gpc.CASE_ID = ? ")) {
      ps.setString(1, userId);
      ps.setString(2, userId);
      ps.setString(3, userId);
      ps.setString(4, userId);
      ps.setString(5, userId);
      ps.setString(6, caseId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          c.put("case_id", rs.getString("CASE_ID"));
          c.put("record_id", rs.getString("RECORD_ID"));
          c.put(
              "discharge_date",
              ConversionUtils.getLocalDateStringFromDate(rs.getDate("DISCHARGE_DATE")));
          c.put("time_in_queue_days", rs.getInt("TIME_IN_QUEUE_DAYS"));
          c.put("length_of_stay_days", rs.getInt("LENGTH_OF_STAY_DAYS"));
          c.put("case_status", rs.getString("CASE_STATUS"));
          updatedAt = ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("UPDATED_AT"));
          c.put("case_type", "physician");

          c.put(
              "consensus_submission_date",
              ConversionUtils.getLocalDateTimeStringFromTimestamp(
                  rs.getTimestamp("CONSENSUS_SUBMISSION_DATE")));

          Map<String, Object> abstractorOne = new HashMap<>();
          abstractorOne.put("user_id", rs.getString("ABS_1_USER_ID"));
          abstractorOne.put("user_name", rs.getString("ABS_1_NAME"));
          abstractorOne.put("user_email", rs.getString("ABS_1_EMAIL"));
          c.put("abstractor_one", abstractorOne);

          Map<String, Object> abstractorTwo = new HashMap<>();
          abstractorTwo.put("user_id", rs.getString("ABS_2_USER_ID"));
          abstractorTwo.put("user_name", rs.getString("ABS_2_NAME"));
          abstractorTwo.put("user_email", rs.getString("ABS_2_EMAIL"));
          c.put("abstractor_two", abstractorTwo);

          consensusCaseId = rs.getString("CONSENSUS_CASE_ID");
          abs1CaseId = rs.getString("ABS_1_CASE_ID");
          abs2CaseId = rs.getString("ABS_2_CASE_ID");
        }
      }
    }

    if (userId == null && c.isEmpty()) {
      return c;
    } else if (c.isEmpty()) {
      throw new TQMCException(ErrorCode.NOT_FOUND);
    }

    // notes
    List<Map<String, Object>> consensusNotes = new ArrayList<>();
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT gcn.CASE_NOTE_ID, gcn.USER_ID, tu.FIRST_NAME, tu.LAST_NAME, gcn.NOTE, "
                + "gcn.IS_EXTERNAL, gcn.CREATED_AT, gcn.UPDATED_AT "
                + "FROM GTT_CASE_NOTE gcn "
                + "LEFT OUTER JOIN TQMC_USER tu ON tu.USER_ID = gcn.USER_ID "
                + "WHERE gcn.CASE_ID = ? ORDER BY gcn.CREATED_AT ASC")) {
      ps.setString(1, consensusCaseId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {

          Map<String, Object> note = new HashMap<>();
          note.put("note_id", rs.getString("CASE_NOTE_ID"));
          note.put("author_user_id", rs.getString("USER_ID"));
          note.put("author_first_name", rs.getString("FIRST_NAME"));
          note.put("author_last_name", rs.getString("LAST_NAME"));
          note.put("note", rs.getString("NOTE"));
          note.put("is_external", rs.getInt("IS_EXTERNAL") == 1);
          note.put(
              "created_at",
              ConversionUtils.getLocalDateTimeStringFromTimestamp(rs.getTimestamp("CREATED_AT")));
          note.put(
              "updated_at",
              ConversionUtils.getLocalDateTimeStringFromTimestamp(rs.getTimestamp("UPDATED_AT")));

          consensusNotes.add(note);
        }
      }
    }
    c.put("consensus_notes", consensusNotes);

    // events
    LocalDateTime latestEventCreatedAt = null;
    Map<String, Map<String, Object>> events = new HashMap<>();
    List<Map<String, Object>> physicianEvents = new ArrayList<>();
    List<Map<String, Object>> consensusEvents = new ArrayList<>();
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT "
                + "e.RECORD_CASE_EVENT_ID, "
                + "e.CASE_ID AS CASE_ID, "
                + "gtt.BUCKET AS TRIGGER_CATEGORY, "
                + "gtt.NAME AS TRIGGER_DISPLAY, "
                + "gtt.TRIGGER_TEMPLATE_ID AS TRIGGER_VALUE, "
                + "gett.BUCKET AS EVENT_CATEGORY, "
                + "gett.NAME AS EVENT_DISPLAY, "
                + "gett.EVENT_TYPE_TEMPLATE_ID AS EVENT_VALUE, "
                + "ghct.NAME AS HARM_DISPLAY, "
                + "ghct.HARM_CATEGORY_TEMPLATE_ID AS HARM_VALUE, "
                + "e.PRESENT_ON_ADMISSION,"
                + "e.EVENT_DESCRIPTION, "
                + "e.UPDATED_AT, "
                + "e.IS_DELETED "
                + "FROM GTT_RECORD_CASE_EVENT e "
                + "LEFT OUTER JOIN GTT_TRIGGER_TEMPLATE gtt ON gtt.TRIGGER_TEMPLATE_ID = e.TRIGGER_TEMPLATE_ID "
                + "LEFT OUTER JOIN GTT_EVENT_TYPE_TEMPLATE gett ON gett.EVENT_TYPE_TEMPLATE_ID = e.EVENT_TYPE_TEMPLATE_ID "
                + "LEFT OUTER JOIN GTT_HARM_CATEGORY_TEMPLATE ghct ON ghct.HARM_CATEGORY_TEMPLATE_ID = e.HARM_CATEGORY_TEMPLATE_ID "
                + "WHERE e.CASE_ID IN (?, ?, ?, ?)")) {
      ps.setString(1, caseId);
      ps.setString(2, consensusCaseId);
      ps.setString(3, abs1CaseId);
      ps.setString(4, abs2CaseId);

      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          Map<String, Object> adverseEvent = new HashMap<>();

          String id = rs.getString("RECORD_CASE_EVENT_ID");
          String eventCaseId = rs.getString("CASE_ID");

          adverseEvent.put("event_id", id);

          Map<String, Object> trigger = new HashMap<>();
          trigger.put("category", rs.getString("TRIGGER_CATEGORY"));
          trigger.put("display", rs.getString("TRIGGER_DISPLAY"));
          trigger.put("value", rs.getString("TRIGGER_VALUE"));
          adverseEvent.put("trigger", trigger);

          Map<String, Object> adverseEventType = new HashMap<>();
          adverseEventType.put("category", rs.getString("EVENT_CATEGORY"));
          adverseEventType.put("display", rs.getString("EVENT_DISPLAY"));
          adverseEventType.put("value", rs.getString("EVENT_VALUE"));
          adverseEvent.put(
              "adverse_event_type",
              (adverseEventType.get("value") != null) ? adverseEventType : null);

          Map<String, Object> harm = new HashMap<>();
          harm.put("display", rs.getString("HARM_DISPLAY"));
          harm.put("value", rs.getString("HARM_VALUE"));
          adverseEvent.put("level_of_harm", (harm.get("value") != null) ? harm : null);

          boolean res = rs.getInt("PRESENT_ON_ADMISSION") == 1;
          adverseEvent.put("present_on_admission", (rs.wasNull()) ? null : res);
          adverseEvent.put("description", rs.getString("EVENT_DESCRIPTION"));
          adverseEvent.put("is_deleted", rs.getBoolean("IS_DELETED"));

          LocalDateTime eventCreatedAt =
              ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("UPDATED_AT"));
          if (latestEventCreatedAt == null
              || eventCreatedAt != null && eventCreatedAt.isAfter(latestEventCreatedAt)) {
            latestEventCreatedAt = eventCreatedAt;
          }

          events.put(id, adverseEvent);

          if (consensusCaseId.equalsIgnoreCase(eventCaseId)) {
            consensusEvents.add(adverseEvent);
          } else if (caseId.equalsIgnoreCase(eventCaseId)) {
            physicianEvents.add(adverseEvent);
          }
        }
      }
    }
    c.put("consensus_events", consensusEvents);
    c.put("events", physicianEvents);

    c.put("updated_at", ConversionUtils.getLocalDateTimeString(updatedAt));

    // irr match
    int matchCount = 0;
    int irrCount = 0;
    Map<String, Map<String, Object>> irrs = new HashMap<>();
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT "
                + "ABSTRACTOR_ONE_RECORD_CASE_EVENT_ID, "
                + "ABSTRACTOR_TWO_RECORD_CASE_EVENT_ID, "
                + "ALTERNATE_RECORD_CASE_EVENT_ID, "
                + "SELECTED_CASE_EVENT_ID, "
                + "IS_DELETED, "
                + "IS_MATCH, "
                + "IRR_MATCH_ID "
                + "FROM GTT_IRR_MATCH WHERE CONSENSUS_CASE_ID = ?")) {
      ps.setString(1, consensusCaseId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          Map<String, Object> irr = new HashMap<>();

          irr.put(
              "abstractor_one_event",
              events.get(rs.getString("ABSTRACTOR_ONE_RECORD_CASE_EVENT_ID")));
          irr.put(
              "abstractor_two_event",
              events.get(rs.getString("ABSTRACTOR_TWO_RECORD_CASE_EVENT_ID")));
          irr.put(
              "alternate_record_case_event",
              events.get(rs.getString("ALTERNATE_RECORD_CASE_EVENT_ID")));
          irr.put("selected_event", events.get(rs.getString("SELECTED_CASE_EVENT_ID")));
          irr.put("is_deleted", rs.getInt("IS_DELETED") == 1);

          boolean isMatch = rs.getInt("IS_MATCH") == 1;
          irr.put("is_match", isMatch);

          irrs.put(rs.getString("IRR_MATCH_ID"), irr);

          if (isMatch) {
            matchCount++;
          }
          irrCount++;
        }
      }
    }
    if (irrCount > 0) {
      c.put("irr", Double.toString((double) matchCount / irrCount));
    } else {
      c.put("irr", "1");
    }
    c.put("abstracted_events_by_trigger", irrs);
    return c;
  }

  public static String getUnassignedString(ResultSet rs, String columnName) throws SQLException {
    String value = rs.getString(columnName);
    return (value == null || value.trim().isEmpty()) ? "unassigned" : value;
  }

  // Creates all needed GTT Cases from records
  // records is a map of <RECORD_ID, DATE> Date is DISCHARGE_DATE normally
  public static void createGttCases(
      Connection con,
      Map<String, LocalDateTime> records,
      String abs1UserId,
      String abs2UserId,
      String physUserId,
      LocalDateTime currentTimestamp)
      throws SQLException {
    // Abstractor, Consensus, and Physician cases need to be made
    // We need to add the workflow for each created case.

    // id won't be displayed on the FE
    int gttPadLength = TQMCConstants.PADDING_LENGTH;

    // Create the case and workflow
    String insertAbsCasesQuery =
        "INSERT INTO GTT_ABSTRACTOR_CASE "
            + "(CASE_ID, CREATED_AT, RECORD_ID, USER_ID) "
            + "VALUES (?,?,?,?);";
    String insertCasesTimeQuery =
        "INSERT INTO GTT_CASE_TIME (CASE_ID, CASE_TIME_ID, CASE_TYPE) VALUES (?, ?, ?)";
    String insertConCasesQuery =
        "INSERT INTO GTT_CONSENSUS_CASE "
            + "(ABS_1_CASE_ID, ABS_2_CASE_ID, CASE_ID, CREATED_AT, RECORD_ID) "
            + "VALUES (?,?,?,?,?);";
    String insertPhyCasesQuery =
        "INSERT INTO GTT_PHYSICIAN_CASE "
            + "(CASE_ID, CONSENSUS_CASE_ID, CREATED_AT, RECORD_ID, PHYSICIAN_USER_ID) "
            + "VALUES (?,?,?,?,?);";
    String insertWfQuery =
        "INSERT INTO GTT_WORKFLOW (CASE_ID, CASE_TYPE, GUID, IS_LATEST, RECIPIENT_STAGE, "
            + "RECIPIENT_USER_ID, SENDING_STAGE, SENDING_USER_ID, SMSS_TIMESTAMP, STEP_STATUS) "
            + "VALUES (?,?,?,?,?, ?,?,?,?,?)";

    try (PreparedStatement psAbs = con.prepareStatement(insertAbsCasesQuery);
        PreparedStatement psTime = con.prepareStatement(insertCasesTimeQuery);
        PreparedStatement psCon = con.prepareStatement(insertConCasesQuery);
        PreparedStatement psPhy = con.prepareStatement(insertPhyCasesQuery);
        PreparedStatement psWf = con.prepareStatement(insertWfQuery)) {
      for (String record : records.keySet()) {
        int colIndexAbs = 1;

        String abs1CaseId =
            getDisplayId(
                TQMCConstants.GTT + "-ABS", getNextId(con, "GTT_ABSTRACTOR_CASE"), gttPadLength);
        String abs2CaseId =
            getDisplayId(
                TQMCConstants.GTT + "-ABS", getNextId(con, "GTT_ABSTRACTOR_CASE"), gttPadLength);
        // Abstraction cases
        psAbs.setString(colIndexAbs++, abs1CaseId);
        psAbs.setObject(colIndexAbs++, currentTimestamp);
        psAbs.setString(colIndexAbs++, record);
        psAbs.setString(colIndexAbs, abs1UserId);
        psAbs.addBatch();
        // Second Abstraction case
        psAbs.setString(1, abs2CaseId);
        psAbs.setString(colIndexAbs, abs2UserId);
        psAbs.addBatch();
        // Consensus case
        int colIndexCon = 1;
        psCon.setString(colIndexCon++, abs1CaseId);
        psCon.setString(colIndexCon++, abs2CaseId);
        String conId =
            getDisplayId(
                TQMCConstants.GTT + "-CON", getNextId(con, "GTT_CONSENSUS_CASE"), gttPadLength);
        psCon.setString(colIndexCon++, conId);
        psCon.setObject(colIndexCon++, currentTimestamp);
        psCon.setString(colIndexCon++, record);
        psCon.addBatch();

        // Physician case
        int colIndexPhy = 1;
        String phyId =
            getDisplayId(
                TQMCConstants.GTT + "-PHY", getNextId(con, "GTT_PHYSICIAN_CASE"), gttPadLength);
        psPhy.setString(colIndexPhy++, phyId);
        psPhy.setString(colIndexPhy++, conId);
        psPhy.setObject(colIndexPhy++, currentTimestamp);
        psPhy.setString(colIndexPhy++, record);
        psPhy.setString(colIndexPhy++, physUserId);
        psPhy.addBatch();

        // GTT Timers
        int colIndexTime = 1;
        // Abs 1
        psTime.setString(colIndexTime++, abs1CaseId);
        psTime.setString(colIndexTime++, UUID.randomUUID().toString());
        psTime.setString(colIndexTime++, TQMCConstants.GTT_CASE_TYPE_ABSTRACTOR);
        psTime.addBatch();

        // Abs 2
        colIndexTime = 1;
        psTime.setString(colIndexTime++, abs2CaseId);
        psTime.setString(colIndexTime++, UUID.randomUUID().toString());
        psTime.addBatch();

        // Physician
        colIndexTime = 1;
        psTime.setString(colIndexTime++, phyId);
        psTime.setString(colIndexTime++, UUID.randomUUID().toString());
        psTime.setString(colIndexTime++, TQMCConstants.GTT_CASE_TYPE_PHYSICIAN);
        psTime.addBatch();

        // GTT Workflows
        int parameterIndex = 1;
        String systemId = "system";
        // Start with system creates for Abstraction
        // Abs 1
        psWf.setString(parameterIndex++, abs1CaseId);
        psWf.setString(parameterIndex++, TQMCConstants.GTT_CASE_TYPE_ABSTRACTOR);
        psWf.setString(parameterIndex++, UUID.randomUUID().toString());
        psWf.setInt(parameterIndex++, 0);
        psWf.setString(parameterIndex++, TQMCConstants.GTT_STAGE_ABSTRACTION.toUpperCase());
        psWf.setString(parameterIndex++, systemId);
        psWf.setString(parameterIndex++, null);
        psWf.setString(parameterIndex++, null);
        psWf.setObject(parameterIndex++, currentTimestamp);
        psWf.setString(parameterIndex++, TQMCConstants.CASE_STEP_STATUS_UNASSIGNED);
        psWf.addBatch();
        // Abs 2
        psWf.setString(1, abs2CaseId);
        psWf.setString(3, UUID.randomUUID().toString());
        psWf.addBatch();
        // Abstraction assign
        parameterIndex = 1;
        // Abs 1
        psWf.setString(parameterIndex++, abs1CaseId);
        psWf.setString(parameterIndex++, TQMCConstants.GTT_CASE_TYPE_ABSTRACTOR);
        psWf.setString(parameterIndex++, UUID.randomUUID().toString());
        psWf.setInt(parameterIndex++, 1);
        psWf.setString(parameterIndex++, TQMCConstants.GTT_STAGE_ABSTRACTION.toUpperCase());
        psWf.setString(parameterIndex++, abs1UserId);
        psWf.setString(parameterIndex++, TQMCConstants.GTT_STAGE_ABSTRACTION.toUpperCase());
        psWf.setString(parameterIndex++, systemId);
        psWf.setObject(parameterIndex++, currentTimestamp);
        psWf.setString(parameterIndex++, TQMCConstants.CASE_STEP_STATUS_NOT_STARTED);
        psWf.addBatch();
        // Abs 2
        psWf.setString(1, abs2CaseId);
        psWf.setString(3, UUID.randomUUID().toString());
        psWf.setString(6, abs2UserId);
        psWf.addBatch();
        // Consensus case, only creating one since no one can be assigned
        // System assign
        parameterIndex = 1;
        psWf.setString(parameterIndex++, conId);
        psWf.setString(parameterIndex++, TQMCConstants.GTT_CASE_TYPE_CONSENSUS);
        psWf.setString(parameterIndex++, UUID.randomUUID().toString());
        psWf.setInt(parameterIndex++, 1);
        psWf.setString(parameterIndex++, TQMCConstants.GTT_STAGE_CONSENSUS.toUpperCase());
        psWf.setString(parameterIndex++, systemId);
        psWf.setString(parameterIndex++, TQMCConstants.GTT_STAGE_CONSENSUS.toUpperCase());
        psWf.setString(parameterIndex++, systemId);
        psWf.setObject(parameterIndex++, currentTimestamp);
        psWf.setString(parameterIndex++, TQMCConstants.CASE_STEP_STATUS_NOT_STARTED);
        psWf.addBatch();
        // Physician case
        parameterIndex = 1;
        psWf.setString(parameterIndex++, phyId);
        psWf.setString(parameterIndex++, TQMCConstants.GTT_CASE_TYPE_PHYSICIAN);
        psWf.setString(parameterIndex++, UUID.randomUUID().toString());
        psWf.setInt(parameterIndex++, 0);
        psWf.setString(parameterIndex++, TQMCConstants.GTT_STAGE_PHYSICIAN_REVIEW.toUpperCase());
        psWf.setString(parameterIndex++, systemId);
        psWf.setString(parameterIndex++, null);
        psWf.setString(parameterIndex++, null);
        psWf.setObject(parameterIndex++, currentTimestamp);
        psWf.setString(parameterIndex++, TQMCConstants.CASE_STEP_STATUS_UNASSIGNED);
        psWf.addBatch();
        // Physician assign
        parameterIndex = 1;
        psWf.setString(parameterIndex++, phyId);
        psWf.setString(parameterIndex++, TQMCConstants.GTT_CASE_TYPE_PHYSICIAN);
        psWf.setString(parameterIndex++, UUID.randomUUID().toString());
        psWf.setInt(parameterIndex++, 1);
        psWf.setString(parameterIndex++, TQMCConstants.GTT_STAGE_PHYSICIAN_REVIEW.toUpperCase());
        psWf.setString(parameterIndex++, physUserId);
        psWf.setString(parameterIndex++, TQMCConstants.GTT_STAGE_PHYSICIAN_REVIEW.toUpperCase());
        psWf.setString(parameterIndex++, systemId);
        psWf.setObject(parameterIndex++, currentTimestamp);
        psWf.setString(parameterIndex++, TQMCConstants.CASE_STEP_STATUS_NOT_STARTED);
        psWf.addBatch();
      }
      psAbs.executeBatch();
      psCon.executeBatch();
      psPhy.executeBatch();
      psTime.executeBatch();
      psWf.executeBatch();
    }
  }

  public static void checkReassignGttPermissions(
      Connection con, String caseId, String caseType, String assigneeUserId) throws SQLException {

    // Check if the new Assignee is a existing user
    TQMCUserInfo newAssignee = TQMCHelper.getTQMCUserInfo(con, assigneeUserId);
    if (newAssignee == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND, "User not found");
    }

    // Check if the new Assignee has the GTT product
    if (!newAssignee.getProducts().contains(TQMCConstants.GTT)) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "New assignee must have GTT access");
    }

    // Check if a valid case type is passed in
    // Only Physician and Abstraction cases can be reassigned
    if (!TQMCConstants.GTT_CASE_TYPE_ABSTRACTOR.equals(caseType)
        && !TQMCConstants.GTT_CASE_TYPE_PHYSICIAN.equals(caseType)) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid Case Type");
    }

    // If the case type is abstractor, checks to see the new assignee has the
    // abstractor role or is
    // a management lead
    if (TQMCConstants.GTT_CASE_TYPE_ABSTRACTOR.equals(caseType)
        && (!TQMCConstants.ABSTRACTOR.equals(newAssignee.getRole())
            && !TQMCConstants.MANAGEMENT_LEAD.equals(newAssignee.getRole()))) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST, "User does not have the correct role for this case");
    }
    // If the case type is physician, checks to see the new assignee has the
    // physician role
    if (TQMCConstants.GTT_CASE_TYPE_PHYSICIAN.equals(caseType)
        && !TQMCConstants.PHYSICIAN.equals(newAssignee.getRole())) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST, "User does not have the correct role for this case");
    }

    // Check to make sure the Case ID passed in matches the case type
    String checkCaseType = TQMCHelper.getCaseType(con, caseId);
    if (checkCaseType == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Case Not Found");
    }
    if (!caseType.equals(checkCaseType)) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Case Type does not match the given Case ID");
    }

    // check if user is already on record
    if (TQMCHelper.isAssignedOnConsensusCase(con, caseId, assigneeUserId)) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST,
          "Unable to reassign user to this case as they are already the second abstractor");
    }
  }

  public static Set<String> getActiveProductIds(Connection con) throws SQLException {
    Set<String> result = new HashSet<>();
    try (PreparedStatement ps =
        con.prepareStatement("SELECT PRODUCT_ID FROM TQMC_PRODUCT WHERE IS_ACTIVE=1")) {
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          result.add(rs.getString(1));
        }
      }
    }
    return result;
  }

  public static String getCaseType(Connection con, String caseId) throws SQLException {
    try (PreparedStatement getType =
        con.prepareStatement(
            "SELECT CASE_TYPE "
                + "FROM GTT_WORKFLOW GW "
                + "WHERE GW.CASE_ID = ? AND IS_LATEST = 1")) {
      getType.setString(1, caseId);
      ResultSet rs = getType.executeQuery();
      if (rs.next()) {
        return rs.getString("CASE_TYPE");
      }
    }
    return "No Case Found";
  }

  public static Map<String, Object> getMnAppealInfo(Connection con, String appealTypeId)
      throws SQLException {
    Map<String, Object> appealInfo = new HashMap<>();
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT APPEAL_TYPE_NAME, IS_SECOND_LEVEL "
                + "FROM MN_APPEAL_TYPE MAT "
                + "WHERE MAT.APPEAL_TYPE_ID = ?")) {
      ps.setString(1, appealTypeId);
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        appealInfo.put("appeal_type", rs.getString("APPEAL_TYPE_NAME"));
        appealInfo.put("is_second_level", rs.getInt("IS_SECOND_LEVEL") == 1);
      }
    }
    return appealInfo;
  }

  public static String getRecordId(Connection con, String caseId, ProductTables product)
      throws SQLException {
    return getRecordId(con, caseId, product, null);
  }

  public static String getRecordId(
      Connection con, String caseId, ProductTables product, String caseType) throws SQLException {
    String table = product.getCaseTable();
    String caseIdColumn = "CASE_ID";
    String recordId = null;
    if (product == ProductTables.SOC) {

      if (TQMCConstants.CASE_TYPE_NURSE_REVIEW.equals(caseType)) {
        table = TQMCConstants.TABLE_SOC_NURSE_REVIEW;
        caseIdColumn = "NURSE_REVIEW_ID";
      }
    }

    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT RECORD_ID FROM " + table + " WHERE " + caseIdColumn + " = ?")) {
      ps.setString(1, caseId);
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        recordId = rs.getString("RECORD_ID");
      }
    }

    if (recordId == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND);
    }

    return recordId;
  }

  public static String getAliasRecordId(Connection con, String caseId, ProductTables product)
      throws SQLException {
    return getAliasRecordId(con, caseId, product, null);
  }

  public static String getAliasRecordId(
      Connection con, String caseId, ProductTables product, String caseType) throws SQLException {
    String table = product.getCaseTable();
    String recordTable = product.getRecordTable();
    String caseIdColumn = "CASE_ID";
    String recordIdColumn = "RECORD_ID";
    String aliasRecordId = null;
    if (product == ProductTables.SOC) {

      if (TQMCConstants.CASE_TYPE_NURSE_REVIEW.equals(caseType)) {
        table = TQMCConstants.TABLE_SOC_NURSE_REVIEW;
        caseIdColumn = "NURSE_REVIEW_ID";
      }
    }

    /**
     * The below statement is a bit opaque. Here's what it outputs for future dev Example Statement
     * SELECT ALIAS_RECORD_ID FROM MN_CASE LEFT JOIN MN_RECORD ON MN_CASE.RECORD_ID =
     * MN_RECORD.RECORD_ID WHERE CASE_ID = ?
     */
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT ALIAS_RECORD_ID FROM "
                + table
                + " LEFT JOIN "
                + recordTable
                + " ON "
                + table
                + "."
                + recordIdColumn
                + " = "
                + recordTable
                + "."
                + recordIdColumn
                + " WHERE "
                + caseIdColumn
                + " = ?")) {
      ps.setString(1, caseId);
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        aliasRecordId = rs.getString("ALIAS_RECORD_ID");
      }
    }

    if (aliasRecordId == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND);
    }

    return aliasRecordId;
  }

  public static boolean hasFilesForRecord(Connection con, String recordId, ProductTables product)
      throws SQLException {
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT 1 FROM "
                + product.getFileTable()
                + " WHERE RECORD_ID = ? AND DELETED_AT IS NULL")) {
      ps.setString(1, recordId);
      ResultSet rs = ps.executeQuery();
      return rs.next();
    }
  }

  public static String getGttRecordId(Connection con, String caseId, String caseType)
      throws SQLException {
    String recordId = null;
    if (TQMCConstants.GTT_CASE_TYPE_ABSTRACTOR.equalsIgnoreCase(caseType)) {
      // Query the Abstractor case table for a specific case id
      // Return the RECORD_ID assigned to the case
      try (PreparedStatement findRecordId =
          con.prepareStatement(
              "SELECT RECORD_ID " + "FROM GTT_ABSTRACTOR_CASE " + "WHERE CASE_ID = ?"); ) {
        findRecordId.setString(1, caseId);
        ResultSet rs = findRecordId.executeQuery();
        if (rs.next()) {
          recordId = rs.getString("RECORD_ID");
        }
      }
    } else if (TQMCConstants.GTT_CASE_TYPE_PHYSICIAN.equalsIgnoreCase(caseType)) {
      // Query the Physician case table for a specific case id
      // Return the RECORD_ID assigned to the case
      try (PreparedStatement findRecordId =
          con.prepareStatement(
              "SELECT RECORD_ID " + "FROM GTT_PHYSICIAN_CASE " + "WHERE CASE_ID = ?"); ) {
        findRecordId.setString(1, caseId);
        ResultSet rs = findRecordId.executeQuery();
        if (rs.next()) {
          recordId = rs.getString("RECORD_ID");
        }
      }
    } else {
      // Query the Consensus case table for a specific case id
      // Return the RECORD_ID assigned to the case
      try (PreparedStatement findRecordId =
          con.prepareStatement(
              "SELECT RECORD_ID " + "FROM GTT_CONSENSUS_CASE " + "WHERE CASE_ID = ?"); ) {
        findRecordId.setString(1, caseId);
        ResultSet rs = findRecordId.executeQuery();
        if (rs.next()) {
          recordId = rs.getString("RECORD_ID");
        }
      }
    }
    return recordId;
  }

  public static boolean recordIdExists(Connection con, String recordId, String product)
      throws SQLException {
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT 1 FROM " + product.toUpperCase() + "_RECORD WHERE RECORD_ID = ?")) {
      ps.setString(1, recordId);
      ResultSet rs = ps.executeQuery();
      return rs.next();
    }
  }

  /** @deprecated */
  public static void createGttPhysicianCase(
      Connection con,
      String oldCaseId,
      String consensusId,
      String management_lead_id,
      String userId)
      throws SQLException {
    String newCaseId = null;
    String recordId = getGttRecordId(con, oldCaseId, TQMCConstants.GTT_CASE_TYPE_PHYSICIAN);
    LocalDateTime now = ConversionUtils.getUTCFromLocalNow();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    String formattedDateTime = now.format(formatter);

    // Set up the case data to insert the new Physician Case
    try (PreparedStatement insertQuery =
        con.prepareStatement(
            "INSERT INTO GTT_PHYSICIAN_CASE "
                + "(CASE_ID, RECORD_ID, CONSENSUS_CASE_ID, PHYSICIAN_USER_ID, CREATED_AT) "
                + "VALUES (?, ?, ?, ?, ?)"); ) {
      // Generating a new unique case id
      newCaseId =
          getDisplayId(
              TQMCConstants.GTT + "-PHY",
              getNextId(con, "GTT_PHYSICIAN_CASE"),
              TQMCConstants.PADDING_LENGTH);

      // Setting up the values of the new case's fields
      int i = 1;
      insertQuery.setString(i++, newCaseId);
      insertQuery.setString(i++, recordId);
      insertQuery.setString(i++, consensusId);
      insertQuery.setString(i++, userId);
      insertQuery.setString(i++, formattedDateTime);
      insertQuery.execute();
    }
    // Inserting the new physician case into case time table
    try (PreparedStatement insertQuery =
        con.prepareStatement(
            "INSERT INTO GTT_CASE_TIME (CASE_ID, CASE_TIME_ID, CASE_TYPE) " + "VALUES (?, ?, ?)")) {
      int i = 1;
      insertQuery.setString(i++, newCaseId);
      insertQuery.setString(i++, UUID.randomUUID().toString());
      insertQuery.setString(i++, TQMCConstants.GTT_CASE_TYPE_PHYSICIAN);
      insertQuery.execute();
    }
    // Adding the new case to the GTT Record Case Event Table
    // With the previous physician data
    try (PreparedStatement insertRCET =
        con.prepareStatement(
            "INSERT INTO GTT_RECORD_CASE_EVENT ("
                + "RECORD_CASE_EVENT_ID, CASE_ID, CASE_TYPE, UPDATED_AT, "
                + "CONSENSUS_LINK_CASE_EVENT_ID, EVENT_DESCRIPTION, "
                + "EVENT_TYPE_TEMPLATE_ID, HARM_CATEGORY_TEMPLATE_ID, "
                + "PRESENT_ON_ADMISSION, TRIGGER_TEMPLATE_ID) "
                + "SELECT UUID(), ?, ?, ?, CONSENSUS_LINK_CASE_EVENT_ID, "
                + "EVENT_DESCRIPTION, EVENT_TYPE_TEMPLATE_ID, HARM_CATEGORY_TEMPLATE_ID, "
                + "PRESENT_ON_ADMISSION, TRIGGER_TEMPLATE_ID "
                + "FROM GTT_RECORD_CASE_EVENT GRCE "
                + "WHERE GRCE.RECORD_CASE_EVENT_ID IN ("
                + "SELECT GIM.SELECTED_CASE_EVENT_ID "
                + "FROM GTT_IRR_MATCH GIM "
                + "WHERE GIM.CONSENSUS_CASE_ID = ? "
                + "AND GIM.IS_DELETED = 0)")) {
      int i = 1;
      insertRCET.setString(i++, newCaseId);
      insertRCET.setString(i++, TQMCConstants.GTT_CASE_TYPE_PHYSICIAN);
      insertRCET.setString(i++, formattedDateTime);
      insertRCET.setString(i++, consensusId);
      insertRCET.execute();
    }

    // Adding the new case to the workflow table
    GttWorkflow newCase = new GttWorkflow();
    newCase.setCaseId(newCaseId);
    newCase.setGuid(UUID.randomUUID().toString());
    newCase.setCaseType(TQMCConstants.GTT_CASE_TYPE_PHYSICIAN);
    newCase.setSendingStage(TQMCConstants.GTT_STAGE_REASSIGNED);
    newCase.setRecipientStage(TQMCConstants.GTT_STAGE_PHYSICIAN_REVIEW);
    newCase.setStepStatus(TQMCConstants.CASE_STEP_STATUS_NOT_STARTED);
    newCase.setSendingUserId(management_lead_id);
    newCase.setRecipientUserId(userId);
    newCase.setSmssTimestamp(ConversionUtils.getUTCFromLocalNow());
    newCase.setIsLatest(true);
    TQMCHelper.createNewGttWorkflowEntry(con, newCase);
  }

  public static void reassignGttPhysicianCase(
      Connection con,
      String caseId,
      String consensusId,
      String management_lead_id,
      String newUserId)
      throws SQLException {
    LocalDateTime now = ConversionUtils.getUTCFromLocalNow();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    String formattedDateTime = now.format(formatter);

    // Update the userId in Physician Case by caseId
    try (PreparedStatement insertQuery =
        con.prepareStatement(
            "UPDATE GTT_PHYSICIAN_CASE SET PHYSICIAN_USER_ID = ?, UPDATED_AT = ? WHERE CASE_ID = ?"); ) {
      int i = 1;
      insertQuery.setString(i++, newUserId);
      insertQuery.setString(i++, formattedDateTime);
      insertQuery.setString(i++, caseId);
      insertQuery.execute();
    }

    //    Reset all values for a case
    resetCaseValues(con, caseId, TQMCConstants.GTT_CASE_TYPE_PHYSICIAN);

    // Adding the case back to the GTT Record Case Event Table with the consensus data

    try (PreparedStatement insertRCET =
        con.prepareStatement(
            "INSERT INTO GTT_RECORD_CASE_EVENT ("
                + "RECORD_CASE_EVENT_ID, CASE_ID, CASE_TYPE, UPDATED_AT, "
                + "CONSENSUS_LINK_CASE_EVENT_ID, EVENT_DESCRIPTION, "
                + "EVENT_TYPE_TEMPLATE_ID, HARM_CATEGORY_TEMPLATE_ID, "
                + "PRESENT_ON_ADMISSION, TRIGGER_TEMPLATE_ID) "
                + "SELECT UUID(), ?, ?, ?, CONSENSUS_LINK_CASE_EVENT_ID, "
                + "EVENT_DESCRIPTION, EVENT_TYPE_TEMPLATE_ID, HARM_CATEGORY_TEMPLATE_ID, "
                + "PRESENT_ON_ADMISSION, TRIGGER_TEMPLATE_ID "
                + "FROM GTT_RECORD_CASE_EVENT GRCE "
                + "WHERE GRCE.RECORD_CASE_EVENT_ID IN ("
                + "SELECT GIM.SELECTED_CASE_EVENT_ID "
                + "FROM GTT_IRR_MATCH GIM "
                + "WHERE GIM.CONSENSUS_CASE_ID = ? "
                + "AND GIM.IS_DELETED = 0)")) {
      int i = 1;
      insertRCET.setString(i++, caseId);
      insertRCET.setString(i++, TQMCConstants.GTT_CASE_TYPE_PHYSICIAN);
      insertRCET.setString(i++, formattedDateTime);
      insertRCET.setString(i++, consensusId);
      insertRCET.execute();
    }
  }

  private static void resetCaseValues(Connection con, String caseId, String caseType)
      throws SQLException {
    // Update the case in case time table by case_id, need to null out values for
    // case_time

    try (PreparedStatement resetCaseTime =
        con.prepareStatement(
            "UPDATE GTT_CASE_TIME SET CUMULATIVE_TIME = NULL, START_TIME = NULL, STOP_TIME = NULL WHERE CASE_ID = ? AND CASE_TYPE = ?")) {
      int i = 1;
      resetCaseTime.setString(i++, caseId);
      resetCaseTime.setString(i++, caseType);
      resetCaseTime.execute();
    }

    //	    Update RECORD_CASE_EVENT table to remove all record_case_events created from before, then
    // insert the default set again

    try (PreparedStatement deleteFromRCET =
        con.prepareStatement(
            "DELETE FROM GTT_RECORD_CASE_EVENT WHERE CASE_ID = ? AND CASE_TYPE = ?")) {
      int i = 1;
      deleteFromRCET.setString(i++, caseId);
      deleteFromRCET.setString(i++, caseType);
      int rowsUpdated = deleteFromRCET.executeUpdate();
      LOGGER.warn(rowsUpdated + " rows deleted from GTT_RECORD_CASE_EVENT");
    }

    //    Delete all case notes

    try (PreparedStatement deleteFromRCET =
        con.prepareStatement("DELETE FROM GTT_CASE_NOTE WHERE CASE_ID = ? AND CASE_TYPE = ?")) {
      int i = 1;
      deleteFromRCET.setString(i++, caseId);
      deleteFromRCET.setString(i++, caseType);
      int rowsUpdated = deleteFromRCET.executeUpdate();
      LOGGER.warn(rowsUpdated + " rows deleted from GTT_RECORD_CASE_EVENT");
    }
  }

  public static boolean isAssignedOnConsensusCase(Connection con, String caseId, String userId)
      throws SQLException {
    try (PreparedStatement checkUserQuery =
        con.prepareStatement(
            "SELECT gac1.USER_ID AS USER_ID_1, gac2.USER_ID AS USER_ID_2 "
                + "FROM GTT_CONSENSUS_CASE gcc "
                + "INNER JOIN GTT_ABSTRACTOR_CASE gac1 ON gcc.ABS_1_CASE_ID = gac1.CASE_ID "
                + "INNER JOIN GTT_ABSTRACTOR_CASE gac2 ON gcc.ABS_2_CASE_ID = gac2.CASE_ID "
                + "WHERE gcc.ABS_1_CASE_ID = ? OR gcc.ABS_2_CASE_ID = ?")) {
      checkUserQuery.setString(1, caseId);
      checkUserQuery.setString(2, caseId);
      ResultSet rs = checkUserQuery.executeQuery();
      if (rs.next()) {
        String userId1 = rs.getString("USER_ID_1");
        String userId2 = rs.getString("USER_ID_2");
        if (userId1 == null && userId2 != null) {
          return userId2.equals(userId);
        }
        if (userId1 != null && userId2 == null) {
          return userId1.equals(userId);
        }
        if (userId1 != null && userId2 != null) {
          return userId1.equals(userId) || userId2.equals(userId);
        }
      }
    }
    return false;
  }

  /** @deprecated */
  public static void createGttAbstractorCase(
      Connection con, String oldCaseId, String management_lead_id, String userId)
      throws SQLException {

    String newCaseId = null;
    String recordId = getGttRecordId(con, oldCaseId, TQMCConstants.GTT_CASE_TYPE_ABSTRACTOR);
    LocalDateTime now = ConversionUtils.getUTCFromLocalNow();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    String formattedDateTime = now.format(formatter);

    // Set up the case data to insert the new Abstractor Case
    try (PreparedStatement insertQuery =
        con.prepareStatement(
            "INSERT INTO GTT_ABSTRACTOR_CASE "
                + "(CASE_ID, CREATED_AT, RECORD_ID, USER_ID) "
                + "VALUES(?, ?, ?, ?)")) {
      // Generating a new unique case id
      newCaseId =
          getDisplayId(
              TQMCConstants.GTT + "-ABS",
              getNextId(con, "GTT_ABSTRACTOR_CASE"),
              TQMCConstants.PADDING_LENGTH);

      // Setting up the values of the new case's fields
      int i = 1;
      insertQuery.setString(i++, newCaseId);
      insertQuery.setString(i++, formattedDateTime);
      insertQuery.setString(i++, recordId);
      insertQuery.setString(i++, userId);
      insertQuery.execute();
    }

    // Inserting the new Abstraction case into case time table
    try (PreparedStatement insertQuery =
        con.prepareStatement(
            "INSERT INTO GTT_CASE_TIME (CASE_ID, CASE_TIME_ID, CASE_TYPE) " + "VALUES (?, ?, ?)")) {
      int i = 1;
      insertQuery.setString(i++, newCaseId);
      insertQuery.setString(i++, UUID.randomUUID().toString());
      insertQuery.setString(i++, TQMCConstants.GTT_CASE_TYPE_ABSTRACTOR);
      insertQuery.execute();
    }

    // Adding the new case to the workflow table
    GttWorkflow newCase = new GttWorkflow();
    newCase.setCaseId(newCaseId);
    newCase.setGuid(UUID.randomUUID().toString());
    newCase.setCaseType(TQMCConstants.GTT_CASE_TYPE_ABSTRACTOR);
    newCase.setSendingStage(TQMCConstants.GTT_STAGE_REASSIGNED);
    newCase.setRecipientStage(TQMCConstants.GTT_STAGE_ABSTRACTION);
    newCase.setStepStatus(TQMCConstants.CASE_STEP_STATUS_NOT_STARTED);
    newCase.setSendingUserId(management_lead_id);
    newCase.setRecipientUserId(userId);
    newCase.setSmssTimestamp(now);
    newCase.setIsLatest(true);
    TQMCHelper.createNewGttWorkflowEntry(con, newCase);

    String consensusCaseId = null;
    String abs1CaseId = null;
    String updateSql = null;

    // Query the consensus case table to get the
    // consensus case_id, abs_1_case_id
    // where the old Abstractor case id matches either abs_1_case_id or
    // abs_2_case_id
    try (PreparedStatement consensusInfo =
        con.prepareStatement(
            "SELECT CASE_ID, ABS_1_CASE_ID FROM GTT_CONSENSUS_CASE gcc "
                + "WHERE (gcc.ABS_1_CASE_ID = ? OR gcc.ABS_2_CASE_ID = ?)"); ) {

      consensusInfo.setString(1, oldCaseId);
      consensusInfo.setString(2, oldCaseId);
      ResultSet rs = consensusInfo.executeQuery();
      if (rs.next()) {
        consensusCaseId = rs.getString("CASE_ID");
        abs1CaseId = rs.getString("ABS_1_CASE_ID");
      }
    }

    // Checking to see if ABS_1_CASE_ID is equal to the old Abstractor case id
    // so we know which column needs to be updated with the new
    // Abstractor case id
    if (abs1CaseId.equals(oldCaseId)) {
      updateSql = "UPDATE GTT_CONSENSUS_CASE SET ABS_1_CASE_ID = ? WHERE CASE_ID = ?";
    } else {
      updateSql = "UPDATE GTT_CONSENSUS_CASE SET ABS_2_CASE_ID = ? WHERE CASE_ID = ?";
    }

    // Updating the consensus case with the new
    // Abstractor case id
    try (PreparedStatement updateQuery = con.prepareStatement(updateSql)) {
      updateQuery.setString(1, newCaseId);
      updateQuery.setString(2, consensusCaseId);
      updateQuery.execute();
    }
    // Updating the status of the consensus case
    GttWorkflow cwf = getLatestGttWorkflow(con, consensusCaseId);
    cwf.setStepStatus(TQMCConstants.CASE_STEP_STATUS_NOT_STARTED);
    cwf.setSmssTimestamp(now);
    cwf.setIsLatest(true);
    cwf.setGuid(UUID.randomUUID().toString());
    createNewGttWorkflowEntry(con, cwf);
  }

  public static void reassignGttAbstractorCase(
      Connection con, String caseId, String management_lead_id, String newUserId)
      throws SQLException {

    LocalDateTime now = ConversionUtils.getUTCFromLocalNow();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    String formattedDateTime = now.format(formatter);

    // UPDATE Abstractor case by case_id
    try (PreparedStatement insertQuery =
        con.prepareStatement(
            "UPDATE GTT_ABSTRACTOR_CASE SET USER_ID = ?, UPDATED_AT = ? WHERE CASE_ID = ?")) {

      int i = 1;
      insertQuery.setString(i++, newUserId);
      insertQuery.setString(i++, formattedDateTime);
      insertQuery.setString(i++, caseId);
      insertQuery.execute();
    }

    //    Reset all values for abstractor
    resetCaseValues(con, caseId, TQMCConstants.GTT_CASE_TYPE_ABSTRACTOR);
  }

  public static DpWorkflow getLatestDpWorkflow(Connection con, String caseId) throws SQLException {
    DpWorkflow latestDpWorkflow = new DpWorkflow();
    try (PreparedStatement ps =
        con.prepareStatement(
            "Select wf.*, reopening_reason FROM (SELECT guid, case_id, is_latest, recipient_user_id, sending_user_id, step_status, workflow_notes, smss_timestamp FROM Dp_Workflow WHERE case_id = ? AND is_latest = 1) as wf LEFT OUTER JOIN Dp_case c ON c.case_id = wf.case_id")) {
      ps.setString(1, caseId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          latestDpWorkflow.setGuid(rs.getString("guid"));
          latestDpWorkflow.setCaseId(rs.getString("case_id"));
          latestDpWorkflow.setIsLatest(rs.getInt("is_latest") == 1);
          latestDpWorkflow.setRecipientUserId(rs.getString("recipient_user_id"));
          latestDpWorkflow.setSendingUserId(rs.getString("sending_user_id"));
          latestDpWorkflow.setStepStatus(rs.getString("step_status"));
          latestDpWorkflow.setWorkflowNotes(rs.getString("workflow_notes"));
          latestDpWorkflow.setSmssTimestamp(
              ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("smss_timestamp")));
          latestDpWorkflow.setReopenReason(rs.getString("reopening_reason"));
        }
      }
    }

    if (latestDpWorkflow.getGuid() == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND);
    }

    return latestDpWorkflow;
  }

  public static McscWorkflow getLatestMcscWorkflow(Connection con, String caseId)
      throws SQLException {
    McscWorkflow latestMcscWorkflow = new McscWorkflow();
    try (PreparedStatement ps =
        con.prepareStatement(
            "Select wf.*, reopening_reason FROM (SELECT guid, case_id, is_latest, recipient_user_id, sending_user_id, step_status, workflow_notes, smss_timestamp FROM Mcsc_Workflow WHERE case_id = ? AND is_latest = 1) as wf LEFT OUTER JOIN mcsc_case c ON c.case_id = wf.case_id")) {
      ps.setString(1, caseId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          latestMcscWorkflow.setGuid(rs.getString("guid"));
          latestMcscWorkflow.setCaseId(rs.getString("case_id"));
          latestMcscWorkflow.setIsLatest(rs.getInt("is_latest") == 1);
          latestMcscWorkflow.setRecipientUserId(rs.getString("recipient_user_id"));
          latestMcscWorkflow.setSendingUserId(rs.getString("sending_user_id"));
          latestMcscWorkflow.setStepStatus(rs.getString("step_status"));
          latestMcscWorkflow.setWorkflowNotes(rs.getString("workflow_notes"));
          latestMcscWorkflow.setSmssTimestamp(
              ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("smss_timestamp")));
          latestMcscWorkflow.setReopenReason(rs.getString("reopening_reason"));
        }
      }
    }

    if (latestMcscWorkflow.getGuid() == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND);
    }

    return latestMcscWorkflow;
  }

  public static String getMnCaseIdForRecord(Connection con, String recordId) throws SQLException {
    String caseId = null;
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT case_id FROM MN_CASE WHERE record_id = ? AND deleted_at IS NULL and submission_guid is NULL")) {
      ps.setString(1, recordId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          if (caseId != null) {
            throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR);
          }
          caseId = rs.getString(1);
        }
      }
    }
    return caseId;
  }

  public static String appendSubmissionSuffix(String caseId, String guid) {
    return caseId + "-" + guid;
  }

  public static void updateMcscWorkflow(Connection con, McscWorkflow mcscWorkflow)
      throws SQLException {
    updateMcscWorkflow(con, mcscWorkflow, false);
  }

  public static void updateMcscWorkflow(
      Connection con, McscWorkflow mcscWorkflow, boolean skipIsLatestUpdate) throws SQLException {

    if (!skipIsLatestUpdate) {
      try (PreparedStatement ps =
          con.prepareStatement(
              "UPDATE "
                  + "MCSC_WORKFLOW"
                  + " SET is_latest = 0 WHERE case_id = ? AND is_latest = 1")) {
        ps.setString(1, mcscWorkflow.getCaseId());
        ps.execute();
      }
    }

    try (PreparedStatement ps =
        con.prepareStatement(
            "INSERT INTO "
                + "MCSC_WORKFLOW"
                + " (case_id, guid, is_latest, recipient_user_id, sending_user_id, smss_timestamp, step_status, workflow_notes) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, mcscWorkflow.getCaseId());
      ps.setString(parameterIndex++, mcscWorkflow.getGuid());
      ps.setBoolean(parameterIndex++, mcscWorkflow.getIsLatest());
      ps.setString(parameterIndex++, mcscWorkflow.getRecipientUserId());
      ps.setString(parameterIndex++, mcscWorkflow.getSendingUserId());
      ps.setObject(parameterIndex++, mcscWorkflow.getSmssTimestamp());
      ps.setString(parameterIndex++, mcscWorkflow.getStepStatus());
      ps.setString(parameterIndex++, mcscWorkflow.getWorkflowNotes());
      ps.execute();
    }
  }

  public static void updateDpWorkflow(Connection con, DpWorkflow dpWorkflow) throws SQLException {
    updateDpWorkflow(con, dpWorkflow, false);
  }

  public static void updateDpWorkflow(
      Connection con, DpWorkflow dpWorkflow, boolean skipIsLatestUpdate) throws SQLException {

    if (!skipIsLatestUpdate) {
      try (PreparedStatement ps =
          con.prepareStatement(
              "UPDATE DP_WORKFLOW SET is_latest = 0 WHERE case_id = ? AND is_latest = 1")) {
        ps.setString(1, dpWorkflow.getCaseId());
        ps.execute();
      }
    }

    try (PreparedStatement ps =
        con.prepareStatement(
            "INSERT INTO DP_WORKFLOW (case_id, guid, is_latest, recipient_user_id, sending_user_id, smss_timestamp, step_status, workflow_notes) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, dpWorkflow.getCaseId());
      ps.setString(parameterIndex++, dpWorkflow.getGuid());
      ps.setBoolean(parameterIndex++, dpWorkflow.getIsLatest());
      ps.setString(parameterIndex++, dpWorkflow.getRecipientUserId());
      ps.setString(parameterIndex++, dpWorkflow.getSendingUserId());
      ps.setObject(parameterIndex++, dpWorkflow.getSmssTimestamp());
      ps.setString(parameterIndex++, dpWorkflow.getStepStatus());
      ps.setString(parameterIndex++, dpWorkflow.getWorkflowNotes());
      ps.execute();
    }
  }

  public static String generateWorkflowNote(String oldStep, String newStep, String userId) {
    return userId + ": " + oldStep + " --> " + newStep;
  }

  public static String generateReopenWorkflowNote(
      String activeUserId, String assignedUserId, String reason) {
    return activeUserId + " reopened case for " + assignedUserId + ". Reason: " + reason;
  }

  public static final Map<String, Set<String>> generateRoleProductMap() {
    Map<String, Set<String>> map = new HashMap<>();
    map.put(
        TQMCConstants.ABSTRACTOR,
        new HashSet<>(Arrays.asList(TQMCConstants.GTT, TQMCConstants.ORYX)));
    map.put(
        TQMCConstants.MANAGEMENT_LEAD,
        new HashSet<>(
            Arrays.asList(
                TQMCConstants.GTT,
                TQMCConstants.MN,
                TQMCConstants.SOC,
                TQMCConstants.ORYX,
                TQMCConstants.DP,
                TQMCConstants.MCSC)));
    map.put(TQMCConstants.PHYSICIAN, new HashSet<>(Arrays.asList(TQMCConstants.GTT)));
    map.put(
        TQMCConstants.CONTRACTING_LEAD,
        new HashSet<>(
            Arrays.asList(
                TQMCConstants.MN, TQMCConstants.SOC, TQMCConstants.DP, TQMCConstants.MCSC)));
    map.put(
        TQMCConstants.PEER_REVIEWER,
        new HashSet<>(Arrays.asList(TQMCConstants.MN, TQMCConstants.SOC)));
    map.put(
        TQMCConstants.ADMIN,
        new HashSet<>(
            Arrays.asList(
                TQMCConstants.GTT,
                TQMCConstants.MN,
                TQMCConstants.SOC,
                TQMCConstants.ORYX,
                TQMCConstants.DP,
                TQMCConstants.MCSC)));
    map.put(TQMCConstants.NURSE_REVIEWER, new HashSet<>(Arrays.asList(TQMCConstants.SOC)));
    map.put(
        TQMCConstants.QUALITY_REVIEWER,
        new HashSet<>(Arrays.asList(TQMCConstants.DP, TQMCConstants.MCSC)));
    return map;
  }

  public static boolean validateRoleProduct(String role, Set<String> products) {
    if (!TQMCConstants.VALID_ROLE_PRODUCT_MAP.keySet().contains(role)) {
      return false;
    }
    for (String product : products) {
      if (!TQMCConstants.VALID_ROLE_PRODUCT_MAP.get(role).contains(product)) {
        return false;
      }
    }
    return true;
  }

  public static Set<String> getValidSpecialtyIds(Connection con) throws SQLException {
    Set<String> validSpecialties = new HashSet<>();
    String query = "SELECT specialty_id from TQMC_SPECIALTY";
    try (PreparedStatement ps = con.prepareStatement(query)) {
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          validSpecialties.add(rs.getString(1));
        }
      }
    }
    return validSpecialties;
  }

  public static void updateGttRecordCaseEventDescriptions(
      Connection con,
      Map<String, String> eventDescriptionByRecordCaseEventId,
      LocalDateTime updatedAt)
      throws SQLException {
    if (eventDescriptionByRecordCaseEventId.isEmpty()) {
      return;
    }

    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE GTT_RECORD_CASE_EVENT SET EVENT_DESCRIPTION = ?, UPDATED_AT = ? WHERE RECORD_CASE_EVENT_ID = ? "
                + "AND (COALESCE(EVENT_DESCRIPTION, '') <> COALESCE(?, ''))")) {
      for (Entry<String, String> entry : eventDescriptionByRecordCaseEventId.entrySet()) {
        int parameterIndex = 1;
        ps.setString(parameterIndex++, entry.getValue());
        ps.setObject(parameterIndex++, updatedAt);
        ps.setString(parameterIndex++, entry.getKey());
        ps.setString(parameterIndex++, entry.getValue());
        ps.addBatch();
      }
      ps.executeBatch();
    }
  }

  // need to update as more products get added
  public static Set<CaseDetails> getProductCaseAssignments(Connection con, TQMCUserInfo u)
      throws SQLException {

    Set<CaseDetails> caseDetails = new HashSet<>();

    if (u.getProducts().isEmpty()) {
      return caseDetails;
    }

    String QUERY = "SELECT case_id, specialty_id, product, case_type, has_files FROM (";

    Set<String> products = u.getProducts();
    int i = 0;
    for (String product : products) {
      String queryTable = addProductToCaseQuery(product.toLowerCase());
      if (queryTable.length() > 0) {
        QUERY += queryTable;
        QUERY += " UNION ALL ";
        i++;
      }
    }

    QUERY = QUERY.substring(0, QUERY.length() - 10);

    QUERY += ") AS combined_results";

    try (PreparedStatement ps = con.prepareStatement(QUERY)) {
      int parameterIndex = 1;
      while (parameterIndex <= i) {
        ps.setString(parameterIndex++, u.getUserId());
      }

      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          CaseDetails d = new CaseDetails();
          d.setCaseId(rs.getString("case_id"));
          d.setSpecialtyId(rs.getString("specialty_id"));
          d.setProduct(rs.getString("product"));
          d.setCaseType(rs.getString("case_type"));
          d.setHasFiles(rs.getInt("has_files") == 1);
          d.setRecipientUserId(u.getUserId());
          caseDetails.add(d);
        }
      }
    }

    return caseDetails;
  }

  private static String addProductToCaseQuery(String product) {
    switch (product) {
      case "mn":
        return "SELECT mnc.case_id, mnc.specialty_id, 'mn' AS product, null AS case_type, 1 AS has_files "
            + "FROM MN_WORKFLOW mnw LEFT OUTER JOIN MN_CASE mnc ON mnc.case_id = mnw.case_id WHERE mnw.is_latest = '1' "
            + "AND (mnw.STEP_STATUS = 'not_started' OR mnw.STEP_STATUS = 'in_progress') "
            + "AND mnw.recipient_user_id = ?";
      case "gtt":
        return "SELECT gwf.case_id, NULL AS specialty_id, 'gtt' AS product, gwf.case_type, 0 AS has_files "
            + "FROM GTT_WORKFLOW gwf WHERE gwf.is_latest = '1' "
            + "AND (gwf.STEP_STATUS = 'not_started' OR gwf.STEP_STATUS = 'in_progress') "
            + "AND gwf.recipient_user_id = ?";
      case "soc":
        return "SELECT sc.case_id, sc.specialty_id, 'soc' AS product, null AS case_type, 1 AS has_files "
            + "FROM SOC_WORKFLOW swf LEFT OUTER JOIN SOC_CASE sc ON sc.case_id = swf.case_id WHERE swf.is_latest = '1' "
            + "AND (swf.STEP_STATUS = 'not_started' OR swf.STEP_STATUS = 'in_progress') "
            + "AND swf.recipient_user_id = ?";
      case "dp":
        return "SELECT dwf.case_id, NULL AS specialty_id, 'dp' AS product, null AS case_type, 1 AS has_files "
            + "FROM DP_WORKFLOW dwf WHERE dwf.is_latest = '1' "
            + "AND (dwf.STEP_STATUS = 'not_started' OR dwf.STEP_STATUS = 'in_progress') "
            + "AND dwf.recipient_user_id = ?";
      case "mcsc":
        return "SELECT mwf.case_id, NULL AS specialty_id, 'mcsc' AS product, null AS case_type, 1 AS has_files "
            + "FROM MCSC_WORKFLOW mwf WHERE mwf.is_latest = '1' "
            + "AND (mwf.STEP_STATUS = 'not_started' OR mwf.STEP_STATUS = 'in_progress') "
            + "AND mwf.recipient_user_id = ?";
      default:
        String message = "Workflow table for " + product + " not found";
        LOGGER.warn(message);
        return "";
    }
  }

  public static Pair<String, String> getPeerSpecialtyFromSocCaseID(Connection con, String caseId)
      throws SQLException {
    String query;
    Pair<String, String> output;
    query =
        "SELECT ts.SPECIALTY_NAME, ts.SUBSPECIALTY_NAME\r\n"
            + "FROM SOC_CASE sc\r\n"
            + "JOIN TQMC_USER_SPECIALTY tus ON sc.USER_ID = tus.USER_ID\r\n"
            + "JOIN TQMC_SPECIALTY ts ON tus.SPECIALTY_ID = ts.SPECIALTY_ID\r\n"
            + "WHERE sc.CASE_ID= ?";

    try (PreparedStatement ps = con.prepareStatement(query)) {
      ps.setString(1, caseId);
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        output =
            new Pair<String, String>(
                rs.getString("SPECIALTY_NAME"), rs.getString("SUBSPECIALTY_NAME"));
      } else {
        throw new TQMCException(ErrorCode.NOT_FOUND, "User does not have a specialty");
      }
    }

    return output;
  }

  public static Pair<String, String> getSpecialtyFromCaseId(
      Connection con, ProductTables product, String caseId) throws SQLException {
    String caseTable = product.getCaseTable();
    String query;
    Pair<String, String> output;
    switch (product) {
      case SOC:
      case MN:
        query =
            "SELECT TS.SPECIALTY_NAME , TS.subspecialty_name FROM TQMC_SPECIALTY ts\r\n"
                + "INNER JOIN "
                + caseTable
                + " c ON C.SPECIALTY_ID = TS.SPECIALTY_ID\r\n"
                + "WHERE c.CASE_ID = ?";
        break;
      default:
        throw new TQMCException(ErrorCode.BAD_REQUEST, "Specialty only supported in SOC and MN");
    }
    try (PreparedStatement ps = con.prepareStatement(query)) {
      ps.setString(1, caseId);
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        output =
            new Pair<String, String>(
                rs.getString("SPECIALTY_NAME"), rs.getString("SUBSPECIALTY_NAME"));
      } else {
        throw new TQMCException(ErrorCode.NOT_FOUND);
      }
    }
    return output;
  }

  public static boolean canStartGttCase(Connection con, GttWorkflow wf) throws SQLException {

    if (TQMCConstants.GTT_CASE_TYPE_ABSTRACTOR.equals(wf.getCaseType())) {
      return true;
    }

    if (TQMCConstants.GTT_CASE_TYPE_CONSENSUS.equals(wf.getCaseType())) {
      try (PreparedStatement ps =
          con.prepareStatement(
              "SELECT w.step_status FROM gtt_consensus_case s "
                  + "LEFT OUTER JOIN gtt_workflow w "
                  + "ON (s.abs_1_case_id = w.case_id OR s.abs_2_case_id = w.case_id) "
                  + "WHERE s.case_id = ? AND w.is_latest = '1'")) {
        ps.setString(1, wf.getCaseId());
        if (ps.execute()) {
          ResultSet rs = ps.getResultSet();
          while (rs.next()) {
            if (!TQMCConstants.CASE_STEP_STATUS_COMPLETED.equals(rs.getString(1))) {
              return false;
            }
          }
        }
      }
    } else if (TQMCConstants.GTT_CASE_TYPE_PHYSICIAN.equals(wf.getCaseType())) {
      try (PreparedStatement ps =
          con.prepareStatement(
              "SELECT w.step_status FROM gtt_physician_case s "
                  + "LEFT OUTER JOIN gtt_workflow w ON s.consensus_case_id = w.case_id "
                  + "WHERE s.case_id = ? AND w.is_latest = '1'")) {
        ps.setString(1, wf.getCaseId());
        if (ps.execute()) {
          ResultSet rs = ps.getResultSet();
          if (rs.next()) {
            if (!TQMCConstants.CASE_STEP_STATUS_COMPLETED.equals(rs.getString(1))) {
              return false;
            }
          }
        }
      }
    } else {
      return false;
    }
    return true;
  }

  public static final Map<String, Map<String, Integer>> generateFormOrderComparatorMap() {
    Map<String, Map<String, Integer>> formMap = new HashMap<>();

    Map<String, Integer> triggerMap = new HashMap<>();
    Map<String, Integer> adverseMap = new HashMap<>();

    triggerMap.put("None", 1);
    triggerMap.put("Cares", 2);
    triggerMap.put("Medication", 3);
    triggerMap.put("Surgical", 4);
    triggerMap.put("Intensive Care", 5);
    triggerMap.put("Perinatal", 6);
    triggerMap.put("Emergency Department", 7);

    adverseMap.put("Procedure/Surgery", 1);
    adverseMap.put("Infection", 2);
    adverseMap.put("Medication", 3);
    adverseMap.put("Other", 4);

    formMap.put("triggers", triggerMap);
    formMap.put("adverse_event_types", adverseMap);

    return formMap;
  }

  public static Set<String> getAssignedCases(Connection con, String recordId, ProductTables product)
      throws SQLException {

    String caseTable = product.getCaseTable();
    String workflowTable = product.getWorkflowTable();

    String defaultQuery =
        "SELECT c.CASE_ID FROM "
            + caseTable
            + " c INNER JOIN "
            + workflowTable
            + " w ON c.CASE_ID = w.CASE_ID"
            + " WHERE c.RECORD_ID = ? AND w.is_latest = 1 AND w.STEP_STATUS IN ('not_started', 'in_progress', 'completed')";

    Set<String> assignedCPEIDs = new HashSet<>();

    try (PreparedStatement ps = con.prepareStatement(defaultQuery)) {

      ps.setString(1, recordId);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        assignedCPEIDs.add(rs.getString("CASE_ID"));
      }
    }

    if (product.equals(ProductTables.SOC)) {
      try (PreparedStatement ps =
          con.prepareStatement(
              "SELECT snr.NURSE_REVIEW_ID FROM SOC_NURSE_REVIEW snr INNER JOIN SOC_WORKFLOW sw ON sw.CASE_ID = snr.NURSE_REVIEW_ID WHERE RECORD_ID = ? AND submission_guid IS NULL AND sw.IS_LATEST = 1 AND sw.STEP_STATUS IN ('not_started', 'in_progress', 'completed')")) {
        ps.setString(1, recordId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
          assignedCPEIDs.add(rs.getString("NURSE_REVIEW_ID"));
        }
      }
    }

    return assignedCPEIDs;
  }

  public static boolean hasAssignedCases(Connection con, String recordId, ProductTables product)
      throws SQLException {
    Set<String> assignedCases = getAssignedCases(con, recordId, product);
    return assignedCases.size() > 0;
  }

  public static int getNextId(Connection con, String table) throws SQLException {
    int nextId = -1;
    try (PreparedStatement ps =
        con.prepareStatement("SELECT NEXT VALUE FOR " + table + "_SEQUENCE")) {
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          nextId = rs.getInt(1);
        }
      }
    }
    return nextId;
  }

  public static String getDisplayId(String prefix, int id, int padLength) {
    return (prefix + "-" + String.format("%0" + padLength + "d", id)).toUpperCase();
  }

  public static void onEndPage(PdfWriter writer, Document document, String projectId) {
    try {
      PdfContentByte cb = writer.getDirectContent();
      String projectAssetDirectory = AssetUtility.getProjectAssetsFolder(projectId);

      // Construct paths based on your knowledge of the subdirectories
      Path logo1Path = Paths.get(projectAssetDirectory, "pdf_resources/DHA-Logo.png").normalize();
      Image logo1 = Image.getInstance(logo1Path.toString());

      Path logo2Path = Paths.get(projectAssetDirectory, "pdf_resources/TQMC.png").normalize();
      Image logo2 = Image.getInstance(logo2Path.toString());

      // Add logos to the header
      logo1.scaleToFit(100, 50); // Adjust the size as needed
      logo2.scaleToFit(131, 50); // Adjust the size as needed
      float headerY = document.top() - ((document.topMargin() + logo1.getScaledHeight()) / 2);

      // Add logo1 to the right side of the header
      logo1.setAbsolutePosition(document.right() - logo1.getScaledWidth(), headerY);
      cb.addImage(logo1);

      // Add logo2 to the left side of the header
      logo2.setAbsolutePosition(document.left(), headerY);
      cb.addImage(logo2);

      // Add a blank paragraph or spacer at the top of each page to create space below
      // the logos
      if (writer.getPageNumber() > 1) {
        ColumnText.showTextAligned(
            cb,
            Element.ALIGN_LEFT,
            new Phrase("\n\n\n\n\n\n\n\n"),
            document.left(),
            document.top() - logo1.getScaledHeight() - 20,
            0);
      }
    } catch (IOException | DocumentException e) {
      e.printStackTrace();
    }
  }

  public static void addJustifiedListItem(
      com.lowagie.text.List list, String text, Font timesNewRomanFont) {
    ListItem item = new ListItem(new Phrase(text, timesNewRomanFont));
    item.setAlignment(Element.ALIGN_JUSTIFIED);
    list.add(item);
  }

  public static void generateAttestationPdf(
      Connection con, String projectId, String caseId, File pdf, String attestedQuery) {
    try (PreparedStatement query = con.prepareStatement(attestedQuery);
        Document document = new Document(); ) {
      query.setString(1, caseId);
      ResultSet rs = query.executeQuery();
      if (rs.next()) {
        String attested;
        String attested_date;
        if (rs.getString(TQMCConstants.ATTESTATION_SIGNATURE) != null
            && rs.getString(TQMCConstants.ATTESTED_AT) != null) {
          attested = rs.getString(TQMCConstants.ATTESTATION_SIGNATURE);
          attested_date =
              ConversionUtils.getLocalDateStringFromDate(rs.getDate(TQMCConstants.ATTESTED_AT));
        } else {
          attested = "false";
          attested_date = "N/A";
        }

        String specialty = rs.getString("SPECIALTY_NAME");
        String subspecialty = rs.getString("SUBSPECIALTY_NAME");

        FileOutputStream pdf_fos = new FileOutputStream(pdf);
        PdfWriter writer = PdfWriter.getInstance(document, pdf_fos);
        document.open();

        writer.setPageEvent(
            new PdfPageEventHelper() {
              @Override
              public void onEndPage(PdfWriter writer, Document document) {
                TQMCHelper.onEndPage(writer, document, projectId);
              }
            });

        // Set font to Times New Roman 12
        String projectAssetDirectory = AssetUtility.getProjectAssetsFolder(projectId);
        Path timesNewRomanPath =
            Paths.get(projectAssetDirectory, "pdf_resources/timesNewRoman.ttf").normalize();
        BaseFont timesNewRomanBase =
            BaseFont.createFont(
                timesNewRomanPath.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font timesNewRomanFont = new Font(timesNewRomanBase, 11, Font.NORMAL);

        // Add space between sections
        document.add(new Paragraph("\n\n\n"));

        document.add(new Paragraph("MEDICAL PROFESSIONAL REVIEWER ATTESTATION", timesNewRomanFont));
        document.add(new Paragraph("\n"));
        document.add(
            new Paragraph(
                "(If you cannot attest to all of the following, please contact Deloitte immediately)",
                timesNewRomanFont));
        document.add(new Paragraph("\n"));
        document.add(new Paragraph("I attest that I:", timesNewRomanFont));

        // Create a list for bullet points with solid bullets
        com.lowagie.text.List bulletPoints =
            new com.lowagie.text.List(com.lowagie.text.List.UNORDERED);
        bulletPoints.setListSymbol(
            new Chunk("\u2022    ", timesNewRomanFont)); // Unicode for solid bullet
        bulletPoints.setIndentationLeft(20); // Indentation of 20 for solid bullets

        // Add list items with justified alignment using the utility method
        TQMCHelper.addJustifiedListItem(
            bulletPoints,
            "Am currently licensed and actively engaged in clinical practice at least 20 hours/week as a monthly average, to accommodate fluctuating clinical schedules;",
            timesNewRomanFont);
        TQMCHelper.addJustifiedListItem(
            bulletPoints, "Practice in the same clinical area being reviewed;", timesNewRomanFont);
        TQMCHelper.addJustifiedListItem(
            bulletPoints, "Am a certified/licensed " + specialty + ";", timesNewRomanFont);
        TQMCHelper.addJustifiedListItem(
            bulletPoints,
            "Have a sub-specialty (Secondary) Certification in " + subspecialty + ";",
            timesNewRomanFont);
        TQMCHelper.addJustifiedListItem(
            bulletPoints,
            "Am knowledgeable in the treatment of the beneficiary's condition, and familiar with guidelines and protocols in the area of treatment under review;",
            timesNewRomanFont);
        TQMCHelper.addJustifiedListItem(
            bulletPoints,
            "Do not have a conflict of interest with the assigned review. I understand that a conflict of interest exists if any of the following apply:",
            timesNewRomanFont);

        // Create a nested list for sub-items with open circle bullets
        com.lowagie.text.List nestedList =
            new com.lowagie.text.List(com.lowagie.text.List.UNORDERED);
        nestedList.setListSymbol(
            new Chunk("\u25CB      ", timesNewRomanFont)); // Unicode for open circle
        nestedList.setIndentationLeft(25); // Indentation of 25 for open circle bullets

        // Add nested list items with justified alignment using the utility method
        TQMCHelper.addJustifiedListItem(
            nestedList,
            "I have participated in the care of the patient/member under review; are an associate or close competitor of the medical professional under review;",
            timesNewRomanFont);
        TQMCHelper.addJustifiedListItem(
            nestedList, "I am a family member of the patient under review;", timesNewRomanFont);
        TQMCHelper.addJustifiedListItem(
            nestedList,
            "I am a governing body member, officer, partner and/or 5% or more owner or managing employee of the health facility or practice where the services were performed;",
            timesNewRomanFont);
        TQMCHelper.addJustifiedListItem(
            nestedList,
            "I am a staff member in the same hospital as the medical professional under review;",
            timesNewRomanFont);
        TQMCHelper.addJustifiedListItem(
            nestedList,
            "I received financial incentives to promote products or services rendered by a particular entity;",
            timesNewRomanFont);
        TQMCHelper.addJustifiedListItem(
            nestedList,
            "Or, I was involved with the previous medical necessity review of the case under review.",
            timesNewRomanFont);

        // Add the nested list to the main list item
        bulletPoints.add(nestedList);

        // Continue adding main list items with solid bullets using the utility method
        TQMCHelper.addJustifiedListItem(
            bulletPoints,
            "Have not had a change in my standing and status in the practice of medicine since submission of information to Deloitte for credentialing, and specifically that I have not been subject to any disciplinary action by any health care institution, licensing authority, professional society, or government health care program;",
            timesNewRomanFont);
        TQMCHelper.addJustifiedListItem(
            bulletPoints,
            "Understand that, while unlikely, Defense Health Agency may ask me to serve as an expert witness for this case if the case goes to a hearing;",
            timesNewRomanFont);
        TQMCHelper.addJustifiedListItem(
            bulletPoints,
            "Have abided by all State and Federal laws regarding confidentially and disclosure of personal health information for all medical records and protected health information reviewed;",
            timesNewRomanFont);
        TQMCHelper.addJustifiedListItem(
            bulletPoints,
            "Have received and reviewed all the medical records and other clinical records listed in this Decision Report;",
            timesNewRomanFont);
        TQMCHelper.addJustifiedListItem(
            bulletPoints,
            "Have found the medical records and other information to be complete, legible, and absent of any relevant deficiency;",
            timesNewRomanFont);
        TQMCHelper.addJustifiedListItem(
            bulletPoints,
            "Have made an independent and impartial decisions based upon a thorough review of the entire case record and application of relevant medical scientific evidence.",
            timesNewRomanFont);

        document.add(bulletPoints);

        // Load the cursive font
        Path cursiveFontPath =
            Paths.get(projectAssetDirectory, "pdf_resources/segoesc.ttf").normalize();
        BaseFont cursiveFontBase =
            BaseFont.createFont(cursiveFontPath.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font cursiveFont = new Font(cursiveFontBase, 11, Font.NORMAL);

        // Add space so text doesn't overlap logo
        document.add(new Paragraph("\n\n", timesNewRomanFont));

        // Add the attestation signature in cursive
        document.add(new Paragraph("Signed: " + attested, cursiveFont));
        document.add(new Paragraph("Print Name: " + attested, timesNewRomanFont));
        document.add(new Paragraph("SYSTEM ID: " + rs.getString("RECORD_ID"), timesNewRomanFont));
        document.add(
            new Paragraph(
                "Case ID: " + rs.getString("ALIAS_RECORD_ID") + "\nDate: " + attested_date,
                timesNewRomanFont));
      }
    } catch (ExceptionConverter | IOException | DocumentException | SQLException e) {
      throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
    }
  }

  public static boolean canUpdate(Connection con, String tableName, Record record)
      throws SQLException {
    String recentUpdate = null;
    try (PreparedStatement ps =
        con.prepareStatement("SELECT UPDATED_AT FROM " + tableName + " WHERE RECORD_ID = ?")) {
      ps.setString(1, record.getRecordId());
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          recentUpdate = ConversionUtils.getLocalDateTimeStringFromTimestamp(rs.getTimestamp(1));
        }
      }
    }
    return (recentUpdate.equals(ConversionUtils.getLocalDateTimeString(record.getUpdatedAt())));
  }

  public static List<TQMCUserInfo> getAllUsers(Connection con) throws SQLException {
    List<TQMCUserInfo> result = new ArrayList<>();
    try (PreparedStatement ps =
        con.prepareStatement(
            "select user_id, first_name, last_name, role from TQMC_USER where user_id != 'system' order by last_name")) {
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          TQMCUserInfo user = new TQMCUserInfo();
          user.setUserId(rs.getString("user_id"));
          user.setFirstName(rs.getString("first_name"));
          user.setLastName(rs.getString("last_name"));
          user.setRole(rs.getString("role"));
          result.add(user);
        }
      }
    }
    return result;
  }

  public static List<GttWorkflow> getCompletedPhysicianWorkFlowsByUserDate(
      Connection con, String userId, String startDate, String endDate) throws SQLException {
    List<GttWorkflow> result = new ArrayList<>();
    String query =
        "select"
            + " case_id, smss_timestamp"
            + " from "
            + " gtt_workflow"
            + " where"
            + " SENDING_USER_ID = ?"
            + " and case_type = 'physician'"
            + " and step_status = 'completed'"
            + " and smss_timestamp >= ? "
            + " and smss_timestamp < ?";
    try (PreparedStatement ps = con.prepareStatement(query)) {
      ps.setString(1, userId);
      ps.setString(2, startDate);
      ps.setString(3, endDate);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          GttWorkflow gttWorkflow = new GttWorkflow();
          gttWorkflow.setCaseId(rs.getString("case_id"));
          gttWorkflow.setSmssTimestamp(
              ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("SMSS_TIMESTAMP")));
          result.add(gttWorkflow);
        }
      }
    }
    return result;
  }

  public static Map<String, List<GttWorkflow>> getAbsractionCaseMap(
      Connection con, String startDate, String endDate) throws SQLException {
    Map<String, List<GttWorkflow>> map = new HashMap<>();

    String query =
        "select"
            + " case_id, smss_timestamp, user_id"
            + " from "
            + " gtt_workflow gw"
            + " inner join tqmc_user tu on tu.user_id = gw.recipient_user_id"
            + " where"
            + " SENDING_STAGE = 'ABSTRACTION'"
            + " and step_status = 'completed'"
            + " and smss_timestamp >= ? "
            + " and smss_timestamp < ?";

    try (PreparedStatement ps = con.prepareStatement(query)) {

      ps.setString(1, startDate);
      ps.setString(2, endDate);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          GttWorkflow gttWorkflow = new GttWorkflow();
          gttWorkflow.setCaseId(rs.getString("case_id"));
          gttWorkflow.setSmssTimestamp(
              ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("SMSS_TIMESTAMP")));
          List<GttWorkflow> newList = new ArrayList<>();
          String userId = rs.getString("user_id");
          if (map.containsKey(userId)) {
            newList = map.get(userId);
          }
          newList.add(gttWorkflow);
          map.put(userId, newList);
        }
      }
    }

    return map;
  }

  public static Map<String, Set<String>> getConsensusMap(
      Connection con, String startDate, String endDate) throws SQLException {
    Map<String, Set<String>> map = new HashMap<>();
    String query =
        " select case_id,  abs_1_user_id, abs_2_user_id from "
            + " (SELECT gcc.case_id, gac1.user_id AS abs_1_user_id, gac2.user_id AS abs_2_user_id, smss_timestamp "
            + " FROM gtt_consensus_case gcc "
            + "         INNER JOIN gtt_workflow gw ON gw.case_id = gcc.case_id "
            + "        LEFT JOIN gtt_abstractor_case gac1 ON gac1.case_id = gcc.abs_1_case_id "
            + "        LEFT JOIN gtt_abstractor_case gac2 ON gac2.case_id = gcc.abs_2_case_id "
            + "        WHERE gw.step_status = 'completed' "
            + "AND smss_timestamp >= ? "
            + "AND smss_timestamp < ? "
            + "and is_latest)  ";
    try (PreparedStatement ps = con.prepareStatement(query)) {
      int paramaterIndex = 1;
      ps.setString(paramaterIndex++, startDate);
      ps.setString(paramaterIndex++, endDate);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {

          String caseId = rs.getString("case_id");
          String absOneId = rs.getString("abs_1_user_id");
          String absTwoId = rs.getString("abs_2_user_id");

          Set<String> newList1 = new HashSet<>();
          Set<String> newList2 = new HashSet<>();

          if (map.containsKey(absOneId)) {
            newList1 = map.get(absOneId);
          }
          if (map.containsKey(absTwoId)) {
            newList2 = map.get(absTwoId);
          }
          newList1.add(caseId);
          newList2.add(caseId);

          map.put(absOneId, newList1);
          map.put(absTwoId, newList2);
        }
      }
    }
    return map;
  }

  public static double getAverageCompletionTime(Connection con, List<GttWorkflow> gttWorkflows)
      throws SQLException {
    double totalTime = 0;
    double cases = 0;
    for (GttWorkflow gttWorkflow : gttWorkflows) {
      totalTime += getAbstractionCompletionTime(con, gttWorkflow);
      cases++;
    }
    if (cases > 0) {
      return (totalTime / cases);
    } else {
      return 0;
    }
  }

  public static double getAbstractionCompletionTime(Connection con, GttWorkflow gttWorkflow)
      throws SQLException {
    int totalTime = 0;
    String query = "select cumulative_time from gtt_case_time where case_id = ? ";
    try (PreparedStatement ps = con.prepareStatement(query)) {
      ps.setString(1, gttWorkflow.getCaseId());
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          totalTime = rs.getInt("cumulative_time");
        }
      }
    }
    return (double) totalTime / 60.0;
  }

  public static double getAverageDaysToComplete(
      Connection con, TQMCUserInfo user, List<GttWorkflow> gttWorkflows) throws SQLException {
    double totalDays = 0;
    double totalCases = 0;

    String query =
        "select"
            + " smss_timestamp"
            + " from"
            + " gtt_workflow"
            + " where"
            + " case_id = ?"
            + " and "
            + " case_type = 'abstraction'"
            + " and "
            + " step_status = 'not_started'"
            + " and"
            + " recipient_user_id = ?";
    for (GttWorkflow gttWorkflow : gttWorkflows) {
      try (PreparedStatement ps = con.prepareStatement(query)) {
        ps.setString(1, gttWorkflow.getCaseId());
        ps.setString(2, user.getUserId());
        if (ps.execute()) {
          ResultSet rs = ps.getResultSet();
          while (rs.next()) {
            totalCases++;
            totalDays +=
                ConversionUtils.getDaysBetween(
                    ConversionUtils.getLocalDateTimeFromTimestamp(
                        rs.getTimestamp("smss_timestamp")),
                    gttWorkflow.getSmssTimestamp());
          }
        }
      }
    }
    if (totalCases > 0) {
      return (totalDays / totalCases);
    } else {
      return 0;
    }
  }

  public static double getPhysicianCaseAverageDaysToComplete(
      Connection con, TQMCUserInfo user, List<GttWorkflow> gttWorkflows) throws SQLException {
    double totalDays = 0;
    double totalCases = 0;

    String query =
        "select"
            + " smss_timestamp"
            + " from"
            + " gtt_workflow"
            + " where"
            + " case_id = ?"
            + " and "
            + " case_type = 'physician'"
            + " and "
            + " step_status = 'not_started'"
            + " and"
            + " recipient_user_id = ?";
    for (GttWorkflow gttWorkflow : gttWorkflows) {
      try (PreparedStatement ps = con.prepareStatement(query)) {
        ps.setString(1, gttWorkflow.getCaseId());
        ps.setString(2, user.getUserId());
        if (ps.execute()) {
          ResultSet rs = ps.getResultSet();
          while (rs.next()) {
            totalCases++;
            totalDays +=
                ConversionUtils.getDaysBetween(
                    ConversionUtils.getLocalDateTimeFromTimestamp(
                        rs.getTimestamp("smss_timestamp")),
                    gttWorkflow.getSmssTimestamp());
          }
        }
      }
    }
    if (totalCases > 0) {
      return (totalDays / totalCases);
    } else {
      return 0;
    }
  }

  public static LocalDateTime getWorkflowAssignedDate(
      Connection con, GttWorkflow completedWorlflow, TQMCUserInfo user) throws SQLException {
    LocalDateTime retVal = LocalDateTime.of(1, 1, 1, 1, 1);
    String query =
        "select"
            + " smss_timestamp"
            + " from"
            + " gtt_workflow"
            + " where"
            + " case_id = ? "
            + " and "
            + " case_type = 'abstraction'"
            + " and "
            + " step_status = 'not_started'"
            + " and"
            + " recipient_user_id = ? ";
    try (PreparedStatement ps = con.prepareStatement(query)) {
      ps.setString(1, completedWorlflow.getCaseId());
      ps.setString(2, user.getUserId());
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          retVal = ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("smss_timestamp"));
        }
      }
    }
    return retVal;
  }

  public static LocalDateTime getPhysicianWorkflowAssignedDate(
      Connection con, GttWorkflow completedWorlflow, TQMCUserInfo user) throws SQLException {
    LocalDateTime retVal = LocalDateTime.of(1, 1, 1, 1, 1);
    String query =
        "select"
            + " smss_timestamp"
            + " from"
            + " gtt_workflow"
            + " where"
            + " case_id = ? "
            + " and "
            + " case_type = 'physician'"
            + " and "
            + " step_status = 'not_started'"
            + " and"
            + " recipient_user_id = ? ";
    try (PreparedStatement ps = con.prepareStatement(query)) {
      ps.setString(1, completedWorlflow.getCaseId());
      ps.setString(2, user.getUserId());
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          retVal = ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("smss_timestamp"));
        }
      }
    }
    return retVal;
  }

  public static Pair<Integer, Integer> getNumberOfEventsAndTriggers(
      Connection con, List<GttWorkflow> gttWorkflows) throws SQLException {
    int triggers = 0;
    int events = 0;
    for (GttWorkflow gttWorkflow : gttWorkflows) {
      Pair<Integer, Integer> eventsAndTriggers =
          getNumberOfEventsAndTriggersForGttWorkflow(con, gttWorkflow);
      events += eventsAndTriggers.getFirst();
      triggers += eventsAndTriggers.getSecond();
    }
    return new Pair<Integer, Integer>(events, triggers);
  }

  public static Pair<Integer, Integer> getNumberOfEventsAndTriggersForGttWorkflow(
      Connection con, GttWorkflow gttWorkflow) throws SQLException {
    int triggers = 0;
    int events = 0;

    String query =
        "select TRIGGER_TEMPLATE_ID, EVENT_TYPE_TEMPLATE_ID from  gtt_record_case_event where case_id = ?";
    try (PreparedStatement ps = con.prepareStatement(query)) {
      ps.setString(1, gttWorkflow.getCaseId());
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          if (!rs.getString("TRIGGER_TEMPLATE_ID").isEmpty()
              && !rs.getString("TRIGGER_TEMPLATE_ID").equals(TQMCConstants.NO_TRIGGER_FOUND_ID)) {
            triggers++;
          }
          if (!rs.getString("EVENT_TYPE_TEMPLATE_ID").isEmpty()
              && !rs.getString("EVENT_TYPE_TEMPLATE_ID")
                  .equals(TQMCConstants.NO_ADVERSE_EVENT_ID)) {
            events++;
          }
        }
      }
    }
    return new Pair<Integer, Integer>(events, triggers);
  }

  public static double getAverageIrrFromConsensusCaseIds(Connection con, Set<String> caseIds)
      throws SQLException {
    double cases = 0;
    double totalIrr = 0;
    for (String caseId : caseIds) {
      double irr = getIrrFromConsensusCaseId(con, caseId);
      if (irr >= 0) {
        totalIrr += irr;
        cases++;
      }
    }
    if (cases > 0) {
      return totalIrr / cases;
    } else {
      return -1;
    }
  }

  public static double getIrrFromConsensusCaseId(Connection con, String caseId)
      throws SQLException {
    String query = "select is_match from gtt_irr_match where consensus_case_id = ? ";
    try (PreparedStatement ps = con.prepareStatement(query)) {
      ps.setString(1, caseId);
      double instances = 0;
      double matches = 0;
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          if (rs.getBoolean("is_match")) {
            matches++;
          }
          instances++;
        }
      }
      if (instances > 0) {
        return matches / instances;
      } else return -1;
    }
  }

  public static String getRecordIdFromGttWorkFlowId(Connection con, String gttCaseId)
      throws SQLException {
    String query = "select record_id from GTT_abstractor_case where case_id = ? ";
    try (PreparedStatement ps = con.prepareStatement(query)) {
      ps.setString(1, gttCaseId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          if (!rs.getString("record_id").isEmpty()) {
            return rs.getString("record_id");
          }
        }
      }
    }
    throw new TQMCException(ErrorCode.NOT_FOUND, "GTT Case Not Found");
  }

  public static String getRecordIdFromGttPhysicianWorkFlowId(Connection con, String gttCaseId)
      throws SQLException {
    String query = "select record_id from GTT_physician_case where case_id = ? ";
    try (PreparedStatement ps = con.prepareStatement(query)) {
      ps.setString(1, gttCaseId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          if (!rs.getString("record_id").isEmpty()) {
            return rs.getString("record_id");
          }
        }
      }
    }
    throw new TQMCException(ErrorCode.NOT_FOUND, "GTT Case Not Found");
  }

  public static String getMTFNameFromRecordId(Connection con, String recordId) throws SQLException {
    String query =
        "SELECT COALESCE(mtf.alias_mtf_name, mtf.mtf_name) AS MTF_NAME from mtf inner join tqmc_record r on r.dmis_id = mtf.dmis_id where r.record_id = ?";
    try (PreparedStatement ps = con.prepareStatement(query)) {
      ps.setString(1, recordId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          return rs.getString("MTF_NAME");
        }
      }
    }
    throw new TQMCException(ErrorCode.NOT_FOUND, "Could not find MTF name");
  }

  public static String getPartnerForCompletedConsensus(Connection con, String userId, String caseId)
      throws SQLException {
    String query =
        "select recipient_user_id from gtt_workflow where "
            + " (case_id ="
            + " (select ABS_1_CASE_ID from gtt_consensus_case where case_id = ? )"
            + " or"
            + " case_id = "
            + " (select ABS_2_CASE_ID from gtt_consensus_case where case_id = ?) "
            + " )"
            + " and step_status = 'completed'";
    try (PreparedStatement ps = con.prepareStatement(query)) {
      ps.setString(1, caseId);
      ps.setString(2, caseId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          String abstractorId = rs.getString("recipient_user_id");
          if (!abstractorId.isEmpty() && !abstractorId.equals(userId)) {
            return abstractorId;
          }
        }
      }
    }
    throw new TQMCException(ErrorCode.NOT_FOUND, "Partner not found");
  }

  public static List<String> getCompletedConsensusCasesFromAbstractions(
      Connection con, List<GttWorkflow> abstractions) throws SQLException {
    List<String> result = new ArrayList<>();
    String query =
        "select case_id from gtt_workflow"
            + " where case_id ="
            + " (select case_id from gtt_consensus_case"
            + " where abs_1_case_id = ?"
            + " or abs_2_case_id = ? )"
            + " and step_status= 'completed'";

    for (GttWorkflow abstraction : abstractions) {
      try (PreparedStatement ps = con.prepareStatement(query)) {
        ps.setString(1, abstraction.getCaseId());
        ps.setString(2, abstraction.getCaseId());
        if (ps.execute()) {
          ResultSet rs = ps.getResultSet();
          while (rs.next()) {
            String caseId = rs.getString("case_id");
            if (!caseId.isEmpty()) {
              result.add(caseId);
            }
          }
        }
      }
    }
    return result;
  }

  public static String getConsensusCaseFromAbstraction(Connection con, GttWorkflow abstraction)
      throws SQLException {

    String query =
        "SELECT gcc.CASE_ID FROM GTT_CONSENSUS_CASE gcc "
            + "INNER JOIN GTT_WORKFLOW gw ON gw.CASE_ID = gcc.CASE_ID "
            + "WHERE gw.IS_LATEST AND (gcc.abs_1_case_id = ? OR gcc.abs_2_case_id = ?) ";
    try (PreparedStatement ps = con.prepareStatement(query)) {
      ps.setString(1, abstraction.getCaseId());
      ps.setString(2, abstraction.getCaseId());
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          String caseId = rs.getString("case_id");
          if (!caseId.isEmpty()) {
            return caseId;
          }
        }
      }
    }
    throw new TQMCException(ErrorCode.NOT_FOUND, "Consensus case not found");
  }

  public static String getNameFromUserName(Connection con, String userId) throws SQLException {
    String query = "select * from tqmc_user where user_id = ?";
    try (PreparedStatement ps = con.prepareStatement(query)) {
      ps.setString(1, userId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          String firstName = rs.getString("first_name");
          String lastName = rs.getString("last_name");
          return firstName + " " + lastName;
        }
      }
    }
    throw new TQMCException(ErrorCode.NOT_FOUND, "User not found");
  }

  public static LocalDateTime getGttCaseUpdatedAt(Connection con, String caseId, String caseTable)
      throws SQLException {
    try (PreparedStatement ps =
        con.prepareStatement("SELECT UPDATED_AT FROM " + caseTable + " WHERE CASE_ID = ?")) {
      ps.setString(1, caseId);
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        return rs.getTimestamp("UPDATED_AT").toLocalDateTime();
      }
    }
    return null;
  }

  public static void updateGttCaseUpdatedAt(
      Connection con, String caseId, String caseTable, LocalDateTime newTime) throws SQLException {
    try (PreparedStatement ps =
        con.prepareStatement("UPDATE " + caseTable + " SET UPDATED_AT = ? WHERE CASE_ID = ?")) {
      ps.setString(1, newTime.toString());
      ps.setString(2, caseId);
      ps.execute();
    }
  }
}
