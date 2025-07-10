package tqmc.reactors.gtt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;

public class DeleteAllPreviouslyReassignedCasesReactor extends AbstractTQMCReactor {

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    if (!hasProductManagementPermission(TQMCConstants.GTT)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Map<String, Integer> deleteMap = new HashMap<>();

    try (PreparedStatement gtt_abstractor_case =
            con.prepareStatement(
                "delete from gtt_abstractor_case where case_id in (select case_id from gtt_workflow where recipient_stage = 'REASSIGNED')");
        PreparedStatement gtt_consensus_case =
            con.prepareStatement(
                "delete from gtt_consensus_case where case_id in (select case_id from gtt_workflow where recipient_stage = 'REASSIGNED')");
        PreparedStatement gtt_physician_case =
            con.prepareStatement(
                "delete from gtt_physician_case where case_id in (select case_id from gtt_workflow where recipient_stage = 'REASSIGNED')");
        PreparedStatement gtt_case_note =
            con.prepareStatement(
                "delete from gtt_case_note where case_id in (select case_id from gtt_workflow where recipient_stage = 'REASSIGNED')");
        PreparedStatement gtt_record_case_event =
            con.prepareStatement(
                "delete from gtt_record_case_event where case_id in (select case_id from gtt_workflow where recipient_stage = 'REASSIGNED')");
        PreparedStatement gtt_case_time =
            con.prepareStatement(
                "delete from gtt_case_time where case_id in (select case_id from gtt_workflow where recipient_stage = 'REASSIGNED')"); ) {

      deleteMap.put("gtt_abstractor_case", gtt_abstractor_case.executeUpdate());
      deleteMap.put("gtt_consensus_case", gtt_consensus_case.executeUpdate());
      deleteMap.put("gtt_physician_case", gtt_physician_case.executeUpdate());
      deleteMap.put("gtt_case_note", gtt_case_note.executeUpdate());
      deleteMap.put("gtt_record_case_event", gtt_record_case_event.executeUpdate());
      deleteMap.put("gtt_case_time", gtt_case_time.executeUpdate());
      deleteMap.put(
          "total",
          deleteMap.values().parallelStream().collect(Collectors.summingInt(Integer::intValue)));
    }

    return new NounMetadata(deleteMap, PixelDataType.MAP);
  }
}
