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
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetSocRecordVersionsReactor extends AbstractTQMCReactor {

  public GetSocRecordVersionsReactor() {
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
    if (!(hasProductPermission(TQMCConstants.SOC)
        && (hasRole(TQMCConstants.MANAGEMENT_LEAD) || hasRole(TQMCConstants.CONTRACTING_LEAD)))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Map<String, Object> output = new HashMap<>();
    Map<String, Object> socCases = new HashMap<>();
    output.put("soc_cases", socCases);

    try (PreparedStatement ps = con.prepareStatement(TQMCHelper.getSocCaseVersionsQuery())) {
      ps.setString(1, queriedRecord);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          output.putIfAbsent("record_id", rs.getString("RECORD_ID"));
          output.putIfAbsent("alias_record_id", rs.getString("ALIAS_RECORD_ID"));
          output.putIfAbsent("patient_name", rs.getString("PATIENT_NAME"));

          if (!socCases.containsKey(rs.getString("BASE_CASE_ID"))) {
            socCases.putIfAbsent(rs.getString("BASE_CASE_ID"), new HashMap<String, Object>());
          }
          Map<String, Object> socCase =
              (Map<String, Object>) socCases.get(rs.getString("BASE_CASE_ID"));

          if (rs.getString("SUBMISSION_GUID") == null) {
            socCase.putIfAbsent("case_id", rs.getString("BASE_CASE_ID"));
            socCase.putIfAbsent("current_case_status", rs.getString("STEP_STATUS"));
          }

          Map<String, Object> queriedVersion = new HashMap<>();
          queriedVersion.put("case_id", rs.getString("CASE_ID"));
          queriedVersion.put("updated_at", rs.getString("UPDATED_AT"));
          queriedVersion.put("case_status", rs.getString("STEP_STATUS"));
          queriedVersion.put("due_date_dha", rs.getString("DUE_DATE_DHA"));
          queriedVersion.put("due_date_review", rs.getString("DUE_DATE_REVIEW"));
          queriedVersion.put("user_id", rs.getString("USER_ID"));
          queriedVersion.put("specialty_id", rs.getString("SPECIALTY_ID"));
          queriedVersion.put("attestation_signature", rs.getString("ATTESTATION_SIGNATURE"));
          queriedVersion.put("attested_at", rs.getString("ATTESTED_AT"));
          queriedVersion.put("active_user_id", rs.getString("ACTIVE_USER_ID"));
          queriedVersion.put("reopening_reason", rs.getString("REOPENING_REASON"));

          // System Evaluation map
          Map<String, String> eval = new HashMap<String, String>();
          eval.put("case_system_evaluation_id", rs.getString("CASE_SYSTEM_EVALUATION_ID"));
          eval.put("references", rs.getString("REFERENCES"));
          eval.put("system_issue", rs.getString("SYSTEM_ISSUE"));
          eval.put("system_issue_rationale", rs.getString("SYSTEM_ISSUE_RATIONALE"));
          eval.put("system_issue_justification", rs.getString("SYSTEM_ISSUE_JUSTIFICATION"));

          queriedVersion.put("system_evaluation", eval);

          Map<String, SocCaseProviderEvaluation> provEvals = new HashMap<>();
          for (SocCaseProviderEvaluation row :
              TQMCHelper.getSocProviderEvaluations(con, rs.getString("CASE_ID"))) {
            provEvals.put(row.getCaseProviderEvaluationId(), row);
          }
          // Converts ProviderEvaluation to JSON
          queriedVersion.put(
              "provider_evaluations",
              CustomMapper.MAPPER.convertValue(
                  provEvals, new TypeReference<Map<String, Object>>() {}));
          if (!socCase.containsKey("version_map")) {
            socCase.put("version_map", new HashMap<String, Object>());
          }
          Map<String, Object> versionMap = (Map<String, Object>) socCase.get("version_map");
          versionMap.put((String) queriedVersion.get("updated_at"), queriedVersion);
        }
      }
    }

    try (PreparedStatement ps = con.prepareStatement(TQMCHelper.getSocNurseReviewVersionsQuery())) {
      ps.setString(1, queriedRecord);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        Map<String, Object> nrMap = new HashMap<>();
        while (rs.next()) {

          if (rs.getString("SUBMISSION_GUID") == null) {
            nrMap.putIfAbsent("nurse_review_id", rs.getString("BASE_NURSE_REVIEW_ID"));
            nrMap.putIfAbsent("current_case_status", rs.getString("CASE_STATUS"));
          }

          Map<String, Object> queriedVersion = new HashMap<>();
          queriedVersion.put("updated_at", rs.getString("UPDATED_AT"));
          queriedVersion.put("case_status", rs.getString("CASE_STATUS"));
          queriedVersion.put("due_date_dha", rs.getString("DUE_DATE_DHA"));
          queriedVersion.put("due_date_review", rs.getString("DUE_DATE_REVIEW"));
          queriedVersion.put("user_id", rs.getString("USER_ID"));
          queriedVersion.put("period_of_care_end", rs.getString("POC_END"));
          queriedVersion.put("period_of_care_start", rs.getString("POC_START"));
          queriedVersion.put("injury", rs.getString("INJURY"));
          queriedVersion.put("summary_of_facts", rs.getString("SUMMARY_OF_FACTS"));
          queriedVersion.put("diagnoses", rs.getString("DIAGNOSES"));
          queriedVersion.put("allegations", rs.getString("ALLEGATIONS"));
          queriedVersion.put("nurse_review_id", rs.getString("NURSE_REVIEW_ID"));
          queriedVersion.put("active_user_id", rs.getString("ACTIVE_USER_ID"));
          queriedVersion.put("reopening_reason", rs.getString("REOPENING_REASON"));

          if (!nrMap.containsKey("version_map")) {
            nrMap.put("version_map", new HashMap<String, Object>());
          }

          Map<String, Object> versionMap = (Map<String, Object>) nrMap.get("version_map");
          versionMap.put((String) queriedVersion.get("updated_at"), queriedVersion);
          // nrMap.put("version_map", versionMap);
        }
        output.put("soc_nurse_review", nrMap);
      }
    }

    return new NounMetadata(output, PixelDataType.BOOLEAN);
  }
}
