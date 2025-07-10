package tqmc.reactors.qu.base;

import com.google.common.collect.Lists;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.CaseStatus;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.Filter.Comparison;
import tqmc.domain.base.GetListPayload;
import tqmc.domain.base.TQMCException;
import tqmc.domain.qu.base.QuCaseTableRow;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetQuCasesReactor extends AbstractTQMCReactor {

  private static final String BASE_QUERY =
      "WITH UNIONED_CASES AS ("
          + "SELECT "
          + "dpc.CASE_ID AS CASE_ID, "
          + "dpc.RECORD_ID AS RECORD_ID, "
          + "dpc.ASSIGNED_AT AS ASSIGNED_DATE, "
          + "dpw.STEP_STATUS AS CASE_STATUS, "
          + "CASE WHEN dpw.RECIPIENT_USER_ID = 'system' "
          + "THEN NULL ELSE dpw.RECIPIENT_USER_ID END AS ASSIGNED_TO_USER_ID, "
          + "'dp' AS CASE_TYPE "
          + "FROM DP_CASE dpc "
          + "LEFT OUTER JOIN DP_WORKFLOW dpw "
          + "ON dpc.CASE_ID = dpw.CASE_ID AND dpw.IS_LATEST = 1 AND dpc.DELETED_AT IS NULL "
          + "UNION ALL "
          + "SELECT "
          + "mcsc.CASE_ID AS CASE_ID, "
          + "mcsc.RECORD_ID AS RECORD_ID, "
          + "mcsc.ASSIGNED_AT AS ASSIGNED_DATE, "
          + "mcscw.STEP_STATUS AS CASE_STATUS, "
          + "CASE WHEN mcscw.RECIPIENT_USER_ID = 'system' "
          + "THEN NULL ELSE mcscw.RECIPIENT_USER_ID END AS ASSIGNED_TO_USER_ID, "
          + "'mcsc' AS CASE_TYPE "
          + "FROM MCSC_CASE mcsc "
          + "LEFT OUTER JOIN MCSC_WORKFLOW mcscw "
          + "ON mcsc.CASE_ID = mcscw.CASE_ID AND mcscw.IS_LATEST = 1 AND mcsc.DELETED_AT IS NULL), "
          + "BASE AS ("
          + "SELECT "
          + "uc.CASE_ID, "
          + "qr.RECORD_ID AS RECORD_ID, "
          + "qr.ALIAS_RECORD_ID AS ALIAS_RECORD_ID, "
          + "qr.CLAIM_NUMBER AS CLAIM_NUMBER, "
          + "CONCAT(qr.PATIENT_LAST_NAME, ', ', qr.PATIENT_FIRST_NAME) AS PATIENT_NAME, "
          + "CONCAT(qr.PATIENT_LAST_NAME, ', ', qr.PATIENT_FIRST_NAME, ' ', qr.PATIENT_LAST_NAME, ' ', qr.PATIENT_FIRST_NAME) AS PATIENT_NAME_QUERY, "
          + "qr.CARE_CONTRACTOR_ID AS CARE_CONTRACTOR_ID, "
          + "CASE WHEN qr.MISSING_RECEIVED_AT IS NULL THEN GREATEST(qr.CASE_LENGTH_DAYS - DATEDIFF(DAY, qr.RECEIVED_AT , CURRENT_DATE()), 0) "
          + "ELSE GREATEST(qr.CASE_LENGTH_DAYS - DATEDIFF(DAY, qr.MISSING_RECEIVED_AT, CURRENT_DATE()), 0) END AS TIME_REMAINING_DAYS, "
          + "CASE WHEN qr.MISSING_RECEIVED_AT IS NULL THEN qr.RECEIVED_AT "
          + "ELSE qr.MISSING_RECEIVED_AT END AS COMPLETE_FILES_RECEIVED_AT, "
          + "mncc.CARE_CONTRACTOR_NAME, "
          + "uc.CASE_TYPE, "
          + "CASE WHEN COUNT(DISTINCT qrf.FILE_NAME) > 0 THEN 1 ELSE 0 END AS HAS_FILES, "
          + "uc.ASSIGNED_DATE, "
          + "uc.CASE_STATUS, "
          + "uc.ASSIGNED_TO_USER_ID, "
          + "CASE WHEN uc.ASSIGNED_TO_USER_ID = 'system' "
          + "THEN NULL ELSE CONCAT(tu.FIRST_NAME, ' ', tu.LAST_NAME) END AS ASSIGNED_TO_USER_NAME, "
          + "CASE WHEN uc.ASSIGNED_TO_USER_ID = 'system' "
          + "THEN NULL ELSE CONCAT(tu.LAST_NAME, ', ', tu.FIRST_NAME, ' ', tu.LAST_NAME, ' ', tu.FIRST_NAME) END AS ASSIGNED_TO_USER_QUERY, "
          + "FROM UNIONED_CASES uc "
          + "LEFT OUTER JOIN QU_RECORD qr "
          + "ON uc.RECORD_ID = qr.RECORD_ID "
          + "LEFT OUTER JOIN MN_CARE_CONTRACTOR mncc "
          + "ON qr.CARE_CONTRACTOR_ID = mncc.CARE_CONTRACTOR_ID "
          + "LEFT OUTER JOIN TQMC_USER tu "
          + "ON uc.ASSIGNED_TO_USER_ID = tu.USER_ID "
          + "LEFT OUTER JOIN QU_RECORD_FILE qrf "
          + "ON qrf.RECORD_ID = uc.RECORD_ID AND qrf.DELETED_AT IS NULL "
          + "GROUP BY uc.CASE_ID, uc.RECORD_ID, qr.ALIAS_RECORD_ID, qr.CLAIM_NUMBER, qr.PATIENT_FIRST_NAME, qr.PATIENT_LAST_NAME, mncc.CARE_CONTRACTOR_NAME, uc.CASE_TYPE, qr.RECEIVED_AT, uc.ASSIGNED_DATE, uc.CASE_STATUS, uc.ASSIGNED_TO_USER_ID, tu.FIRST_NAME, tu.LAST_NAME) "
          + "SELECT BASE.*, COUNT(*) OVER() AS TOTAL_ROW_COUNT FROM BASE ";

  private static final String ASSIGNED_TO_USER_ID = "ASSIGNED_TO_USER_ID";

  private static final List<String> VALID_SORT_FIELDS =
      Lists.newArrayList(
          "RECORD_ID",
          "ALIAS_RECORD_ID",
          "CLAIM_NUMBER",
          "PATIENT_NAME",
          "CARE_CONTRACTOR_NAME",
          "CASE_TYPE",
          "COMPLETE_FILES_RECEIVED_AT",
          "TIME_REMAINING_DAYS",
          "ASSIGNED_TO_USER_NAME",
          "ASSIGNED_DATE",
          "CASE_STATUS");

  private static final List<String> VALID_FILTER_FIELDS =
      Lists.newArrayList(
          "RECORD_ID",
          "ALIAS_RECORD_ID",
          "CLAIM_NUMBER",
          "PATIENT_NAME_QUERY",
          "CARE_CONTRACTOR_NAME",
          "CASE_TYPE",
          "COMPLETE_FILES_RECEIVED_AT",
          "TIME_REMAINING_DAYS",
          "ASSIGNED_TO_USER_QUERY",
          "ASSIGNED_DATE",
          "CASE_STATUS",
          ASSIGNED_TO_USER_ID);

  private static final List<String> DEFAULT_ORDER_FIELDS =
      Lists.newArrayList("TIME_REMAINING_DAYS ASC", "CASE_STATUS ASC", "RECORD_ID ASC");

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    if (!(hasProductPermission(TQMCConstants.DP) || hasProductPermission(TQMCConstants.MCSC))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Boolean managementView = null;
    if (hasRole(TQMCConstants.MANAGEMENT_LEAD) || hasRole(TQMCConstants.CONTRACTING_LEAD)) {
      managementView = true;
    } else if (hasRole(TQMCConstants.QUALITY_REVIEWER)) {
      managementView = false;
    }

    if (managementView == null) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    GetListPayload payload =
        TQMCHelper.getListPayloadObject(
            this.getNounStore(), VALID_SORT_FIELDS, VALID_FILTER_FIELDS);

    if (managementView == false) {
      Map<Comparison, List<String>> filter = new HashMap<>();
      List<String> fieldValues = new ArrayList<>();
      fieldValues.add(userId);
      filter.put(Comparison.EQUAL, fieldValues);

      Map<String, Map<Comparison, List<String>>> collatedFilters = payload.getCollatedFilters();
      if (collatedFilters == null) {
        collatedFilters = new HashMap<>();
      }
      collatedFilters.put(ASSIGNED_TO_USER_ID, filter);
      payload.setCollatedFilters(collatedFilters);
    }

    List<String> arguments = new ArrayList<>();
    String query =
        TQMCHelper.getListQuery(userId, payload, BASE_QUERY, arguments, DEFAULT_ORDER_FIELDS);

    Map<String, Object> output = new LinkedHashMap<>();
    List<QuCaseTableRow> cases = new ArrayList<>();

    try (PreparedStatement ps = con.prepareStatement(query)) {
      for (int i = 1; i < arguments.size(); i++) {
        ps.setString(i, arguments.get(i));
      }
      int trc = 0; // Total Row Count
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          String caseId = rs.getString("CASE_ID");
          String csString = rs.getString("CASE_STATUS");
          CaseStatus caseStatus =
              (csString != null) ? CaseStatus.valueOf(csString.toUpperCase()) : null;
          String recordId = rs.getString("RECORD_ID");
          String aliasRecordId = rs.getString("ALIAS_RECORD_ID");
          String claimNumber = rs.getString("CLAIM_NUMBER");
          String patientName = rs.getString("PATIENT_NAME");
          String patientNameQuery = rs.getString("PATIENT_NAME_QUERY");
          String careContractorName = rs.getString("CARE_CONTRACTOR_NAME");
          String caseType = rs.getString("CASE_TYPE");
          Boolean hasFiles = rs.getInt("HAS_FILES") == 1;
          Integer timeRemainingDays = Integer.parseInt(rs.getString("TIME_REMAINING_DAYS"));
          LocalDate receivedAt =
              ConversionUtils.getLocalDateFromDate(rs.getDate("COMPLETE_FILES_RECEIVED_AT"));
          String userId = rs.getString("ASSIGNED_TO_USER_ID");
          String userName = rs.getString("ASSIGNED_TO_USER_NAME");
          String userNameQuery = rs.getString("ASSIGNED_TO_USER_QUERY");
          LocalDate assignedDate =
              ConversionUtils.getLocalDateFromDate(rs.getDate("ASSIGNED_DATE"));
          String totalRowCount = rs.getString("TOTAL_ROW_COUNT");

          cases.add(
              new QuCaseTableRow(
                  caseId,
                  caseStatus,
                  recordId,
                  aliasRecordId,
                  claimNumber,
                  patientName,
                  patientNameQuery,
                  careContractorName,
                  caseType,
                  hasFiles,
                  timeRemainingDays,
                  receivedAt,
                  userName,
                  userId,
                  userNameQuery,
                  assignedDate));

          trc = Integer.parseInt(totalRowCount);
        }
      }
      output.put("total_row_count", trc);
      List<Map<String, Object>> outMap =
          cases.parallelStream().map(e -> e.toMap()).collect(Collectors.toList());
      output.put("cases", outMap);
    }

    return new NounMetadata(output, PixelDataType.MAP);
  }
}
