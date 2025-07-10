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
import tqmc.domain.base.GetListPayload;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetGttCasesReactor extends AbstractTQMCReactor {

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  private static final List<String> DEFAULT_ORDER_FIELDS =
      Lists.newArrayList("RECORD_ID ASC", "CASE_ID ASC");

  private static final List<String> VALID_SORT_FIELDS = Lists.newArrayList();

  private static final List<String> VALID_FILTER_FIELDS =
      Lists.newArrayList(
          "DMIS_ID", "CASE_TYPE", "CASE_STATUS", "ASSIGNED_USER_ONE_ID", "DISCHARGE_DATE_QUERY");

  /** Constructor, takes in arguments. None of the arguments are mandatory. */
  public GetGttCasesReactor() {
    this.keysToGet = TQMCConstants.LIST_REACTOR_ARGUMENTS;
    this.keyRequired = new int[] {0, 0, 0};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    String baseQuery =
        "SELECT *\n"
            + "FROM (\n"
            + "    SELECT\n"
            + "        mid_query.RECORD_ID,\n"
            + "        mid_query.case_id,\n"
            + "        mid_query.ASSIGNED_USER_ONE_ID,\n"
            + "        mid_query.CASE_TYPE,\n"
            + "        CASE\n"
            + "            WHEN mid_query.CASE_TYPE = 'abstraction' THEN paired_query.RECIPIENT_USER_ID\n"
            + "            ELSE NULL\n"
            + "        END AS PAIRED_ABSTRACTION_USER_ID,\n"
            + "        TO_CHAR(mid_query.DISCHARGE_DATE, 'YYYY-MM') AS DISCHARGE_DATE_QUERY,\n"
            + "        mid_query.CASE_STATUS,\n"
            + "        mid_query.DMIS_ID\n"
            + "    FROM\n"
            + "        (\n"
            + "        SELECT\n"
            + "            inner_query.RECORD_ID,\n"
            + "            inner_query.CASE_ID,\n"
            + "            gw.CASE_TYPE AS CASE_TYPE,\n"
            + "            gw.RECIPIENT_USER_ID AS ASSIGNED_USER_ONE_ID,\n"
            + "            r.dmis_id AS DMIS_ID,\n"
            + "            step_status AS CASE_STATUS,\n"
            + "            r.DISCHARGE_DATE AS DISCHARGE_DATE\n"
            + "        FROM\n"
            + "            (\n"
            + "            SELECT\n"
            + "                gac.record_id,\n"
            + "                gac.case_id\n"
            + "            FROM\n"
            + "                gtt_abstractor_case gac\n"
            + "            UNION\n"
            + "            SELECT\n"
            + "                gcc.record_id,\n"
            + "                gcc.case_id\n"
            + "            FROM\n"
            + "                gtt_consensus_case gcc\n"
            + "            UNION\n"
            + "            SELECT\n"
            + "                gpc.record_id,\n"
            + "                gpc.case_id\n"
            + "            FROM\n"
            + "                gtt_physician_case gpc) AS inner_query\n"
            + "        LEFT OUTER JOIN tqmc_record r ON\n"
            + "            inner_query.record_id = r.record_id\n"
            + "        LEFT OUTER JOIN GTT_WORKFLOW gw ON\n"
            + "            inner_query.CASE_ID = gw.CASE_ID\n"
            + "        WHERE\n"
            + "            gw.IS_LATEST = 1) AS mid_query\n"
            + "    LEFT JOIN (\n"
            + "        SELECT\n"
            + "            gac.record_id,\n"
            + "            gw.RECIPIENT_USER_ID,\n"
            + "            gac.case_id\n"
            + "        FROM\n"
            + "            gtt_abstractor_case gac\n"
            + "        JOIN GTT_WORKFLOW gw ON\n"
            + "            gac.case_id = gw.CASE_ID\n"
            + "        WHERE\n"
            + "            gw.CASE_TYPE = 'abstraction'\n"
            + "            AND gw.IS_LATEST = 1) AS paired_query ON\n"
            + "        mid_query.RECORD_ID = paired_query.record_id\n"
            + "        AND mid_query.CASE_ID != paired_query.case_id\n"
            + ") AS final_query\n";

    GetListPayload payload =
        TQMCHelper.getListPayloadObject(
            this.getNounStore(), VALID_SORT_FIELDS, VALID_FILTER_FIELDS);

    List<String> arguments = new ArrayList<>();
    String query =
        TQMCHelper.getListQuery(userId, payload, baseQuery, arguments, DEFAULT_ORDER_FIELDS);

    Map<String, List<Map<String, String>>> casesMap = new HashMap<>();

    try (PreparedStatement ps = con.prepareStatement(query)) {
      for (int i = 1; i < arguments.size(); i++) {
        ps.setString(i, arguments.get(i));
      }
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        String recordId = rs.getString("RECORD_ID");
        String caseId = rs.getString("CASE_ID");
        String assignedUserOneId = rs.getString("ASSIGNED_USER_ONE_ID");
        String pairedAbstractionUserId = rs.getString("PAIRED_ABSTRACTION_USER_ID");

        if (!casesMap.containsKey(recordId)) {
          casesMap.put(recordId, new ArrayList<>());
        }
        Map<String, String> caseHashMap = new HashMap<>();
        caseHashMap.put("case_id", caseId);
        caseHashMap.put("assigned_user_one_id", assignedUserOneId);
        caseHashMap.put("paired_abstraction_user_id", pairedAbstractionUserId);
        casesMap.get(recordId).add(caseHashMap);
      }
    }

    return new NounMetadata(casesMap, PixelDataType.MAP);
  }
}
