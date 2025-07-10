package tqmc.reactors.qu.mcsc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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

public class GetMcscCaseReactor extends AbstractTQMCReactor {

  private static final String MCSC_DATA_QUERY =
      "SELECT mc.CASE_ID, qr.ALIAS_RECORD_ID, mc.RECORD_ID, "
          + "mw.STEP_STATUS, "
          + "CONCAT(tu.LAST_NAME, ', ', tu.FIRST_NAME) AS USER_NAME, tu.EMAIL, "
          + "CASE WHEN qr.MISSING_RECEIVED_AT IS NULL THEN GREATEST(qr.CASE_LENGTH_DAYS - DATEDIFF(DAY, qr.RECEIVED_AT , CURRENT_DATE()), 0) "
          + "ELSE GREATEST(qr.CASE_LENGTH_DAYS - DATEDIFF(DAY, qr.MISSING_RECEIVED_AT, CURRENT_DATE()), 0) END AS TIME_REMAINING_DAYS, "
          + "mc.REOPENING_REASON, mc.UPDATED_AT, mw.SMSS_TIMESTAMP AS COMPLETED_AT, "
          + "CONCAT(qr.PATIENT_LAST_NAME, ', ', qr.PATIENT_FIRST_NAME) AS PATIENT_NAME, "
          + "mc.QUALITY_NOTES, mc.UTILIZATION_NOTES, "
          + "mcc.CARE_CONTRACTOR_NAME, "
          + "mc.QUALITY_REVIEW_COMPLETE, "
          + "mc.USER_ID "
          + "FROM MCSC_CASE mc "
          + "LEFT JOIN QU_RECORD qr ON mc.RECORD_ID = qr.RECORD_ID "
          + "LEFT JOIN MCSC_WORKFLOW mw ON mc.CASE_ID = mw.CASE_ID "
          + "LEFT JOIN TQMC_USER tu ON mc.USER_ID = tu.USER_ID "
          + "LEFT JOIN MN_CARE_CONTRACTOR mcc ON mcc.CARE_CONTRACTOR_ID = qr.CARE_CONTRACTOR_ID "
          + "WHERE mc.CASE_ID = ? AND mw.IS_LATEST = 1 AND (mc.DELETED_AT IS NULL)";

  private static final String MCSC_QUALITY_REVIEW_EVENT_QUERY =
      "SELECT mqre.QUALITY_REVIEW_EVENT_ID, mqre.TRIGGER_TEMPLATE_ID, mqre.HARM_CATEGORY_TEMPLATE_ID, mqre.EVENT_DESCRIPTION "
          + "FROM MCSC_CASE mc "
          + "LEFT JOIN MCSC_QUALITY_REVIEW_EVENT mqre ON mc.CASE_ID = mqre.CASE_ID "
          + "WHERE mc.CASE_ID = ? ";

  private static final String MCSC_QUESTION_RESPONSE_QUERY =
      "SELECT mrr.REVIEW_RESPONSE_ID, mrr.QUESTION_TEMPLATE_ID, mrr.RESPONSE_TEMPLATE_ID "
          + "FROM MCSC_CASE mc "
          + "LEFT JOIN MCSC_REVIEW_RESPONSE mrr ON mrr.CASE_ID = mc.CASE_ID "
          + "WHERE mc.CASE_ID = ? ";

  public GetMcscCaseReactor() {
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

    // check for access to the case
    if (!hasProductPermission(TQMCConstants.MCSC)
        || (!hasProductManagementPermission(TQMCConstants.MCSC)
            && !TQMCHelper.hasCaseAccess(con, userId, caseId, TQMCConstants.MCSC))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    // Check if the case is completed or not as management leads can not access
    if (hasRole(TQMCConstants.MANAGEMENT_LEAD)
        && !TQMCHelper.caseCompleted(con, caseId, ProductTables.MCSC)) {
      throw new TQMCException(
          ErrorCode.FORBIDDEN, "Management lead can not access non-completed cases");
    }

    Map<String, Object> result = new HashMap<String, Object>();

    // Query gathers all output parameters
    try (PreparedStatement mcscData = con.prepareStatement(MCSC_DATA_QUERY);
        PreparedStatement qr = con.prepareStatement(MCSC_QUALITY_REVIEW_EVENT_QUERY);
        PreparedStatement quQR = con.prepareStatement(MCSC_QUESTION_RESPONSE_QUERY)) {

      // set the case_id for WHERE clause
      mcscData.setString(1, caseId);
      qr.setString(1, caseId);
      quQR.setString(1, caseId);

      // add query results to output map
      ResultSet mcscDRS = mcscData.executeQuery();

      if (mcscDRS.next()) {
        result.put("case_id", caseId);
        result.put("alias_record_id", mcscDRS.getString("ALIAS_RECORD_ID"));
        result.put("record_id", mcscDRS.getString("RECORD_ID"));
        result.put("case_status", mcscDRS.getString("STEP_STATUS"));
        result.put("user_name", mcscDRS.getString("USER_NAME"));
        result.put("user_email", mcscDRS.getString("EMAIL"));
        result.put("record_id", mcscDRS.getString("RECORD_ID"));
        result.put("time_remaining_days", Integer.toString(mcscDRS.getInt("TIME_REMAINING_DAYS")));
        result.put("reopening_reason", mcscDRS.getString("REOPENING_REASON"));
        result.put(
            "updated_at",
            ConversionUtils.getLocalDateTimeStringFromTimestamp(
                mcscDRS.getTimestamp("UPDATED_AT")));

        if (TQMCHelper.caseCompleted(con, caseId, ProductTables.MCSC)) {
          result.put(
              "completed_at",
              ConversionUtils.getLocalDateTimeStringFromTimestamp(
                  mcscDRS.getTimestamp("COMPLETED_AT")));
        }
        result.put("patient_name", mcscDRS.getString("PATIENT_NAME"));
        result.put("quality_notes", mcscDRS.getString("QUALITY_NOTES"));
        result.put("utilization_notes", mcscDRS.getString("UTILIZATION_NOTES"));
        result.put("care_contractor_name", mcscDRS.getString("CARE_CONTRACTOR_NAME"));
        result.put("quality_review_complete", mcscDRS.getBoolean("QUALITY_REVIEW_COMPLETE"));
        result.put("user_id", mcscDRS.getString("USER_ID"));
      }
      // check if the case being queried exists and the user has permission
      if (result.isEmpty()) {
        throw new TQMCException(ErrorCode.NOT_FOUND, "Case is not found");
      }

      // add quality review event info to output map
      ResultSet qrRS = qr.executeQuery();
      List<Map<String, String>> qualityReviewEvent = new ArrayList<Map<String, String>>();
      while (qrRS.next()) {
        Map<String, String> qualityReviewEventEntry = new HashMap<String, String>();
        qualityReviewEventEntry.put(
            "quality_review_event_id", qrRS.getString("QUALITY_REVIEW_EVENT_ID"));
        qualityReviewEventEntry.put("trigger_template_id", qrRS.getString("TRIGGER_TEMPLATE_ID"));
        qualityReviewEventEntry.put(
            "harm_category_template_id", qrRS.getString("HARM_CATEGORY_TEMPLATE_ID"));
        qualityReviewEventEntry.put("event_description", qrRS.getString("EVENT_DESCRIPTION"));
        qualityReviewEvent.add(qualityReviewEventEntry);
      }
      result.put("quality_review_events", qualityReviewEvent);

      // add question and response info to output map
      ResultSet quQRS = quQR.executeQuery();
      Map<String, Map<String, String>> questions = new HashMap<String, Map<String, String>>();

      while (quQRS.next()) {
        Map<String, String> entry = new HashMap<String, String>();
        String questionResponseId = quQRS.getString("REVIEW_RESPONSE_ID");
        entry.put("question_response_id", questionResponseId);
        entry.put("question_template_id", quQRS.getString("QUESTION_TEMPLATE_ID"));
        entry.put("response_template_id", quQRS.getString("RESPONSE_TEMPLATE_ID"));
        questions.put(questionResponseId, entry);
      }
      result.put("utilization_responses", questions);
    }

    return new NounMetadata(result, PixelDataType.MAP);
  }
}
