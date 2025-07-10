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

public class GetGttConsensusCasesReactor extends AbstractTQMCReactor {

  private static final String GET_CASES_BASE_QUERY =
      "SELECT CASE_ID, RECORD_ID, DISCHARGE_DATE, UPDATED_AT, TIME_IN_QUEUE_DAYS, CASE_STATUS, "
          + "PAIRED_ABSTRACTION_STATUS, ABSTRACTOR_ID, ABSTRACTOR_EMAIL, PAIRED_ABSTRACTOR_NAME, PAIRED_ABSTRACTOR_QUERY, CONSENSUS_CASE_STATUS_QUERY, "
          + "CASE WHEN IRR_ROW_COUNT = 0 THEN NULL ELSE CAST(IRR_MATCH_COUNT AS DOUBLE)/IRR_ROW_COUNT END AS IRR, "
          + "COUNT(*) OVER() AS TOTAL_ROW_COUNT FROM ( "
          + "SELECT gcc.CASE_ID AS CASE_ID, gcc.RECORD_ID AS RECORD_ID, tr.DISCHARGE_DATE AS DISCHARGE_DATE, "
          + "gw.SMSS_TIMESTAMP AS UPDATED_AT, DATEDIFF(DAY, gcc.CREATED_AT, CURRENT_TIMESTAMP) AS TIME_IN_QUEUE_DAYS, "
          + "gw.STEP_STATUS AS CASE_STATUS, tu.USER_ID AS ABSTRACTOR_ID, tu.EMAIL AS ABSTRACTOR_EMAIL, "
          + "CONCAT(tu.FIRST_NAME, ' ', tu.LAST_NAME) AS PAIRED_ABSTRACTOR_NAME, "
          + "CONCAT(tu.LAST_NAME, ', ', tu.FIRST_NAME, ' ', tu.LAST_NAME, ' ', tu.FIRST_NAME) as PAIRED_ABSTRACTOR_QUERY, "
          + "gw2.STEP_STATUS AS PAIRED_ABSTRACTION_STATUS, "
          + "SUM(CASE WHEN COALESCE(gim.IS_MATCH, 0) = 1 THEN 1 ELSE 0 END) AS IRR_MATCH_COUNT, COUNT(gim.IRR_MATCH_ID) AS IRR_ROW_COUNT, "
          + "CASE "
          + "WHEN gw2.STEP_STATUS = 'completed' AND gw.STEP_STATUS = 'in_progress' THEN 'in_progress' "
          + "WHEN gw2.STEP_STATUS = 'completed' AND gw.STEP_STATUS = 'not_started' THEN 'not_started' "
          + "WHEN gw2.STEP_STATUS != 'completed' AND gw.STEP_STATUS = 'not_started' THEN 'waiting_on_abstractor_2' "
          + "END AS CONSENSUS_CASE_STATUS_QUERY, "
          + "FROM GTT_ABSTRACTOR_CASE gac1 "
          + "INNER JOIN GTT_WORKFLOW gw1 ON gw1.CASE_ID = gac1.CASE_ID AND gw1.RECIPIENT_USER_ID = gac1.USER_ID "
          + "AND gw1.IS_LATEST = 1 AND gw1.STEP_STATUS = 'completed' "
          + "INNER JOIN GTT_CONSENSUS_CASE gcc ON gac1.CASE_ID IN (gcc.ABS_1_CASE_ID, gcc.ABS_2_CASE_ID) "
          + "INNER JOIN GTT_WORKFLOW gw ON gw.CASE_ID = gcc.CASE_ID AND gw.IS_LATEST = 1 "
          + "INNER JOIN GTT_WORKFLOW gw2 ON gw2.CASE_ID IN (gcc.ABS_1_CASE_ID, gcc.ABS_2_CASE_ID) AND gw2.CASE_ID <> gac1.CASE_ID "
          + "AND gw2.IS_LATEST = 1 "
          + "LEFT OUTER JOIN TQMC_USER tu ON tu.USER_ID = gw2.RECIPIENT_USER_ID AND tu.IS_ACTIVE = 1 "
          + "INNER JOIN TQMC_RECORD tr ON tr.RECORD_ID = gac1.RECORD_ID "
          + "LEFT OUTER JOIN GTT_IRR_MATCH gim ON gim.CONSENSUS_CASE_ID = gcc.CASE_ID "
          + "WHERE gac1.USER_ID = ? "
          + "GROUP BY gcc.CASE_ID, gcc.RECORD_ID, tr.DISCHARGE_DATE, gw.SMSS_TIMESTAMP, gcc.CREATED_AT, gw.STEP_STATUS, "
          + "tu.USER_ID, tu.EMAIL, tu.FIRST_NAME, tu.LAST_NAME, gw2.STEP_STATUS )";

  private static final List<String> VALID_FIELDS =
      Lists.newArrayList(
          "RECORD_ID",
          "DISCHARGE_DATE",
          "CASE_STATUS",
          "PAIRED_ABSTRACTOR_QUERY",
          "ABSTRACTOR_EMAIL",
          "TIME_IN_QUEUE_DAYS",
          "CONSENSUS_CASE_STATUS_QUERY");

  public GetGttConsensusCasesReactor() {
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
        TQMCHelper.getListPayloadObject(this.getNounStore(), VALID_FIELDS, VALID_FIELDS);

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
          c.put("paired_abstraction_status", rs.getString("PAIRED_ABSTRACTION_STATUS"));

          Map<String, Object> pa = new HashMap<>();
          pa.put("user_id", rs.getString("ABSTRACTOR_ID"));
          pa.put("user_email", rs.getString("ABSTRACTOR_EMAIL"));
          pa.put("user_name", rs.getString("PAIRED_ABSTRACTOR_NAME"));
          c.put("paired_abstractor_query", rs.getString("PAIRED_ABSTRACTOR_QUERY"));
          c.put("paired_abstractor", pa);

          double doubleValue = rs.getDouble("IRR");
          if (!rs.wasNull()) {
            c.put("irr", doubleValue);
          }

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
