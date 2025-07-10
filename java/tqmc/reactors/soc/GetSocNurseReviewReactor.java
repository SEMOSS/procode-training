package tqmc.reactors.soc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.CaseStatus;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.ProductTables;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetSocNurseReviewReactor extends AbstractTQMCReactor {

  private static final String QUERY =
      "SELECT "
          + "snr.nurse_review_id AS NURSE_REVIEW_ID, "
          + "sr.record_id AS RECORD_ID, "
          + "sr.alias_record_id AS ALIAS_RECORD_ID, "
          + "snr.user_id AS USER_ID, "
          + "CONCAT(sr.patient_last_name, ', ', sr.patient_first_name) AS PATIENT_NAME, "
          + "snr.DUE_DATE_REVIEW, "
          + "snr.period_of_care_start AS POC_START, "
          + "snr.period_of_care_end AS POC_END, "
          + "snr.injury AS INJURY, "
          + "snr.diagnoses AS DIAGNOSES, "
          + "snr.allegations AS ALLEGATIONS, "
          + "snr.summary_of_facts AS SUMMARY_OF_FACTS, "
          + "snr.updated_at AS UPDATED_AT, "
          + "CONCAT(tu.FIRST_NAME, ' ', tu.LAST_NAME) AS NURSE_REVIEWER_NAME, "
          + "sw.step_status AS CASE_STATUS, "
          + "snr.reopening_reason AS REOPENING_REASON "
          + "FROM soc_record sr "
          + "RIGHT OUTER JOIN soc_nurse_review snr "
          + "ON snr.record_id = sr.record_id "
          + "LEFT OUTER JOIN soc_workflow sw "
          + "ON snr.nurse_review_id = sw.case_id "
          + "LEFT OUTER JOIN tqmc_user tu "
          + "ON snr.user_id = tu.user_id "
          + "WHERE snr.nurse_review_id = ? AND sw.is_latest = 1";

  public GetSocNurseReviewReactor() {
    this.keysToGet = new String[] {TQMCConstants.NURSE_REVIEW_ID};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    String nurseReviewId = this.keyValue.get(TQMCConstants.NURSE_REVIEW_ID);
    String recordId = TQMCHelper.getNurseReviewRecordId(con, nurseReviewId);

    if (!hasProductPermission(TQMCConstants.SOC)
        || (!(hasRole(TQMCConstants.MANAGEMENT_LEAD) || hasRole(TQMCConstants.CONTRACTING_LEAD))
            && !TQMCHelper.hasRecordAccess(con, userId, recordId, ProductTables.SOC))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    if (!TQMCHelper.isNurseReviewer(con, userId, nurseReviewId)
        && !TQMCHelper.caseCompleted(con, nurseReviewId, ProductTables.SOC)) {
      throw new TQMCException(
          ErrorCode.FORBIDDEN, "Only nurse reviewers can access non-completed cases");
    }

    Map<String, String> output = new HashMap<>();

    try (PreparedStatement ps = con.prepareStatement(QUERY)) {
      ps.setString(1, nurseReviewId);
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        output.put("nurse_review_id", rs.getString("NURSE_REVIEW_ID"));
        output.put("nurse_reviewer_name", rs.getString("NURSE_REVIEWER_NAME"));
        output.put("record_id", rs.getString("RECORD_ID"));
        output.put("alias_record_id", rs.getString("ALIAS_RECORD_ID"));
        output.put("user_id", rs.getString("USER_ID"));
        output.put("patient_name", rs.getString("PATIENT_NAME"));
        String dueDateReview =
            ConversionUtils.getLocalDateStringFromDate(rs.getDate("DUE_DATE_REVIEW"));
        output.put("due_date_review", dueDateReview);
        output.put("period_of_care_start", rs.getString("POC_START"));
        output.put("period_of_care_end", rs.getString("POC_END"));
        output.put("injury", rs.getString("INJURY"));
        output.put("diagnoses", rs.getString("DIAGNOSES"));
        output.put("allegations", rs.getString("ALLEGATIONS"));
        output.put("summary_of_facts", rs.getString("SUMMARY_OF_FACTS"));
        output.put(
            "updated_at",
            ConversionUtils.getLocalDateTimeStringFromTimestamp(rs.getTimestamp("UPDATED_AT")));
        String csString = rs.getString("CASE_STATUS");
        output.put(
            "case_status",
            (csString != null) ? CaseStatus.valueOf(csString.toUpperCase()).getCaseStatus() : null);
        output.put("reopening_reason", rs.getString("REOPENING_REASON"));
      }
    }

    return new NounMetadata(output, PixelDataType.MAP);
  }
}
