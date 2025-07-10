package tqmc.reactors.gtt;

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
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetGttMtfRecordSummaryReactor extends AbstractTQMCReactor {

  private static final String DISCHARGE_KEY = "dischargeDateMonthList";
  // Grab the count of total and incomplete records by MTF
  private static final String GET_GTT_MTF_RECORD_SUMMARY_BASE_QUERY =
      "SELECT "
          + "m.DMIS_ID, "
          // Counts all non null instances of a record
          + "sum(CASE WHEN gwf.STEP_STATUS IS NOT NULL THEN 1 ELSE 0 end) AS total, "
          // Counts all completed instances of a record
          + "sum(CASE WHEN gwf.STEP_STATUS != 'completed' THEN 1 ELSE 0 end) AS incomplete "
          + "FROM MTF m "
          + "LEFT JOIN TQMC_RECORD r ON r.DMIS_ID = m.DMIS_ID "
          + "LEFT JOIN GTT_PHYSICIAN_CASE gpc ON "
          + "r.RECORD_ID = gpc.RECORD_ID "
          + "LEFT JOIN GTT_WORKFLOW gwf ON "
          + "gpc.CASE_ID = gwf.CASE_ID "
          + "AND gwf.IS_LATEST = 1 ";

  private static final String GROUP_CLAUSE = "GROUP BY m.DMIS_ID ";

  private static final String SORT_PARTIAL_CLAUSE =
      "WHERE r.DISCHARGE_DATE IS NULL OR LEFT(r.DISCHARGE_DATE, 7) IN (";

  // The idea is we want to append a Filter on the months which come in

  // This'll be done with a (can be done with a) -
  // make this a subquery, grab out all the stuff we want, then at the end, append a
  // WHERE on the correct group of dates.

  public GetGttMtfRecordSummaryReactor() {
    this.keysToGet = new String[] {DISCHARGE_KEY};
    this.keyRequired = new int[] {0};
  }

  @Override
  public NounMetadata doExecute(Connection con) throws SQLException {

    // Grab the months passed by the front end and place into String array

    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);

    List<String> dischargeMonthList = payload.getDischargeDateMonthList();
    if (dischargeMonthList == null) {
      dischargeMonthList = new ArrayList<String>();
    }

    // Build the full query from it's components
    StringBuilder sb = new StringBuilder(GET_GTT_MTF_RECORD_SUMMARY_BASE_QUERY);
    if (dischargeMonthList.size() > 0) {
      sb.append(SORT_PARTIAL_CLAUSE);
      for (int i = 0; i < dischargeMonthList.size() - 1; i++) {
        sb.append("?, ");
      }
      sb.append("?) ");
    }
    sb.append(GROUP_CLAUSE);

    // Check access Requirements
    if (!(hasProductManagementPermission(TQMCConstants.GTT))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    try (PreparedStatement ps = con.prepareStatement(sb.toString())) {

      int index = 1;
      for (String month : dischargeMonthList) {
        ps.setString(index, month);
        index++;
      }

      ps.execute();
      ResultSet rs = ps.getResultSet();

      int incompleteSum = 0;
      int totalSum = 0;

      // Create Map structure containing number of records and incomplete records
      // as well as number of records and incomplete records per MTF
      Map<String, Map<String, Integer>> mtfMap = new HashMap<>();
      while (rs.next()) {
        String dmisId = rs.getString("DMIS_ID");
        Map<String, Integer> recordsMap = new HashMap<>();

        Integer incomplete = rs.getInt("INCOMPLETE");
        Integer total = rs.getInt("TOTAL");

        incompleteSum += incomplete;
        totalSum += total;

        recordsMap.put("incomplete", incomplete);
        recordsMap.put("total", total);

        mtfMap.put(dmisId, recordsMap);
      }

      Map<String, Integer> sumMap = new HashMap<>();
      sumMap.put("incomplete", incompleteSum);
      sumMap.put("total", totalSum);

      Map<String, Object> result = new HashMap<>();
      result.put("all", sumMap);
      result.put("mtf_map", mtfMap);

      return new NounMetadata(result, PixelDataType.MAP);
    }
  }

  public static class Payload {

    private List<String> dischargeDateMonthList;

    public List<String> getDischargeDateMonthList() {
      return dischargeDateMonthList;
    }
  }
}
