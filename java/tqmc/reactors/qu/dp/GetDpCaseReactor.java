package tqmc.reactors.qu.dp;

import com.google.common.collect.Lists;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.ProductTables;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetDpCaseReactor extends AbstractTQMCReactor {

  // Base query to get data using caseId
  private static final String DP_DATA_QUERY =
      "SELECT dc.CASE_ID, dc.RECORD_ID, dc.REOPENING_REASON, dc.UPDATED_AT, dc.CASE_NOTES, "
          + "qr.ALIAS_RECORD_ID, CONCAT(qr.PATIENT_LAST_NAME, ', ', qr.PATIENT_FIRST_NAME) AS PATIENT_NAME, "
          + "dw.STEP_STATUS, "
          + "CONCAT(tu.LAST_NAME, ', ', tu.FIRST_NAME) AS USER_NAME, tu.EMAIL, "
          + "CASE WHEN qr.MISSING_RECEIVED_AT IS NULL THEN GREATEST(qr.CASE_LENGTH_DAYS - DATEDIFF(DAY, qr.RECEIVED_AT , CURRENT_DATE()), 0) "
          + "ELSE GREATEST(qr.CASE_LENGTH_DAYS - DATEDIFF(DAY, qr.MISSING_RECEIVED_AT, CURRENT_DATE()), 0) END AS TIME_REMAINING_DAYS, "
          + "mcc.CARE_CONTRACTOR_NAME, "
          + "dc.USER_ID "
          + "FROM DP_CASE dc "
          + "LEFT JOIN QU_RECORD qr ON qr.RECORD_ID = dc.RECORD_ID "
          + "LEFT JOIN DP_WORKFLOW dw ON dw.CASE_ID = dc.CASE_ID "
          + "LEFT JOIN TQMC_USER tu ON tu.USER_ID = dc.USER_ID "
          + "LEFT JOIN MN_CARE_CONTRACTOR mcc ON mcc.CARE_CONTRACTOR_ID = qr.CARE_CONTRACTOR_ID "
          + "WHERE dw.IS_LATEST = 1 AND (dc.DELETED_AT IS NULL) AND dc.CASE_ID = ?";

  private static final String DP_AUDIT_QUERY =
      "SELECT dar.AUDIT_RESPONSE_ID, dar.QUESTION_TEMPLATE_ID, dar.RESPONSE_TEMPLATE_ID, "
          + "dqt.AUDIT_TYPE, "
          + "FROM DP_CASE dc "
          + "LEFT JOIN DP_AUDIT_RESPONSE dar ON dc.CASE_ID = dar.CASE_ID "
          + "LEFT JOIN DP_QUESTION_TEMPLATE dqt ON dar.QUESTION_TEMPLATE_ID = dqt.QUESTION_TEMPLATE_ID "
          + "WHERE dc.CASE_ID = ? AND (dc.DELETED_AT IS NULL)"
          + "GROUP BY dc.CASE_ID, dar.AUDIT_RESPONSE_ID ";

  private static final List<String> AUDIT_TYPE = Lists.newArrayList("general", "patient_visit");

  public GetDpCaseReactor() {
    this.keysToGet = new String[] {TQMCConstants.CASE_ID};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    String caseId = this.keyValue.get(TQMCConstants.CASE_ID);

    // Check for access to the case
    if (!hasProductPermission(TQMCConstants.DP)
        || (!hasProductManagementPermission(TQMCConstants.DP)
            && !TQMCHelper.hasCaseAccess(con, userId, caseId, TQMCConstants.DP))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    // Check if the case is completed or not as management leads can not access
    if (hasRole(TQMCConstants.MANAGEMENT_LEAD)
        && !TQMCHelper.caseCompleted(con, caseId, ProductTables.DP)) {
      throw new TQMCException(
          ErrorCode.FORBIDDEN, "Management lead can not access non-completed cases");
    }

    Map<String, Object> result = new HashMap<String, Object>();

    // Query gathers all output parameters
    try (PreparedStatement dpData = con.prepareStatement(DP_DATA_QUERY);
        PreparedStatement dpAudit = con.prepareStatement(DP_AUDIT_QUERY)) {

      // Set caseId for Where clause
      dpData.setString(1, caseId);
      dpAudit.setString(1, caseId);

      // Add query results to output map
      ResultSet dpDRS = dpData.executeQuery();
      if (dpDRS.next()) {
        result.put("case_id", caseId);
        result.put("record_id", dpDRS.getString("RECORD_ID"));
        result.put("reopening_reason", dpDRS.getString("REOPENING_REASON"));
        result.put(
            "updated_at",
            ConversionUtils.getLocalDateTimeStringFromTimestamp(dpDRS.getTimestamp("UPDATED_AT")));
        result.put("case_notes", dpDRS.getString("CASE_NOTES"));
        result.put("time_remaining_days", Integer.toString(dpDRS.getInt("TIME_REMAINING_DAYS")));
        result.put("alias_record_id", dpDRS.getString("ALIAS_RECORD_ID"));
        result.put("patient_name", dpDRS.getString("PATIENT_NAME"));
        result.put("case_status", dpDRS.getString("STEP_STATUS"));
        result.put("user_name", dpDRS.getString("USER_NAME"));
        result.put("user_email", dpDRS.getString("EMAIL"));
        result.put("care_contractor_name", dpDRS.getString("CARE_CONTRACTOR_NAME"));
        result.put("user_id", dpDRS.getString("USER_ID"));
      }
      // Check if the case being queried exists and the user has permission
      if (result.isEmpty()) {
        throw new TQMCException(ErrorCode.NOT_FOUND, "Case Not Found");
      }

      ResultSet dpARS = dpAudit.executeQuery();
      Map<String, Map<String, String>> patientQuestions =
          new HashMap<String, Map<String, String>>();
      Map<String, Map<String, String>> generalQuestions =
          new HashMap<String, Map<String, String>>();

      while (dpARS.next()) {
        Map<String, String> entry = new HashMap<String, String>();
        String questionResponseId = dpARS.getString("AUDIT_RESPONSE_ID");
        entry.put("question_response_id", questionResponseId);
        entry.put("question_template_id", dpARS.getString("QUESTION_TEMPLATE_ID"));
        entry.put("response_template_id", dpARS.getString("RESPONSE_TEMPLATE_ID"));

        if (dpARS.getString("AUDIT_TYPE").equals(AUDIT_TYPE.get(0))) {
          generalQuestions.put(questionResponseId, entry);
        } else if (dpARS.getString("AUDIT_TYPE").equals(AUDIT_TYPE.get(1))) {
          patientQuestions.put(questionResponseId, entry);
        }
      }
      result.put("patient_responses", patientQuestions);
      result.put("general_responses", generalQuestions);
    }

    return new NounMetadata(result, PixelDataType.MAP);
  }
}
