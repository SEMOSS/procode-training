package tqmc.reactors.soc;

import com.fasterxml.jackson.core.type.TypeReference;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.mapper.CustomMapper;
import tqmc.domain.soc.SocCaseProviderEvaluation;
import tqmc.domain.soc.SocWorkflow;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetSocCaseReactor extends AbstractTQMCReactor {

  private static final String SOC_CASE_QUERY =
      "SELECT sc.RECORD_ID, sc.CASE_ID, user.LAST_NAME, user.FIRST_NAME, user.EMAIL, "
          + "sc.ATTESTATION_SIGNATURE, sc.ATTESTED_AT, sc.DUE_DATE_REVIEW, sc.DUE_DATE_DHA, "
          + "sc.SPECIALTY_ID, "
          + "snr.NURSE_REVIEW_ID, "
          + "eval.CASE_SYSTEM_EVALUATION_ID, eval.REFERENCES, eval.SYSTEM_ISSUE, "
          + "eval.SYSTEM_ISSUE_RATIONALE, eval.SYSTEM_ISSUE_JUSTIFICATION, "
          + "sr.ALIAS_RECORD_ID AS ALIAS_RECORD_ID, "
          + "CONCAT(sr.PATIENT_LAST_NAME, ', ', sr.PATIENT_FIRST_NAME) AS PATIENT_NAME, "
          + "sc.USER_ID, sc.UPDATED_AT, sw.STEP_STATUS, sc.SUBMISSION_GUID "
          + "FROM SOC_CASE sc "
          + "LEFT JOIN TQMC_USER user ON sc.USER_ID = user.USER_ID "
          + "LEFT JOIN SOC_RECORD sr ON sr.RECORD_ID = sc.RECORD_ID "
          + "LEFT JOIN SOC_NURSE_REVIEW snr ON sr.RECORD_ID = snr.RECORD_ID "
          + "LEFT JOIN SOC_CASE_SYSTEM_EVALUATION eval ON sc.CASE_ID = eval.CASE_ID "
          + "LEFT JOIN SOC_WORKFLOW sw ON sc.CASE_ID = sw.CASE_ID "
          + "WHERE sc.CASE_ID = ? AND sc.DELETED_AT IS NULL AND snr.DELETED_AT IS NULL ";

  public GetSocCaseReactor() {
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

    // permissions only for reviewer on case
    if (!hasProductPermission(TQMCConstants.SOC)
        || !TQMCHelper.hasCaseAccess(con, userId, caseId, TQMCConstants.SOC)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Map<String, Object> result = new HashMap<String, Object>();

    // This query gathers everything except the provider evaluations as there is an one to many
    // relationship
    // TOP in query might be redundant since CASE_ID is a PK

    try (PreparedStatement ps = con.prepareStatement(SOC_CASE_QUERY)) {
      ps.setString(1, caseId);

      if (ps.execute()) {

        ResultSet rs = ps.getResultSet();
        Map<String, Object> eval;

        if (rs.next()) {
          result.put("case_id", caseId);
          result.put("record_id", rs.getString("RECORD_ID"));
          // Last, First name
          String lastName = rs.getString("LAST_NAME");
          String firstName = rs.getString("FIRST_NAME");
          String name = lastName + ", " + firstName;
          if (firstName == null) {
            name = null;
          }
          result.put("user_name", name);
          result.put("user_email", rs.getString("EMAIL"));
          result.put("attestation_signature", rs.getString("ATTESTATION_SIGNATURE"));
          result.put("attested_at", rs.getString("ATTESTED_AT"));
          String dueDateReview =
              ConversionUtils.getLocalDateStringFromDate(rs.getDate("DUE_DATE_REVIEW"));
          String dueDateDHA =
              ConversionUtils.getLocalDateStringFromDate(rs.getDate("DUE_DATE_DHA"));
          result.put("due_date_review", dueDateReview);
          result.put("due_date_dha", dueDateDHA);
          result.put("specialty_id", rs.getString("SPECIALTY_ID"));
          // Workflow related variables
          SocWorkflow wf = TQMCHelper.getLatestSocWorkflow(con, caseId);
          result.put("case_status", wf.getStepStatus());
          result.put("case_type", wf.getCaseType());
          result.put("reopening_reason", wf.getReopenReason());

          String nurseReviewId = rs.getString("NURSE_REVIEW_ID");
          SocWorkflow nrwf = TQMCHelper.getLatestSocWorkflow(con, nurseReviewId);
          result.put("nurse_review_id", nurseReviewId);
          result.put("nurse_review_case_status", nrwf.getStepStatus());

          // System Evaluation map
          eval = new HashMap<String, Object>();
          eval.put("case_system_evaluation_id", rs.getString("CASE_SYSTEM_EVALUATION_ID"));
          eval.put("references", rs.getString("REFERENCES"));
          eval.put("system_issue", rs.getString("SYSTEM_ISSUE"));
          eval.put("system_issue_rationale", rs.getString("SYSTEM_ISSUE_RATIONALE"));
          eval.put("system_issue_justification", rs.getString("SYSTEM_ISSUE_JUSTIFICATION"));
          result.put("alias_record_id", rs.getString("ALIAS_RECORD_ID"));
          result.put("patient_name", rs.getString("PATIENT_NAME"));
          result.put("user_id", rs.getString("USER_ID"));
          result.put("updated_at", rs.getString("UPDATED_AT"));
          // Separate query to bulk collect all provider evaluations
          Map<String, SocCaseProviderEvaluation> provEvals = new HashMap<>();
          for (SocCaseProviderEvaluation row : TQMCHelper.getSocProviderEvaluations(con, caseId)) {
            provEvals.put(row.getCaseProviderEvaluationId(), row);
          }
          // Converts ProviderEvaluation to JSON
          result.put(
              "provider_evaluations",
              CustomMapper.MAPPER.convertValue(
                  provEvals, new TypeReference<Map<String, Object>>() {}));
          result.put("system_evaluation", eval);
        } else {
          throw new TQMCException(ErrorCode.NOT_FOUND);
        }
      }
    }
    return new NounMetadata(result, PixelDataType.MAP);
  }
}
