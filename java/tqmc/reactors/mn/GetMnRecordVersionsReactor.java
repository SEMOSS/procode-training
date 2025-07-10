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
import tqmc.domain.mn.MnCase;
import tqmc.domain.mn.MnCaseQuestion;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetMnRecordVersionsReactor extends AbstractTQMCReactor {

  private static final String mnRecordQuery =
      "SELECT mc.CASE_ID, mc.RECORD_ID, mat.IS_SECOND_LEVEL, mc.UPDATED_AT,"
          + " mc.ATTESTATION_SIGNATURE, mc.ATTESTED_AT, mc.REOPENING_REASON,"
          + " mw.STEP_STATUS,"
          + " mc.SPECIALTY_ID,"
          + " mr.CARE_CONTRACTOR_ID, mr.FACILITY_NAME, mc.DUE_DATE_REVIEW, mc.DUE_DATE_DHA, "
          + " mr.ALIAS_RECORD_ID,"
          + " CONCAT(mr.PATIENT_LAST_NAME, ', ', mr.PATIENT_FIRST_NAME) AS PATIENT_NAME, "
          + " mc.RECOMMENDATION_RESPONSE,"
          + " mc.RECOMMENDATION_EXPLANATION, "
          + " mc.USER_ID, "
          + " mw.CASE_ID AS BASE_CASE_ID, "
          + " mc.submission_guid, "
          + " mw.recipient_user_id AS ACTIVE_USER_ID "
          + " FROM MN_CASE mc "
          + "JOIN MN_WORKFLOW mw\n"
          + "  ON ("
          + "      (mc.submission_guid IS NOT NULL AND mc.submission_guid = mw.guid) "
          + "      OR "
          + "      (mc.submission_guid IS NULL AND mc.case_id = mw.case_id AND mw.IS_LATEST = 1) "
          + "     )"
          + " INNER JOIN MN_RECORD mr"
          + " ON mr.RECORD_ID = mc.RECORD_ID  "
          + " LEFT JOIN MN_APPEAL_TYPE mat"
          + " ON mc.APPEAL_TYPE_ID = mat.APPEAL_TYPE_ID"
          + " WHERE mr.RECORD_ID = ? AND mc.DELETED_AT IS NULL ";
  private static final String mnQuestionQuery = TQMCHelper.getMnQuestionQuery();

  public GetMnRecordVersionsReactor() {
    this.keysToGet = new String[] {TQMCConstants.RECORD_ID};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    String queriedRecord = this.keyValue.get(TQMCConstants.RECORD_ID);

    // permissions only for management leads
    if (!(hasProductPermission(TQMCConstants.MN)
        && (hasRole(TQMCConstants.MANAGEMENT_LEAD) || hasRole(TQMCConstants.CONTRACTING_LEAD)))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Map<String, Object> output = new HashMap<>();
    Map<String, Object> mnCase = new HashMap<>();
    Map<String, Object> versionMap = new HashMap<>();
    mnCase.put("version_map", versionMap);
    output.put("mn_case", mnCase);

    try (PreparedStatement ps = con.prepareStatement(mnRecordQuery)) {
      ps.setString(1, queriedRecord);
      if (ps.execute()) {

        ResultSet rs = ps.getResultSet();
        // add query results to map
        while (rs.next()) {
          output.putIfAbsent("record_id", rs.getString("RECORD_ID"));
          output.putIfAbsent("care_contractor_id", rs.getString("CARE_CONTRACTOR_ID"));
          output.putIfAbsent("facility_name", rs.getString("FACILITY_NAME"));
          output.putIfAbsent("alias_record_id", rs.getString("ALIAS_RECORD_ID"));
          output.putIfAbsent("patient_name", rs.getString("PATIENT_NAME"));

          if (rs.getString("SUBMISSION_GUID") == null) {
            mnCase.putIfAbsent("case_id", rs.getString("BASE_CASE_ID"));
            mnCase.putIfAbsent("current_case_status", rs.getString("STEP_STATUS"));
          }

          MnCase queriedVersion = addVersionDataToMap(con, rs);
          if (!mnCase.containsKey("version_map")) {
            mnCase.put("version_map", new HashMap<String, Object>());
          }
          versionMap.put(
              ConversionUtils.getLocalDateTimeString(queriedVersion.getUpdatedAt()),
              queriedVersion.toMap());
          // mnCase.put("version_map", versionMap);
        }
      }
    }

    return new NounMetadata(output, PixelDataType.MAP);
  }

  private MnCase addVersionDataToMap(Connection con, ResultSet rs) throws SQLException {
    MnCase mnVersion = new MnCase();

    mnVersion.setCaseId(rs.getString("CASE_ID"));
    mnVersion.setCaseStatus(rs.getString("STEP_STATUS"));
    mnVersion.setSpecialtyId(rs.getString("SPECIALTY_ID"));
    mnVersion.setDueDateReview(ConversionUtils.getLocalDateFromDate(rs.getDate("DUE_DATE_REVIEW")));
    mnVersion.setDueDateDHA(ConversionUtils.getLocalDateFromDate(rs.getDate("DUE_DATE_DHA")));
    mnVersion.setUpdatedAt(
        ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("UPDATED_AT")));
    mnVersion.setAttestationSignature(rs.getString("ATTESTATION_SIGNATURE"));
    mnVersion.setAttestedAt(
        ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("ATTESTED_AT")));
    mnVersion.setReopeningReason(rs.getString("REOPENING_REASON"));
    mnVersion.setIsSecondLevel(rs.getInt("IS_SECOND_LEVEL") == 1);
    mnVersion.setRecommendationResponse(rs.getString("RECOMMENDATION_RESPONSE"));
    mnVersion.setRecommendationExplanation(rs.getString("RECOMMENDATION_EXPLANATION"));
    mnVersion.setUserId(rs.getString("USER_ID"));
    mnVersion.setActiveUserId(rs.getString("ACTIVE_USER_ID"));

    List<Map<String, String>> questions = new ArrayList<>();
    try (PreparedStatement questionPs = con.prepareStatement(mnQuestionQuery)) {
      questionPs.setString(1, rs.getString("CASE_ID"));

      if (questionPs.execute()) {
        ResultSet questionResults = questionPs.getResultSet();

        // add questions to list
        while (questionResults.next()) {
          MnCaseQuestion entry = new MnCaseQuestion();

          entry.setCaseQuestionId(questionResults.getString("CASE_QUESTION_ID"));
          entry.setQuestion(questionResults.getString("CASE_QUESTION"));
          entry.setResponse(questionResults.getString("QUESTION_RESPONSE"));
          entry.setRationale(questionResults.getString("QUESTION_RATIONALE"));
          entry.setReference(questionResults.getString("QUESTION_REFERENCE"));

          questions.add(entry.toMap());
        }
      }
    }
    mnVersion.setQuestionsMap(questions);
    return mnVersion;
  }
}
