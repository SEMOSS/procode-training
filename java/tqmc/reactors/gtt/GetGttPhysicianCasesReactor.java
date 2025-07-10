package tqmc.reactors.gtt;

import com.google.common.collect.Lists;
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
import tqmc.domain.base.GetListPayload;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetGttPhysicianCasesReactor extends AbstractTQMCReactor {

  private static final String GET_CASES_BASE_QUERY =
      "SELECT "
          + "CASE_ID, "
          + "RECORD_ID, "
          + "DISCHARGE_DATE, "
          + "UPDATED_AT, "
          + "TIME_IN_QUEUE_DAYS, "
          + "CASE_STATUS, "
          + "ADVERSE_EVENT_COUNT, "
          + "COUNT(*) OVER() AS TOTAL_ROW_COUNT "
          + "FROM "
          + "( "
          + "SELECT "
          + "gpc.CASE_ID AS CASE_ID, "
          + "gpc.RECORD_ID AS RECORD_ID, "
          + "tr.DISCHARGE_DATE AS DISCHARGE_DATE, "
          + "gw.SMSS_TIMESTAMP AS UPDATED_AT, "
          + "DATEDIFF(DAY, gpc.CREATED_AT, CURRENT_TIMESTAMP) AS TIME_IN_QUEUE_DAYS, "
          + "gw.STEP_STATUS AS CASE_STATUS, "
          + "( "
          + "SELECT "
          + "COUNT(*) "
          + "FROM "
          + "GTT_RECORD_CASE_EVENT grce "
          + "WHERE "
          + "grce.CASE_ID = gpc.CASE_ID) AS ADVERSE_EVENT_COUNT "
          + "FROM "
          + "GTT_PHYSICIAN_CASE gpc "
          + "INNER JOIN GTT_WORKFLOW gw ON "
          + "gw.CASE_ID = gpc.CASE_ID "
          + "AND gw.RECIPIENT_USER_ID = gpc.PHYSICIAN_USER_ID "
          + "AND gw.RECIPIENT_STAGE ILIKE 'PHYSICIAN REVIEW' "
          + "AND gw.IS_LATEST = 1 "
          + "INNER JOIN GTT_WORKFLOW gwc ON "
          + "gwc.CASE_ID = gpc.CONSENSUS_CASE_ID "
          + "AND gwc.IS_LATEST = 1 "
          + "AND gwc.STEP_STATUS = 'completed' "
          + "INNER JOIN TQMC_RECORD tr ON "
          + "tr.RECORD_ID = gpc.RECORD_ID "
          + "WHERE "
          + "gpc.PHYSICIAN_USER_ID = ? ) ";

  private static final List<String> VALID_SORT_FIELDS =
      Lists.newArrayList(
          "RECORD_ID",
          "DISCHARGE_DATE",
          "CASE_STATUS",
          "TIME_IN_QUEUE_DAYS",
          "ADVERSE_EVENT_COUNT");

  private static final List<String> VALID_FILTER_FIELDS =
      Lists.newArrayList("RECORD_ID", "DISCHARGE_DATE", "CASE_STATUS", "TIME_IN_QUEUE_DAYS");

  public GetGttPhysicianCasesReactor() {
    this.keysToGet = TQMCConstants.LIST_REACTOR_ARGUMENTS;
  }

  private static final List<String> DEFAULT_ORDER_FIELDS = Lists.newArrayList("CASE_ID ASC");

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    if (!hasProductPermission(TQMCConstants.GTT)) {
      throw new TQMCException(
          ErrorCode.FORBIDDEN, "User is unauthorized to perform this operation");
    }

    GetListPayload payload =
        TQMCHelper.getListPayloadObject(
            this.getNounStore(), VALID_SORT_FIELDS, VALID_FILTER_FIELDS);

    List<String> arguments = new ArrayList<>();

    String query =
        TQMCHelper.getListQuery(
            userId, payload, GET_CASES_BASE_QUERY, arguments, DEFAULT_ORDER_FIELDS);

    Map<String, Object> results = new HashMap<>();
    int totalCount = 0;
    List<Map<String, Object>> cases = new ArrayList<>();
    try (PreparedStatement ps = con.prepareStatement(query)) {
      int parameterIndex = 1;
      for (String s : arguments) {
        ps.setString(parameterIndex++, s);
      }
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          Map<String, Object> c = new HashMap<>();
          c.put("case_id", rs.getString("CASE_ID"));
          c.put("record_id", rs.getString("RECORD_ID"));
          c.put(
              "discharge_date",
              ConversionUtils.getLocalDateStringFromDate(rs.getDate("DISCHARGE_DATE")));
          c.put(
              "updated_at",
              ConversionUtils.getLocalDateTimeStringFromTimestamp(rs.getTimestamp("UPDATED_AT")));
          c.put("time_in_queue_days", rs.getInt("TIME_IN_QUEUE_DAYS"));
          c.put("case_status", rs.getString("CASE_STATUS"));
          c.put("adverse_event_count", rs.getInt("ADVERSE_EVENT_COUNT"));
          if (totalCount == 0) {
            totalCount = rs.getInt("TOTAL_ROW_COUNT");
          }
          cases.add(c);
        }
      }
    }

    results.put("total_row_count", totalCount);
    results.put("cases", cases);
    return new NounMetadata(results, PixelDataType.MAP);
  }
}
