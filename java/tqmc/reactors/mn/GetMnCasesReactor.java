package tqmc.reactors.mn;

import com.google.common.collect.Lists;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.CaseStatus;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.GetListPayload;
import tqmc.domain.base.TQMCException;
import tqmc.domain.mn.MnManagementCaseTableRow;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetMnCasesReactor extends AbstractTQMCReactor {

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  private static final List<String> VALID_SORT_FIELDS =
      Lists.newArrayList(
          "APPEAL_TYPE_NAME",
          "DUE_DATE_REVIEW",
          "DUE_DATE_DHA",
          "CASE_STATUS",
          "SPECIALTY_NAME",
          "CARE_CONTRACTOR_NAME",
          "ASSIGNED_DATE",
          "USER_NAME",
          "ALIAS_RECORD_ID",
          "PATIENT_NAME",
          "COMPLETED_AT");

  private static final List<String> VALID_FILTER_FIELDS =
      Lists.newArrayList(
          "SPECIALTY_NAME",
          "CARE_CONTRACTOR_NAME",
          "DUE_DATE_REVIEW",
          "DUE_DATE_DHA",
          "CASE_STATUS_STR",
          "ALIAS_RECORD_ID",
          "PATIENT_NAME_QUERY",
          "APPEAL_TYPE_ID",
          "USER_ID",
          "COMPLETED_AT",
          "ASSIGNED_DATE");

  private static final List<String> DEFAULT_ORDER_FIELDS =
      Lists.newArrayList("DUE_DATE_DHA ASC", "CASE_ID ASC");

  /** Constructor, takes in arguments. None of the arguments are mandatory. */
  public GetMnCasesReactor() {
    this.keysToGet = TQMCConstants.LIST_REACTOR_ARGUMENTS;
    this.keyRequired = new int[] {0, 0, 0};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    /** Throws error if correct permissions are not met. */
    if (!(hasProductManagementPermission(TQMCConstants.MN))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    /** Gets input arguments using helper function and validates inputs. */
    GetListPayload payload =
        TQMCHelper.getListPayloadObject(
            this.getNounStore(), VALID_SORT_FIELDS, VALID_FILTER_FIELDS);

    String baseQuery =
        "WITH BASE AS ( "
            + "SELECT "
            + "    mc.CASE_ID AS CASE_ID, "
            + "    mw.STEP_STATUS AS CASE_STATUS_STR, "
            + "    CASE WHEN mw.STEP_STATUS = 'completed' then mw.smss_timestamp END as completed_at,"
            + "    CASE WHEN mw.STEP_STATUS = 'unassigned' THEN 1 WHEN mw.STEP_STATUS = 'not_started' THEN 2 WHEN mw.STEP_STATUS = 'in_progress' THEN 3 WHEN mw.STEP_STATUS = 'completed' THEN 4 END AS CASE_STATUS, "
            + "    mr.RECORD_ID AS RECORD_ID, "
            + "    mat.APPEAL_TYPE_NAME AS APPEAL_TYPE_NAME, "
            + "    mat.APPEAL_TYPE_ID AS APPEAL_TYPE_ID, "
            + "    ts.SPECIALTY_NAME AS SPECIALTY_NAME, "
            + "    mc.SPECIALTY_ID AS SPECIALTY_ID, "
            + "    mr.CARE_CONTRACTOR_NAME AS CARE_CONTRACTOR_NAME, "
            + "    CASE "
            + "        WHEN COUNT(DISTINCT mrf.FILE_NAME) > 0 THEN 1 "
            + "        ELSE 0 "
            + "    END AS HAS_FILES, "
            + "    mc.DUE_DATE_REVIEW AS DUE_DATE_REVIEW, "
            + "    mc.DUE_DATE_DHA AS DUE_DATE_DHA, "
            + "    mc.USER_ID AS USER_ID, "
            + "    mc.ASSIGNED_AT AS ASSIGNED_DATE, "
            + "    mr.ALIAS_RECORD_ID AS ALIAS_RECORD_ID, "
            + "    CONCAT(mr.PATIENT_LAST_NAME, ', ', mr.PATIENT_FIRST_NAME) AS PATIENT_NAME, "
            + "    CONCAT(mr.PATIENT_LAST_NAME, ', ', mr.PATIENT_FIRST_NAME, ' ', mr.PATIENT_LAST_NAME, ' ', mr.PATIENT_FIRST_NAME) as PATIENT_NAME_QUERY "
            + "    FROM "
            + "        MN_RECORD mr "
            + "        INNER JOIN MN_CASE mc ON mc.RECORD_ID = mr.RECORD_ID AND mc.DELETED_AT IS NULL AND mc.SUBMISSION_GUID IS NULL "
            + "        LEFT JOIN TQMC_SPECIALTY ts ON ts.SPECIALTY_ID = mc.SPECIALTY_ID "
            + "        LEFT JOIN MN_WORKFLOW mw ON mw.CASE_ID = mc.CASE_ID AND mw.IS_LATEST = 1 "
            + "        LEFT JOIN MN_RECORD_FILE mrf ON mrf.RECORD_ID = mr.RECORD_ID AND mrf.DELETED_AT IS NULL "
            + "        LEFT JOIN MN_APPEAL_TYPE mat ON mc.APPEAL_TYPE_ID = mat.APPEAL_TYPE_ID "
            + "    GROUP BY "
            + "        CASE_ID, CASE_STATUS, RECORD_ID, APPEAL_TYPE_NAME, SPECIALTY_NAME, SPECIALTY_ID, CARE_CONTRACTOR_NAME, DUE_DATE_REVIEW, DUE_DATE_DHA, USER_ID, ASSIGNED_DATE, ALIAS_RECORD_ID, PATIENT_NAME "
            + ")"
            + "SELECT BASE.*, COUNT(*) OVER() AS TOTAL_ROW_COUNT FROM BASE ";

    /** Forms the final query using all of the information above. */
    List<String> arguments = new ArrayList<>();
    String query =
        TQMCHelper.getListQuery(userId, payload, baseQuery, arguments, DEFAULT_ORDER_FIELDS);

    /** Setting up data structures. */
    Map<String, Object> output = new LinkedHashMap<>();
    List<MnManagementCaseTableRow> cases = new ArrayList<>();

    try (PreparedStatement ps = con.prepareStatement(query)) {
      for (int i = 1; i < arguments.size(); i++) {
        ps.setString(i, arguments.get(i));
      }
      int trc = 0; // Total Row Count
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          String caseId = rs.getString("CASE_ID");
          String csString = rs.getString("CASE_STATUS_STR");
          CaseStatus caseStatus =
              (csString != null) ? CaseStatus.valueOf(csString.toUpperCase()) : null;
          String recordId = rs.getString("RECORD_ID");
          String appealTypeId = rs.getString("APPEAL_TYPE_ID");
          String specialtyId = rs.getString("SPECIALTY_ID");
          String careContractorName = rs.getString("CARE_CONTRACTOR_NAME");
          Boolean hasFiles = rs.getInt("HAS_FILES") == 1;
          String dueDateReview =
              ConversionUtils.getLocalDateStringFromDate(rs.getDate("DUE_DATE_REVIEW"));
          String dueDateDHA =
              ConversionUtils.getLocalDateStringFromDate(rs.getDate("DUE_DATE_DHA"));
          String userId = rs.getString("USER_ID");
          String assignedDate = rs.getString("ASSIGNED_DATE");
          String aliasRecordId = rs.getString("ALIAS_RECORD_ID");
          String patientName = rs.getString("PATIENT_NAME");
          String patientNameQuery = rs.getString("PATIENT_NAME_QUERY");
          String totalRowCount = rs.getString("TOTAL_ROW_COUNT");
          String completedAtDate =
              ConversionUtils.getLocalDateStringFromDate(rs.getDate("COMPLETED_AT"));

          cases.add(
              new MnManagementCaseTableRow(
                  caseId,
                  caseStatus,
                  recordId,
                  appealTypeId,
                  specialtyId,
                  careContractorName,
                  hasFiles,
                  dueDateReview,
                  dueDateDHA,
                  userId,
                  assignedDate,
                  aliasRecordId,
                  patientName,
                  patientNameQuery,
                  completedAtDate));

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
