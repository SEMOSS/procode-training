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

public class GetGttManagementCasesReactor extends AbstractTQMCReactor {

  private static final String GET_GTT_MANAGEMENT_CASES_BASE_QUERY =
      "WITH \r\n"
          + "ASSIGNED_CASE_TIMESTAMPS AS (\r\n"
          + "SELECT\r\n"
          + "	CASE_ID,\r\n"
          + "	MIN(smss_timestamp) AS min_smss_timestamp\r\n"
          + "FROM\r\n"
          + "	GTT_WORKFLOW\r\n"
          + "GROUP BY\r\n"
          + "	CASE_ID\r\n"
          + "),\r\n"
          + "IRR_SCORES AS (\r\n"
          + "SELECT\r\n"
          + "	gim.CONSENSUS_CASE_ID,\r\n"
          + "	CASE\r\n"
          + "		WHEN COUNT(gim.IRR_MATCH_ID) = 0 THEN NULL\r\n"
          + "		ELSE CAST(SUM(CASE WHEN COALESCE(gim.IS_MATCH, 0) = 1 THEN 1 ELSE 0 END) AS DOUBLE) / COUNT(gim.IRR_MATCH_ID)\r\n"
          + "	END AS IRR_SCORE\r\n"
          + "FROM\r\n"
          + "	GTT_IRR_MATCH gim\r\n"
          + "GROUP BY\r\n"
          + "	gim.CONSENSUS_CASE_ID\r\n"
          + "),\r\n"
          + "\r\n"
          + "ALL_CASES AS (\r\n"
          + "SELECT\r\n"
          + "	gac.RECORD_ID AS RECORD_ID,\r\n"
          + "	gac.CASE_ID AS CASE_ID,\r\n"
          + "	gw.CASE_TYPE AS CASE_TYPE,\r\n"
          + "	gw.STEP_STATUS AS CASE_STATUS,\r\n"
          + "	gw.RECIPIENT_USER_ID AS ASSIGNED_USER_ONE_ID,\r\n"
          + "	NULL AS ASSIGNED_USER_TWO_ID,\r\n"
          + "	CONCAT(tu.FIRST_NAME, ' ', tu.LAST_NAME) AS ASSIGNED_USER_ONE_NAME,\r\n"
          + "	NULL AS ASSIGNED_USER_TWO_NAME,\r\n"
          + "	NULL AS PHYSICIAN_STEP,\r\n"
          + "	NULL AS CONSENSUS_STEP,\r\n"
          + "	NULL AS ABS_ONE_STEP,\r\n"
          + "	NULL AS ABS_TWO_STEP,\r\n"
          + "	act.min_smss_timestamp AS ASSIGNED_TIMESTAMP,\r\n"
          + "	NULL AS IRR_SCORE\r\n"
          + "FROM\r\n"
          + "	GTT_ABSTRACTOR_CASE gac\r\n"
          + "INNER JOIN GTT_WORKFLOW gw ON\r\n"
          + "	gw.CASE_ID = gac.CASE_ID\r\n"
          + "	AND gw.IS_LATEST = 1\r\n"
          + "LEFT JOIN TQMC_USER tu ON\r\n"
          + "	tu.USER_ID = gw.RECIPIENT_USER_ID\r\n"
          + "	AND tu.IS_ACTIVE = 1\r\n"
          + "LEFT JOIN ASSIGNED_CASE_TIMESTAMPS act\r\n"
          + "    ON\r\n"
          + "	act.CASE_ID = gac.CASE_ID\r\n"
          + "UNION ALL\r\n"
          + "SELECT\r\n"
          + "	gcc.RECORD_ID,\r\n"
          + "	gcc.CASE_ID AS CASE_ID,\r\n"
          + "	gw.CASE_TYPE AS CASE_TYPE,\r\n"
          + "	gw.STEP_STATUS AS CASE_STATUS,\r\n"
          + "	gw1.RECIPIENT_USER_ID AS ASSIGNED_USER_ONE_ID,\r\n"
          + "	gw2.RECIPIENT_USER_ID AS ASSIGNED_USER_TWO_ID,\r\n"
          + "	CONCAT(tu1.FIRST_NAME, ' ', tu1.LAST_NAME) AS ASSIGNED_USER_ONE_NAME,\r\n"
          + "	CONCAT(tu2.FIRST_NAME, ' ', tu2.LAST_NAME) AS ASSIGNED_USER_TWO_NAME,\r\n"
          + "	NULL AS PHYSICIAN_STEP,\r\n"
          + "	gw.STEP_STATUS AS CONSENSUS_STEP,\r\n"
          + "	gw1.STEP_STATUS AS ABS_ONE_STEP,\r\n"
          + "	gw2.STEP_STATUS AS ABS_TWO_STEP,\r\n"
          + "	act.min_smss_timestamp AS ASSIGNED_TIMESTAMP,\r\n"
          + "	irr.IRR_SCORE AS IRR_SCORE\r\n"
          + "FROM\r\n"
          + "	GTT_CONSENSUS_CASE gcc\r\n"
          + "INNER JOIN GTT_WORKFLOW gw ON\r\n"
          + "	gw.CASE_ID = gcc.CASE_ID\r\n"
          + "	AND gw.IS_LATEST = 1\r\n"
          + "LEFT JOIN GTT_WORKFLOW gw1 ON\r\n"
          + "	gw1.CASE_ID = gcc.ABS_1_CASE_ID\r\n"
          + "	AND gw1.IS_LATEST = 1\r\n"
          + "LEFT JOIN GTT_WORKFLOW gw2 ON\r\n"
          + "	gw2.CASE_ID = gcc.ABS_2_CASE_ID\r\n"
          + "	AND gw2.IS_LATEST = 1\r\n"
          + "LEFT JOIN TQMC_USER tu1 ON\r\n"
          + "	tu1.USER_ID = gw1.RECIPIENT_USER_ID\r\n"
          + "	AND tu1.IS_ACTIVE = 1\r\n"
          + "LEFT JOIN TQMC_USER tu2 ON\r\n"
          + "	tu2.USER_ID = gw2.RECIPIENT_USER_ID\r\n"
          + "	AND tu2.IS_ACTIVE = 1\r\n"
          + "LEFT JOIN ASSIGNED_CASE_TIMESTAMPS act\r\n"
          + "    ON\r\n"
          + "	act.CASE_ID = gcc.CASE_ID\r\n"
          + "LEFT JOIN IRR_SCORES irr\r\n"
          + "    ON\r\n"
          + "	irr.CONSENSUS_CASE_ID = gcc.CASE_ID\r\n"
          + "UNION ALL\r\n"
          + "SELECT\r\n"
          + "	gpc.RECORD_ID AS RECORD_ID,\r\n"
          + "	gpc.CASE_ID AS CASE_ID,\r\n"
          + "	gw.CASE_TYPE AS CASE_TYPE,\r\n"
          + "	gw.STEP_STATUS AS CASE_STATUS,\r\n"
          + "	gw.RECIPIENT_USER_ID AS ASSIGNED_USER_ONE_ID,\r\n"
          + "	NULL AS ASSIGNED_USER_TWO_ID,\r\n"
          + "	CONCAT(tu.FIRST_NAME, ' ', tu.LAST_NAME) AS ASSIGNED_USER_ONE_NAME,\r\n"
          + "	NULL AS ASSIGNED_USER_TWO_NAME,\r\n"
          + "	gw.STEP_STATUS AS PHYSICIAN_STEP,\r\n"
          + "	NULL AS CONSENSUS_STEP,\r\n"
          + "	NULL AS ABS_ONE_STEP,\r\n"
          + "	NULL AS ABS_TWO_STEP,\r\n"
          + "	act.min_smss_timestamp AS ASSIGNED_TIMESTAMP,\r\n"
          + "	NULL AS IRR_SCORE\r\n"
          + "FROM\r\n"
          + "	GTT_PHYSICIAN_CASE gpc\r\n"
          + "INNER JOIN GTT_WORKFLOW gw ON\r\n"
          + "	gw.CASE_ID = gpc.CASE_ID\r\n"
          + "	AND gw.IS_LATEST = 1\r\n"
          + "LEFT JOIN TQMC_USER tu ON\r\n"
          + "	tu.USER_ID = gw.RECIPIENT_USER_ID\r\n"
          + "	AND tu.IS_ACTIVE = 1\r\n"
          + "LEFT JOIN ASSIGNED_CASE_TIMESTAMPS act\r\n"
          + "    ON\r\n"
          + "	act.CASE_ID = gpc.CASE_ID\r\n"
          + "),\r\n"
          + "\r\n"
          + "RECORD_METADATA AS (\r\n"
          + "SELECT\r\n"
          + "	ac.RECORD_ID,\r\n"
          + "	tr.DMIS_ID,\r\n"
          + "	mtf.MTF_NAME,\r\n"
          + "	tr.DISCHARGE_DATE,\r\n"
          + "	LEFT(tr.DISCHARGE_DATE, 7) AS DISCHARGE_DATE_MONTH,\r\n"
          + "	MIN(ac.PHYSICIAN_STEP) AS PHYSICIAN_STEP,\r\n"
          + "	MIN(ac.CONSENSUS_STEP) AS CONSENSUS_STEP,\r\n"
          + "	MIN(ac.ABS_ONE_STEP) AS ABS_ONE_STEP,\r\n"
          + "	MIN(ac.ABS_TWO_STEP) AS ABS_TWO_STEP,\r\n"
          + " GROUP_CONCAT(ac.ASSIGNED_USER_ONE_ID) AS ASSIGNED_USER_ONE_ID\r\n"
          + "FROM\r\n"
          + "	ALL_CASES ac\r\n"
          + "JOIN TQMC_RECORD tr ON\r\n"
          + "	tr.RECORD_ID = ac.RECORD_ID\r\n"
          + "LEFT JOIN MTF mtf ON\r\n"
          + "	tr.DMIS_ID = mtf.DMIS_ID\r\n"
          + "GROUP BY\r\n"
          + "	ac.RECORD_ID,\r\n"
          + "	tr.DMIS_ID,\r\n"
          + "	mtf.MTF_NAME,\r\n"
          + "	tr.DISCHARGE_DATE\r\n"
          + "),\r\n"
          + "\r\n"
          + "RECORDS AS (\r\n"
          + "SELECT\r\n"
          + "	rm.RECORD_ID,\r\n"
          + "	rm.DMIS_ID,\r\n"
          + "	rm.MTF_NAME,\r\n"
          + "	rm.DISCHARGE_DATE,\r\n"
          + "	rm.DISCHARGE_DATE_MONTH,\r\n"
          + " rm.ASSIGNED_USER_ONE_ID,\r\n"
          + "	-- Logic for CURRENT_CASE_TYPE\r\n"
          + "    CASE\r\n"
          + "		WHEN rm.PHYSICIAN_STEP = 'completed' THEN 'completed'\r\n"
          + "		WHEN rm.CONSENSUS_STEP = 'completed' THEN 'physician'\r\n"
          + "		WHEN rm.ABS_ONE_STEP = 'completed' THEN\r\n"
          + "              CASE\r\n"
          + "			WHEN rm.ABS_TWO_STEP = 'completed' THEN 'consensus'\r\n"
          + "			ELSE 'abstraction'\r\n"
          + "		END\r\n"
          + "		ELSE 'abstraction'\r\n"
          + "	END AS CURRENT_CASE_TYPE,\r\n"
          + "	CASE\r\n"
          + "		WHEN rm.PHYSICIAN_STEP = 'completed' THEN 'completed'\r\n"
          + "		WHEN rm.CONSENSUS_STEP = 'completed' THEN rm.PHYSICIAN_STEP\r\n"
          + "		WHEN rm.ABS_ONE_STEP = 'completed'\r\n"
          + "			AND rm.ABS_TWO_STEP = 'completed' THEN rm.CONSENSUS_STEP\r\n"
          + "			WHEN rm.ABS_ONE_STEP NOT IN ('completed', 'in_progress')\r\n"
          + "				AND rm.ABS_TWO_STEP NOT IN ('completed', 'in_progress') THEN 'not_started'\r\n"
          + "				ELSE 'in_progress'\r\n"
          + "			END AS CURRENT_CASE_STATUS\r\n"
          + "		FROM\r\n"
          + "			RECORD_METADATA rm\r\n"
          + "),\r\n"
          + "\r\n"
          + "RECORDS_PROCESSED AS (\r\n"
          + "SELECT\r\n"
          + "	*,\r\n"
          + "	COUNT(*) OVER () AS TOTAL_ROW_COUNT\r\n"
          + "FROM\r\n"
          + "	RECORDS\r\n"
          + "WHERE\r\n"
          + "	%FILTER% %ORDER% %PAGINATION% \r\n"
          + ")\r\n"
          + "\r\n"
          + "SELECT\r\n"
          + "	rp.TOTAL_ROW_COUNT,\r\n"
          + "	rp.RECORD_ID,\r\n"
          + "	ac.CASE_ID,\r\n"
          + "	rp.DMIS_ID,\r\n"
          + "	rp.MTF_NAME,\r\n"
          + "	rp.DISCHARGE_DATE,\r\n"
          + "	rp.DISCHARGE_DATE_MONTH,\r\n"
          + "	ac.CASE_TYPE,\r\n"
          + "	ac.CASE_STATUS,\r\n"
          + "	ac.ASSIGNED_USER_ONE_ID,\r\n"
          + "	ac.ASSIGNED_USER_ONE_NAME,\r\n"
          + "	ac.ASSIGNED_USER_TWO_ID,\r\n"
          + "	ac.ASSIGNED_USER_TWO_NAME,\r\n"
          + "	ac.ASSIGNED_TIMESTAMP,\r\n"
          + "	ac.IRR_SCORE,\r\n"
          + "	rp.CURRENT_CASE_TYPE,\r\n"
          + "	rp.CURRENT_CASE_STATUS\r\n"
          + "FROM\r\n"
          + "			RECORDS_PROCESSED rp\r\n"
          + "LEFT JOIN ALL_CASES ac ON\r\n"
          + "			rp.RECORD_ID = ac.RECORD_ID\r\n";

  private static final List<String> VALID_SORT_FIELDS =
      Lists.newArrayList(
          "RECORD_ID", "MTF_NAME", "DISCHARGE_DATE", "CURRENT_CASE_TYPE", "CURRENT_CASE_STATUS");

  private static final List<String> VALID_FILTER_FIELDS =
      Lists.newArrayList(
          "RECORD_ID",
          "DISCHARGE_DATE_MONTH",
          "CURRENT_CASE_TYPE",
          "CURRENT_CASE_STATUS",
          "DMIS_ID",
          "ASSIGNED_USER_ONE_ID");

  private static final List<String> DEFAULT_ORDER_FIELDS =
      Lists.newArrayList("CURRENT_CASE_STATUS ASC", "DISCHARGE_DATE ASC", "RECORD_ID ASC");

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    if (!(hasProductPermission(TQMCConstants.GTT) && hasRole(TQMCConstants.MANAGEMENT_LEAD))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    GetListPayload payload =
        TQMCHelper.getListPayloadObject(
            this.getNounStore(), VALID_SORT_FIELDS, VALID_FILTER_FIELDS);

    List<String> arguments = new ArrayList<>();

    StringBuilder filter = new StringBuilder();
    TQMCHelper.appendFilterString(payload, arguments, filter);
    String filterPart = filter.length() > 0 ? filter.toString() : "1";

    StringBuilder order = new StringBuilder();
    TQMCHelper.appendOrderByString(payload, order, DEFAULT_ORDER_FIELDS);
    String orderPart = order.toString();

    StringBuilder pagination = new StringBuilder();
    TQMCHelper.appendPaginationString(payload, pagination);
    String paginationPart = pagination.toString();

    String query =
        GET_GTT_MANAGEMENT_CASES_BASE_QUERY
            .replace("%FILTER%", filterPart)
            .replace("%ORDER%", orderPart)
            .replace("%PAGINATION%", paginationPart);

    List<Map<String, Object>> managementCases = new ArrayList<>();
    Map<String, List<Map<String, Object>>> casesForRecordId = new HashMap<>();
    int totalCount = 0;

    try (PreparedStatement ps = con.prepareStatement(query)) {
      int parameterIndex = 1;
      for (String s : arguments) {
        ps.setString(parameterIndex++, s);
      }

      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          totalCount = rs.getInt("TOTAL_ROW_COUNT");

          String recordId = rs.getString("RECORD_ID");
          List<Map<String, Object>> caseListForRecord;

          if (!casesForRecordId.containsKey(recordId)) {
            Map<String, Object> c = new HashMap<>();
            c.put("record_id", recordId);
            c.put("dmis_id", rs.getString("DMIS_ID"));
            c.put(
                "discharge_date",
                ConversionUtils.getLocalDateStringFromDate(rs.getDate("DISCHARGE_DATE")));
            c.put("current_case_type", rs.getString("CURRENT_CASE_TYPE"));
            c.put("current_case_status", rs.getString("CURRENT_CASE_STATUS"));

            caseListForRecord = new ArrayList<>();
            casesForRecordId.put(recordId, caseListForRecord);
            c.put("cases", caseListForRecord);

            managementCases.add(c);
          } else {
            caseListForRecord = casesForRecordId.get(recordId);
          }

          Map<String, Object> caseItem = new HashMap<>();
          caseItem.put("case_id", rs.getString("CASE_ID"));
          caseItem.put("case_type", rs.getString("CASE_TYPE"));
          caseItem.put("case_status", rs.getString("CASE_STATUS"));
          caseItem.put("assigned_user_one_id", rs.getString("ASSIGNED_USER_ONE_ID"));
          caseItem.put("assigned_user_one_name", rs.getString("ASSIGNED_USER_ONE_NAME"));
          caseItem.put("assigned_user_two_id", rs.getString("ASSIGNED_USER_TWO_ID"));
          caseItem.put("assigned_user_two_name", rs.getString("ASSIGNED_USER_TWO_NAME"));
          caseItem.put(
              "assigned_date",
              ConversionUtils.getLocalDateTimeStringFromTimestamp(
                  rs.getTimestamp("ASSIGNED_TIMESTAMP")));
          double doubleValue = rs.getDouble("IRR_SCORE");
          if (!rs.wasNull()) {
            caseItem.put("irr", doubleValue);
          }
          caseListForRecord.add(caseItem);

          if (caseListForRecord != null && caseListForRecord.size() == 4) {
            Map<String, Object> consensus = caseListForRecord.get(2);
            Map<String, Object> abstraction = caseListForRecord.get(0);
            if (abstraction.get("assigned_user_one_id") != consensus.get("assigned_user_one_id")) {
              Map<String, Object> temp = caseListForRecord.get(0);
              caseListForRecord.set(0, caseListForRecord.get(1));
              caseListForRecord.set(1, temp);
            }
          }
        }
      }
    }

    Map<String, Object> results = new HashMap<>();
    results.put("total_row_count", totalCount);
    results.put("managementCases", managementCases);

    return new NounMetadata(results, PixelDataType.MAP);
  }
}
