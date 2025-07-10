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

public class GetGttAbstractionCasesReactor extends AbstractTQMCReactor {

  private static final String GET_CASES_BASE_QUERY =
      "SELECT "
          + "    subquery.CASE_ID, "
          + "	subquery.RECORD_ID, "
          + "	subquery.DISCHARGE_DATE, "
          + "	subquery.CASE_STATUS, "
          + "	subquery.TIMER_STATUS, "
          + "	subquery.UPDATED_AT, "
          + "	subquery.TIME_IN_QUEUE_DAYS, "
          + "	subquery.OTHER_ABSTRACTION_COMPLETE_VALUE, "
          + "	subquery.ABSTRACTOR_2_USER_NAME, "
          + "	CASE "
          + "		WHEN subquery.OTHER_ABSTRACTION_COMPLETE_VALUE = 0 THEN 1 "
          + "	END AS OTHER_ABSTRACTION_COMPLETE, "
          + "	COUNT(*) OVER() AS TOTAL_ROW_COUNT "
          + "FROM "
          + "	( "
          + "	SELECT "
          + "			gac.CASE_ID AS CASE_ID, "
          + "			gac.RECORD_ID AS RECORD_ID, "
          + "			tr.DISCHARGE_DATE AS DISCHARGE_DATE, "
          + "			gw.SMSS_TIMESTAMP AS UPDATED_AT, "
          + "			DATEDIFF(DAY, gac.CREATED_AT, CURRENT_TIMESTAMP) AS TIME_IN_QUEUE_DAYS, "
          + "			gw.STEP_STATUS AS CASE_STATUS, "
          + "			gw2.STEP_STATUS = 'completed' AS OTHER_ABSTRACTION_COMPLETE_VALUE, "
          + "			CONCAT(tu.FIRST_NAME, ' ', tu.LAST_NAME) AS ABSTRACTOR_2_USER_NAME, "
          + "		( "
          + "		SELECT "
          + "			TOP(1) CASE "
          + "				WHEN (gct.START_TIME IS NULL "
          + "					OR gct.STOP_TIME IS NOT NULL) THEN 'paused' "
          + "				ELSE 'running' "
          + "			END "
          + "		FROM "
          + "			GTT_CASE_TIME gct "
          + "		WHERE "
          + "			gct.CASE_ID = gac.CASE_ID "
          + "		ORDER BY "
          + "			gct.START_TIME DESC) AS TIMER_STATUS "
          + "	FROM "
          + "		GTT_ABSTRACTOR_CASE gac "
          + "LEFT JOIN "
          + "		GTT_ABSTRACTOR_CASE gac2 ON "
          + "		gac.RECORD_ID = gac2.RECORD_ID "
          + "    AND gac.USER_ID <> gac2.USER_ID "
          + "LEFT JOIN "
          + "TQMC_USER tu ON "
          + "gac2.USER_ID = tu.USER_ID "
          + "INNER JOIN GTT_WORKFLOW gw2 ON "
          + "				gw2.CASE_ID = gac2.CASE_ID "
          + "	AND gac2.RECORD_ID = gac.RECORD_ID "
          + "	AND gac2.CASE_ID <> gac.CASE_ID "
          + "	AND gw2.IS_LATEST = 1 "
          + "	AND gw2.STEP_STATUS != 'REASSIGNED' "
          + "INNER JOIN GTT_WORKFLOW gw ON "
          + "		gw.CASE_ID = gac.CASE_ID "
          + "	AND gw.RECIPIENT_USER_ID = gac.USER_ID "
          + "	AND gw.IS_LATEST = 1 "
          + "INNER JOIN TQMC_RECORD tr ON "
          + "		tr.RECORD_ID = gac.RECORD_ID "
          + "WHERE "
          + "		gac.USER_ID = ?) AS subquery ";

  private static final List<String> VALID_FIELDS =
      Lists.newArrayList(
          "RECORD_ID",
          "DISCHARGE_DATE",
          "CASE_STATUS",
          "TIMER_STATUS",
          "TIME_IN_QUEUE_DAYS",
          "OTHER_ABSTRACTION_COMPLETE");

  private static final List<String> DEFAULT_ORDER_FIELDS = Lists.newArrayList("CASE_ID ASC");

  public GetGttAbstractionCasesReactor() {
    this.keysToGet = TQMCConstants.LIST_REACTOR_ARGUMENTS;
  }

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    if (!hasProductPermission(TQMCConstants.GTT)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
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
          c.put("case_status", rs.getString("CASE_STATUS"));
          c.put("other_abstraction_complete", rs.getBoolean("OTHER_ABSTRACTION_COMPLETE_VALUE"));
          c.put("abstractor_2_user_name", rs.getString("ABSTRACTOR_2_USER_NAME"));
          c.put("timer_status", rs.getString("TIMER_STATUS"));
          c.put(
              "updated_at",
              ConversionUtils.getLocalDateTimeStringFromTimestamp(rs.getTimestamp("UPDATED_AT")));
          c.put("time_in_queue_days", rs.getInt("TIME_IN_QUEUE_DAYS"));
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
