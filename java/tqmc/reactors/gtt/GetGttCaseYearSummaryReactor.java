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

public class GetGttCaseYearSummaryReactor extends AbstractTQMCReactor {

  private static final String DMIS_ID = "dmisId";

  private static final String DMIS_EXISTS_QUERY = "SELECT DMIS_ID FROM MTF WHERE DMIS_ID = ?;";

  public GetGttCaseYearSummaryReactor() {
    this.keysToGet = new String[] {DMIS_ID};
    this.keyRequired = new int[] {0};
  }

  @Override
  public NounMetadata doExecute(Connection con) throws SQLException {
    // Get the active user info
    String userId = this.userId;
    String dmisId = this.keyValue.get(DMIS_ID);

    // Leaving the below commented code because we may re-visit later

    // Get the current year start and end dates
    // LocalDate now = ConversionUtils.getUTCFromLocalNow().toLocalDate();
    // LocalDate startOfYear = now.withDayOfYear(1);
    // LocalDate endOfYear = startOfYear.plusYears(1);
    // DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    // String startDateStr = startOfYear.atStartOfDay(ZoneId.systemDefault()).format(formatter);
    // String endDateStr = endOfYear.atStartOfDay(ZoneId.systemDefault()).format(formatter);

    // Initialize a map to store the counts of different case statuses
    Map<String, Integer> caseSummary = new HashMap<>();
    caseSummary.put("not_started", 0);
    caseSummary.put("in_progress", 0);
    caseSummary.put("completed", 0);
    caseSummary.put("consensus_not_started", 0);
    caseSummary.put("consensus_in_progress", 0);

    // Initialize the conditional strings
    String dmisIdClause = "";
    if (dmisId != null) {
      dmisIdClause = "AND r.DMIS_ID = ? ";
    }

    if (hasRole(TQMCConstants.ABSTRACTOR) || hasProductManagementPermission(TQMCConstants.GTT)) {
      try (PreparedStatement abstractor =
              con.prepareStatement(
                  "SELECT COUNT(gw.CASE_ID), gw.STEP_STATUS "
                      + "FROM GTT_WORKFLOW gw "
                      + "INNER JOIN GTT_ABSTRACTOR_CASE gac "
                      + "ON gw.CASE_ID = gac.CASE_ID "
                      + "AND gw.IS_LATEST = 1 "
                      + "INNER JOIN TQMC_RECORD r "
                      + "ON gac.RECORD_ID = r.RECORD_ID "
                      + "WHERE gw.RECIPIENT_USER_ID = ? "
                      + "AND gw.CASE_TYPE = '"
                      + TQMCConstants.GTT_CASE_TYPE_ABSTRACTOR
                      + "' "
                      + dmisIdClause
                      + "GROUP BY gw.STEP_STATUS");
          PreparedStatement consensus =
              con.prepareStatement(
                  "SELECT COUNT(gw.CASE_ID), gw.STEP_STATUS "
                      + "FROM GTT_WORKFLOW gw "
                      + "INNER JOIN GTT_CONSENSUS_CASE gcc "
                      + "ON gcc.CASE_ID = gw.CASE_ID "
                      + "INNER JOIN GTT_WORKFLOW gw1 ON gw1.CASE_ID IN (gcc.ABS_1_CASE_ID, gcc.ABS_2_CASE_ID) "
                      + "AND gw1.IS_LATEST = 1 "
                      + "AND gw1.RECIPIENT_USER_ID = ? "
                      + "AND gw1.STEP_STATUS = '"
                      + TQMCConstants.CASE_STEP_STATUS_COMPLETED
                      + "' "
                      + "INNER JOIN TQMC_RECORD r "
                      + "ON gcc.RECORD_ID = r.RECORD_ID "
                      + "WHERE gw.IS_LATEST = 1 "
                      + "AND gw.CASE_TYPE = '"
                      + TQMCConstants.GTT_CASE_TYPE_CONSENSUS
                      + "' "
                      + dmisIdClause
                      + "GROUP BY gw.STEP_STATUS"); ) {
        // get results for query 1 and 2

        abstractor.setString(1, userId);
        consensus.setString(1, userId);

        if (dmisId != null) {

          PreparedStatement ps = con.prepareStatement(DMIS_EXISTS_QUERY);
          ps.setString(1, dmisId);
          ps.execute();
          ResultSet dmisRs = ps.getResultSet();

          // checks if empty
          if (!dmisRs.isBeforeFirst()) {
            throw new TQMCException(ErrorCode.NOT_FOUND);
          }

          consensus.setString(2, dmisId);
          abstractor.setString(2, dmisId);
        }

        ResultSet abstractorResults = abstractor.executeQuery();
        ResultSet consensusResults = consensus.executeQuery();
        if (abstractorResults != null) {
          while (abstractorResults.next()) {
            int count = abstractorResults.getInt(1);
            String stepStatus = abstractorResults.getString(2).toLowerCase();
            if (stepStatus.equalsIgnoreCase("not_started")) {
              caseSummary.put("not_started", caseSummary.get("not_started") + count);
            } else if (stepStatus.equalsIgnoreCase("in_progress")) {
              caseSummary.put("in_progress", caseSummary.get("in_progress") + count);
            }
          }
        }
        if (consensusResults != null) {
          while (consensusResults.next()) {
            int count = consensusResults.getInt(1);
            String stepStatus = consensusResults.getString(2).toLowerCase();
            if (stepStatus.equalsIgnoreCase("not_started")) {
              caseSummary.put(
                  "consensus_not_started", caseSummary.get("consensus_not_started") + count);
            } else if (stepStatus.equalsIgnoreCase("in_progress")) {
              caseSummary.put(
                  "consensus_in_progress", caseSummary.get("consensus_in_progress") + count);
            } else {
              caseSummary.put("completed", caseSummary.get("completed") + count);
            }
          }
        }
      }
    } else if (hasRole(TQMCConstants.PHYSICIAN)) {

      try (PreparedStatement physician =
          con.prepareStatement(
              "SELECT COUNT(gw.CASE_ID), gw.STEP_STATUS "
                  + "FROM GTT_WORKFLOW gw "
                  + "INNER JOIN GTT_PHYSICIAN_CASE gpc "
                  + "ON gpc.CASE_ID = gw.CASE_ID "
                  + "INNER JOIN GTT_WORKFLOW gwc "
                  + "ON gwc.CASE_ID = gpc.CONSENSUS_CASE_ID "
                  + "AND gwc.IS_LATEST = 1 "
                  + "AND gwc.STEP_STATUS = '"
                  + TQMCConstants.CASE_STEP_STATUS_COMPLETED
                  + "' "
                  + "INNER JOIN TQMC_RECORD r "
                  + "ON gpc.RECORD_ID = r.RECORD_ID "
                  + "WHERE gw.RECIPIENT_USER_ID = ? "
                  + "AND gw.IS_LATEST = 1 "
                  + "AND gw.CASE_TYPE = '"
                  + TQMCConstants.GTT_CASE_TYPE_PHYSICIAN
                  + "' "
                  + dmisIdClause
                  + "GROUP BY gw.STEP_STATUS"); ) {
        physician.setString(1, userId);

        ResultSet physicianResults = physician.executeQuery();
        if (physicianResults != null) {
          while (physicianResults.next()) {
            int count = physicianResults.getInt(1);
            String stepStatus = physicianResults.getString(2).toLowerCase();
            if (stepStatus.equalsIgnoreCase("not_started")) {
              caseSummary.put("not_started", caseSummary.get("not_started") + count);
            } else if (stepStatus.equalsIgnoreCase("in_progress")) {
              caseSummary.put("in_progress", caseSummary.get("in_progress") + count);
            } else {
              caseSummary.put("completed", caseSummary.get("completed") + count);
            }
          }
        }
      }
    } else {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }
    // Return the NounMetadata object with the case counts
    return new NounMetadata(caseSummary, PixelDataType.MAP);
  }
}
