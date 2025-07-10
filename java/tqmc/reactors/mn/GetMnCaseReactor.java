package tqmc.reactors.mn;

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
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetMnCaseReactor extends AbstractTQMCReactor {

  private static final String mnCaseQuery =
      "SELECT mc.CASE_ID, mc.RECORD_ID, mat.IS_SECOND_LEVEL, mc.UPDATED_AT,"
          + " mc.ATTESTATION_SIGNATURE, mc.ATTESTED_AT, mc.REOPENING_REASON,"
          + " mw.STEP_STATUS,"
          + " mc.SPECIALTY_ID,"
          + " mr.CARE_CONTRACTOR_ID, mr.FACILITY_NAME, mc.DUE_DATE_REVIEW, mc.DUE_DATE_DHA, "
          + " mr.ALIAS_RECORD_ID,"
          + " CONCAT(mr.PATIENT_LAST_NAME, ', ', mr.PATIENT_FIRST_NAME) AS PATIENT_NAME, "
          + " mc.RECOMMENDATION_RESPONSE,"
          + " mc.RECOMMENDATION_EXPLANATION, "
          + " mc.USER_ID "
          + " FROM MN_CASE mc "
          + " INNER JOIN MN_WORKFLOW mw"
          + " ON mw.CASE_ID = mc.CASE_ID AND mw.IS_LATEST = 1"
          + " INNER JOIN MN_RECORD mr"
          + " ON mr.RECORD_ID = mc.RECORD_ID AND mc.SUBMISSION_GUID IS NULL "
          + " LEFT JOIN MN_APPEAL_TYPE mat"
          + " ON mc.APPEAL_TYPE_ID = mat.APPEAL_TYPE_ID"
          + " WHERE mc.CASE_ID = ? AND mc.DELETED_AT IS NULL ";

  private static final String mnQuestionQuery = TQMCHelper.getMnQuestionQuery();

  public GetMnCaseReactor() {
    this.keysToGet = new String[] {TQMCConstants.CASE_ID};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    String queriedCase = this.keyValue.get(TQMCConstants.CASE_ID);

    // permissions only for reviewer on case
    if (!(hasProductPermission(TQMCConstants.MN)
        && (TQMCHelper.hasCaseAccess(con, userId, queriedCase, TQMCConstants.MN)))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Map<String, Object> output = new HashMap<>();

    try (PreparedStatement caseStatement = con.prepareStatement(mnCaseQuery);
        PreparedStatement questionStatement = con.prepareStatement(mnQuestionQuery)) {
      caseStatement.setString(1, queriedCase);
      questionStatement.setString(1, queriedCase);
      if (caseStatement.execute()) {
        ResultSet caseResults = caseStatement.getResultSet();

        // add query results to map
        while (caseResults.next()) {
          output.put("case_id", caseResults.getString("CASE_ID"));
          output.put("record_id", caseResults.getString("RECORD_ID"));
          output.put("is_second_level", caseResults.getInt("is_second_level") == 1);
          output.put(
              "updated_at",
              ConversionUtils.getLocalDateTimeStringFromTimestamp(
                  caseResults.getTimestamp("UPDATED_AT")));
          output.put("attestation_signature", caseResults.getString("ATTESTATION_SIGNATURE"));
          output.put(
              "attested_at",
              ConversionUtils.getLocalDateStringFromDate(caseResults.getDate("ATTESTED_AT")));
          output.put("reopening_reason", caseResults.getString("REOPENING_REASON"));
          output.put("case_status", caseResults.getString("STEP_STATUS"));
          output.put("specialty_id", caseResults.getString("SPECIALTY_ID"));
          output.put("care_contractor_id", caseResults.getString("CARE_CONTRACTOR_ID"));
          output.put("facility_name", caseResults.getString("FACILITY_NAME"));
          output.put(
              "due_date_review",
              ConversionUtils.getLocalDateStringFromDate(caseResults.getDate("DUE_DATE_REVIEW")));

          output.put("alias_record_id", caseResults.getString("ALIAS_RECORD_ID"));
          output.put("patient_name", caseResults.getString("PATIENT_NAME"));
          output.put("recommendation_response", caseResults.getString("RECOMMENDATION_RESPONSE"));
          output.put(
              "recommendation_explanation", caseResults.getString("RECOMMENDATION_EXPLANATION"));

          List<HashMap<String, String>> questions = new ArrayList<HashMap<String, String>>();
          if (questionStatement.execute()) {
            ResultSet questionResults = questionStatement.getResultSet();

            // add questions to list
            while (questionResults.next()) {
              HashMap<String, String> entry = new HashMap<String, String>();

              entry.put("case_question_id", questionResults.getString("CASE_QUESTION_ID"));
              entry.put("question", questionResults.getString("CASE_QUESTION"));
              entry.put("response", questionResults.getString("QUESTION_RESPONSE"));
              entry.put("rationale", questionResults.getString("QUESTION_RATIONALE"));
              entry.put("reference", questionResults.getString("QUESTION_REFERENCE"));

              questions.add(entry);
            }
          }
          output.put("questions", questions);
        }
      }
    }

    // check that case being queried exists and the user has permission
    if (output.isEmpty()) {
      throw new TQMCException(ErrorCode.NOT_FOUND, "Case is not found");
    }

    return new NounMetadata(output, PixelDataType.MAP);
  }
}
