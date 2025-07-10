package tqmc.reactors.gtt;

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
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetGttAssignedCasesReactor extends AbstractTQMCReactor {

  public GetGttAssignedCasesReactor() {
    this.keysToGet = new String[] {TQMCConstants.RECORD_ID};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    // Check for access to GTT and Management Lead role
    if (!(hasProductPermission(TQMCConstants.GTT) && hasRole(TQMCConstants.MANAGEMENT_LEAD))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Map<String, Object> r = new HashMap<>();

    String recordId = keyValue.get(TQMCConstants.RECORD_ID);
    String abs1CaseId = null;
    String abs2CaseId = null;
    String consensusCaseId = null;
    String physicianCaseId = null;

    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT "
                + "CONCAT(tu1.FIRST_NAME, ' ', tu1.LAST_NAME) AS ABS_ONE_NAME, "
                + "CONCAT(tu2.FIRST_NAME, ' ', tu2.LAST_NAME) AS ABS_TWO_NAME, "
                + "CONCAT(tu3.FIRST_NAME, ' ', tu3.LAST_NAME) AS PHYSICIAN_NAME, "
                + "tu1.EMAIL AS ABS_ONE_EMAIL, "
                + "tu2.EMAIL AS ABS_TWO_EMAIL, "
                + "tu3.EMAIL AS PHYSICIAN_EMAIL, "
                + "gcc.ABS_1_CASE_ID AS ABSTRACTOR_ONE_CASE_ID, "
                + "gcc.ABS_2_CASE_ID AS ABSTRACTOR_TWO_CASE_ID, "
                + "gcc.CASE_ID AS CONSENSUS_CASE_ID, "
                + "gpc.CASE_ID AS PHYSICIAN_CASE_ID "
                + "FROM GTT_PHYSICIAN_CASE gpc "
                + "INNER JOIN GTT_CONSENSUS_CASE gcc ON gcc.CASE_ID = gpc.CONSENSUS_CASE_ID "
                + "LEFT OUTER JOIN GTT_ABSTRACTOR_CASE gac1 ON gac1.CASE_ID = gcc.ABS_1_CASE_ID "
                + "LEFT OUTER JOIN GTT_ABSTRACTOR_CASE gac2 ON gac2.CASE_ID = gcc.ABS_2_CASE_ID "
                + "LEFT OUTER JOIN TQMC_USER tu1 ON tu1.USER_ID = gac1.USER_ID "
                + "LEFT OUTER JOIN TQMC_USER tu2 ON tu2.USER_ID = gac2.USER_ID "
                + "LEFT OUTER JOIN TQMC_USER tu3 ON tu3.USER_ID = gpc.PHYSICIAN_USER_ID "
                + "WHERE gpc.RECORD_ID = ?")) {
      ps.setString(1, recordId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          Map<String, Object> userNames = new HashMap<>();
          userNames.put("abstractor_one_name", TQMCHelper.getUnassignedString(rs, "ABS_ONE_NAME"));
          userNames.put("abstractor_two_name", TQMCHelper.getUnassignedString(rs, "ABS_TWO_NAME"));
          userNames.put("physician_name", TQMCHelper.getUnassignedString(rs, "PHYSICIAN_NAME"));
          userNames.put(
              "abstractor_one_email", TQMCHelper.getUnassignedString(rs, "ABS_ONE_EMAIL"));
          userNames.put(
              "abstractor_two_email", TQMCHelper.getUnassignedString(rs, "ABS_TWO_EMAIL"));
          userNames.put("physician_email", TQMCHelper.getUnassignedString(rs, "PHYSICIAN_EMAIL"));

          Map<String, Object> cases = new HashMap<>();

          abs1CaseId = rs.getString("ABSTRACTOR_ONE_CASE_ID");
          if (abs1CaseId == null || abs1CaseId.trim().isEmpty()) {
            userNames.put("abstractor_one_name", null);
            cases.put("abstractor_one_case", null);
          } else {
            Map<String, Object> abs1 = TQMCHelper.getGttAbstractionCaseMap(con, null, abs1CaseId);
            if (abs1.isEmpty()) {
              userNames.put("abstractor_one_name", null);
              cases.put("abstractor_one_case", null);
            } else {
              cases.put("abstractor_one_case", abs1);
            }
          }

          abs2CaseId = rs.getString("ABSTRACTOR_TWO_CASE_ID");
          if (abs2CaseId == null || abs2CaseId.trim().isEmpty()) {
            userNames.put("abstractor_two_name", null);
            cases.put("abstractor_two_case", null);
          } else {
            Map<String, Object> abs2 = TQMCHelper.getGttAbstractionCaseMap(con, null, abs2CaseId);
            if (abs2.isEmpty()) {
              userNames.put("abstractor_two_name", null);
              cases.put("abstractor_two_case", null);
            } else {
              cases.put("abstractor_two_case", abs2);
            }
          }

          consensusCaseId = rs.getString("CONSENSUS_CASE_ID");
          if (consensusCaseId == null || consensusCaseId.trim().isEmpty()) {
            cases.put("consensus_case", null);
          } else {
            Map<String, Object> consensusCase =
                TQMCHelper.getGttConsensusCaseMap(con, null, consensusCaseId);
            cases.put("consensus_case", consensusCase.isEmpty() ? null : consensusCase);
          }

          physicianCaseId = rs.getString("PHYSICIAN_CASE_ID");
          if (physicianCaseId == null || physicianCaseId.trim().isEmpty()) {
            userNames.put("physician_name", null);
            cases.put("physician_case", null);
          } else {
            Map<String, Object> physicianCase =
                TQMCHelper.getGttPhysicianCaseMap(con, null, physicianCaseId);
            if (physicianCase.isEmpty()) {
              userNames.put("physician_name", null);
              cases.put("physician_case", null);
            } else {
              cases.put("physician_case", physicianCase);
            }
          }

          r.put("user_names", userNames);
          r.put("cases", cases);
        }
      }
    }

    if (r.isEmpty()) {
      throw new TQMCException(ErrorCode.NOT_FOUND);
    }

    return new NounMetadata(r, PixelDataType.MAP);
  }
}
