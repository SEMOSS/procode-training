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
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetGttRecordSummaryReactor extends AbstractTQMCReactor {
  // Running a query using With will not retain the data and return it can't find
  // For example, CASE_TYPE from Workflow will not show up in the subsequent SELECT query

  private static final String BASE_QUERY =
      "WITH \r\n"
          + "\r\n"
          + "ALL_CASES AS (\r\n"
          + "SELECT\r\n"
          + "	gac.RECORD_ID AS RECORD_ID,\r\n"
          + "	gw.CASE_TYPE AS CASE_TYPE,\r\n"
          + "	gw.STEP_STATUS AS CASE_STATUS,\r\n"
          + "	NULL AS PHYSICIAN_STEP,\r\n"
          + "	NULL AS CONSENSUS_STEP,\r\n"
          + "	NULL AS ABS_ONE_STEP,\r\n"
          + "	NULL AS ABS_TWO_STEP\r\n"
          + "FROM\r\n"
          + "	GTT_ABSTRACTOR_CASE gac\r\n"
          + "INNER JOIN GTT_WORKFLOW gw ON\r\n"
          + "	gw.CASE_ID = gac.CASE_ID\r\n"
          + "	AND gw.IS_LATEST = 1\r\n"
          + "UNION ALL\r\n"
          + "SELECT\r\n"
          + "	gcc.RECORD_ID,\r\n"
          + "	gw.CASE_TYPE AS CASE_TYPE,\r\n"
          + "	gw.STEP_STATUS AS CASE_STATUS,\r\n"
          + "	NULL AS PHYSICIAN_STEP,\r\n"
          + "	gw.STEP_STATUS AS CONSENSUS_STEP,\r\n"
          + "	gw1.STEP_STATUS AS ABS_ONE_STEP,\r\n"
          + "	gw2.STEP_STATUS AS ABS_TWO_STEP\r\n"
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
          + "UNION ALL\r\n"
          + "SELECT\r\n"
          + "	gpc.RECORD_ID AS RECORD_ID,\r\n"
          + "	gw.CASE_TYPE AS CASE_TYPE,\r\n"
          + "	gw.STEP_STATUS AS CASE_STATUS,\r\n"
          + "	gw.STEP_STATUS AS PHYSICIAN_STEP,\r\n"
          + "	NULL AS CONSENSUS_STEP,\r\n"
          + "	NULL AS ABS_ONE_STEP,\r\n"
          + "	NULL AS ABS_TWO_STEP\r\n"
          + "FROM\r\n"
          + "	GTT_PHYSICIAN_CASE gpc\r\n"
          + "INNER JOIN GTT_WORKFLOW gw ON\r\n"
          + "	gw.CASE_ID = gpc.CASE_ID\r\n"
          + "	AND gw.IS_LATEST = 1\r\n"
          + "),\r\n"
          + "\r\n"
          + "RECORD_METADATA AS (\r\n"
          + "SELECT\r\n"
          + "	tr.DMIS_ID,\r\n"
          + "	LEFT(tr.DISCHARGE_DATE, 7) AS DISCHARGE_DATE_MONTH,\r\n"
          + "	MIN(ac.PHYSICIAN_STEP) AS PHYSICIAN_STEP,\r\n"
          + "	MIN(ac.CONSENSUS_STEP) AS CONSENSUS_STEP,\r\n"
          + "	MIN(ac.ABS_ONE_STEP) AS ABS_ONE_STEP,\r\n"
          + "	MIN(ac.ABS_TWO_STEP) AS ABS_TWO_STEP\r\n"
          + "FROM\r\n"
          + "	ALL_CASES ac\r\n"
          + "JOIN TQMC_RECORD tr ON\r\n"
          + "	tr.RECORD_ID = ac.RECORD_ID\r\n"
          + "LEFT JOIN MTF mtf ON\r\n"
          + "	tr.DMIS_ID = mtf.DMIS_ID\r\n"
          + "GROUP BY\r\n"
          + "	ac.RECORD_ID,\r\n"
          + "	tr.DMIS_ID,\r\n"
          + "	LEFT(tr.DISCHARGE_DATE, 7)\r\n"
          + "	),\r\n"
          + "\r\n"
          + "RECORDS AS (\r\n"
          + "SELECT\r\n"
          + "	rm.DMIS_ID,\r\n"
          + "	rm.DISCHARGE_DATE_MONTH,\r\n"
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
          + ")\r\n"
          + "\r\n"
          + "SELECT\r\n"
          + "	SUM(CASE WHEN CURRENT_CASE_TYPE = 'abstraction' THEN 1 ELSE 0 END) AS ABSTRACTION_CASES,\r\n"
          + "	SUM(CASE WHEN CURRENT_CASE_TYPE = 'consensus' THEN 1 ELSE 0 END) AS CONSENSUS_CASES,\r\n"
          + "	SUM(CASE WHEN CURRENT_CASE_TYPE = 'physician' THEN 1 ELSE 0 END) AS PHYSICIAN_CASES,\r\n"
          + "	SUM(CASE WHEN CURRENT_CASE_TYPE = 'completed' THEN 1 ELSE 0 END) AS COMPLETED_CASES\r\n"
          + "FROM\r\n"
          + "	RECORDS \r\n";

  private static final List<String> VALID_FILTER_FIELDS =
      Lists.newArrayList("DISCHARGE_DATE_MONTH", "DMIS_ID");

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  /** */
  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    // check access requirements
    if (!hasProductManagementPermission(TQMCConstants.GTT)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    /** Gets input arguments using helper function and validates inputs. */
    GetListPayload payload =
        TQMCHelper.getListPayloadObject(this.getNounStore(), null, VALID_FILTER_FIELDS);

    /** Forms the final query using all of the information above. */
    List<String> arguments = new ArrayList<>();
    String query = TQMCHelper.getListQuery(userId, payload, BASE_QUERY, arguments, null);

    Map<String, Object> result = new HashMap<>();
    try (PreparedStatement ps = con.prepareStatement(query)) {

      for (int i = 1; i < arguments.size(); i++) {
        ps.setString(i, arguments.get(i));
      }
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        result.put(TQMCConstants.GTT_CASE_TYPE_ABSTRACTOR, rs.getInt("ABSTRACTION_CASES"));
        result.put(TQMCConstants.GTT_CASE_TYPE_CONSENSUS, rs.getInt("CONSENSUS_CASES"));
        result.put(TQMCConstants.GTT_CASE_TYPE_PHYSICIAN, rs.getInt("PHYSICIAN_CASES"));
        result.put(TQMCConstants.CASE_STEP_STATUS_COMPLETED, rs.getInt("COMPLETED_CASES"));
      }
    }

    return new NounMetadata(result, PixelDataType.MAP);
  }
}
