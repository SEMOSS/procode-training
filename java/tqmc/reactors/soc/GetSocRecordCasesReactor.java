package tqmc.reactors.soc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.soc.SocCaseType;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetSocRecordCasesReactor extends AbstractTQMCReactor {

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  public GetSocRecordCasesReactor() {
    this.keysToGet = new String[] {TQMCConstants.RECORD_ID};
    this.keyRequired = new int[] {1};
  }

  String caseQuery =
      "SELECT sc.CASE_ID, sw.STEP_STATUS AS CASE_STATUS, CASE WHEN tu.USER_ID IS NULL THEN NULL ELSE CONCAT(tu.FIRST_NAME, ' ', tu.LAST_NAME) END AS PEER_REVIEWER, sc.ATTESTED_AT AS COMPLETED_AT FROM SOC_CASE sc INNER JOIN SOC_WORKFLOW sw ON sc.CASE_ID = sw.CASE_ID AND sw.IS_LATEST AND sw.CASE_TYPE = ? INNER JOIN SOC_RECORD sr ON sr.RECORD_ID = sc.RECORD_ID AND sc.RECORD_ID = ? LEFT JOIN TQMC_USER tu ON tu.USER_ID = sc.USER_ID WHERE sc.DELETED_AT IS NULL AND sc.SUBMISSION_GUID IS NULL ";

  String nurseQuery =
      "SELECT TOP(1) snr.NURSE_REVIEW_ID, sw.STEP_STATUS AS CASE_STATUS, CASE WHEN tu.USER_ID IS NULL THEN NULL ELSE CONCAT(tu.FIRST_NAME, ' ', tu.LAST_NAME) END AS NURSE_REVIEWER, CASE WHEN sw.STEP_STATUS = 'completed' THEN sw.SMSS_TIMESTAMP ELSE NULL END AS COMPLETED_AT, sr.ALIAS_RECORD_ID, CONCAT(sr.PATIENT_LAST_NAME, ', ', sr.PATIENT_FIRST_NAME) AS PATIENT_NAME FROM SOC_NURSE_REVIEW snr INNER JOIN SOC_WORKFLOW sw ON sw.CASE_ID = snr.NURSE_REVIEW_ID AND sw.IS_LATEST = 1 AND sw.CASE_TYPE = ? INNER JOIN SOC_RECORD sr ON sr.RECORD_ID = snr.RECORD_ID AND snr.RECORD_ID = ? AND snr.SUBMISSION_GUID IS NULL LEFT JOIN TQMC_USER tu ON snr.USER_ID = tu.USER_ID WHERE snr.DELETED_AT IS NULL";

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    if (!hasProductManagementPermission(TQMCConstants.SOC)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    String recordId = this.keyValue.get(TQMCConstants.RECORD_ID);
    Map<String, Object> output = new HashMap<>();

    if (!TQMCHelper.recordIdExists(con, recordId, TQMCConstants.SOC)) {
      throw new TQMCException(ErrorCode.NOT_FOUND);
    }

    try (PreparedStatement psCase = con.prepareStatement(caseQuery);
        PreparedStatement psNurse = con.prepareStatement(nurseQuery)) {
      psCase.setString(1, SocCaseType.PEER_REVIEW.getCaseType());
      psCase.setString(2, recordId);
      psNurse.setString(1, SocCaseType.NURSE_REVIEW.getCaseType());
      psNurse.setString(2, recordId);
      ResultSet rsCase = psCase.executeQuery();
      ResultSet rsNurse = psNurse.executeQuery();
      output.put("record_id", recordId);
      List<Map<String, Object>> caseList = new ArrayList<>();
      Map<String, Object> nurseMap = new HashMap<>();
      String nurseCaseStatus = null;
      if (rsNurse.next()) {
        nurseMap.put("nurse_review_id", rsNurse.getString("NURSE_REVIEW_ID"));
        nurseCaseStatus = rsNurse.getString("CASE_STATUS");
        nurseMap.put("case_status", nurseCaseStatus);
        nurseMap.put("nurse_reviewer_name", rsNurse.getString("NURSE_REVIEWER")); // first last
        nurseMap.put("completed_at", rsNurse.getString("COMPLETED_AT"));

        output.put("alias_record_id", rsNurse.getString("ALIAS_RECORD_ID"));
        output.put("patient_name", rsNurse.getString("PATIENT_NAME"));
      }
      Set<String> caseStatusSet = new HashSet<>();
      while (rsCase.next()) {
        Map<String, Object> caseMap = new HashMap<>();
        caseMap.put("case_id", rsCase.getString("CASE_ID"));
        String caseStatus = rsCase.getString("CASE_STATUS");
        caseMap.put("case_status", caseStatus);
        caseStatusSet.add(caseStatus);
        caseMap.put("peer_reviewer_name", rsCase.getString("PEER_REVIEWER")); // first last
        caseMap.put("completed_at", rsCase.getString("COMPLETED_AT"));
        caseList.add(caseMap);
      }
      output.put("nurse_review", nurseMap);
      output.put("soc_cases", caseList);

      output.put(
          "is_complete",
          nurseCaseStatus != null
              && nurseCaseStatus.equals("completed")
              && caseStatusSet.stream()
                  .allMatch(new HashSet<>(Arrays.asList("completed"))::contains));
    }

    return new NounMetadata(output, PixelDataType.MAP);
  }
}
