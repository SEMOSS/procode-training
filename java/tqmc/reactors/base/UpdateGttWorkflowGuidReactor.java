package tqmc.reactors.base;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.gtt.GttWorkflow;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;

public class UpdateGttWorkflowGuidReactor extends AbstractTQMCReactor {

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    // TODO Auto-generated method stub
    String getQuery =
        "SELECT CASE_ID, CASE_TYPE, GUID, IS_LATEST, RECIPIENT_STAGE, RECIPIENT_USER_ID, SENDING_STAGE, SENDING_USER_ID, SMSS_TIMESTAMP, STEP_STATUS FROM GTT_WORKFLOW";
    List<GttWorkflow> wfs = new ArrayList<>();
    try (PreparedStatement ps = con.prepareStatement(getQuery)) {
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          GttWorkflow result = new GttWorkflow();
          result.setCaseId(rs.getString("CASE_ID"));
          result.setCaseType(rs.getString("CASE_TYPE"));
          result.setGuid(rs.getString("GUID"));
          result.setIsLatest(rs.getInt("IS_LATEST") == 1);
          result.setRecipientStage(rs.getString("RECIPIENT_STAGE"));
          result.setRecipientUserId(rs.getString("RECIPIENT_USER_ID"));
          result.setSendingStage(rs.getString("SENDING_STAGE"));
          result.setSendingUserId(rs.getString("SENDING_USER_ID"));
          result.setSmssTimestamp(
              ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("SMSS_TIMESTAMP")));
          result.setStepStatus(rs.getString("STEP_STATUS"));
          wfs.add(result);
        }
      }
    }

    String deleteQuery = "DELETE FROM GTT_WORKFLOW";
    try (PreparedStatement ps = con.prepareStatement(deleteQuery)) {
      ps.execute();
    }

    String insertQuery =
        "INSERT INTO GTT_WORKFLOW ("
            + "CASE_ID, CASE_TYPE, GUID, IS_LATEST, RECIPIENT_STAGE, RECIPIENT_USER_ID, "
            + "SENDING_STAGE, SENDING_USER_ID, SMSS_TIMESTAMP, STEP_STATUS) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?)";
    try (PreparedStatement ps = con.prepareStatement(insertQuery)) {
      for (GttWorkflow w : wfs) {
        int parameterIndex = 1;
        ps.setString(parameterIndex++, w.getCaseId());
        ps.setString(parameterIndex++, w.getCaseType());
        ps.setString(parameterIndex++, UUID.randomUUID().toString());
        ps.setBoolean(parameterIndex++, w.getIsLatest());
        ps.setString(parameterIndex++, w.getRecipientStage());
        ps.setString(parameterIndex++, w.getRecipientUserId());
        ps.setString(parameterIndex++, w.getSendingStage());
        ps.setString(parameterIndex++, w.getSendingUserId());
        ps.setObject(parameterIndex++, w.getSmssTimestamp());
        ps.setString(parameterIndex++, w.getStepStatus());
        ps.addBatch();
      }
      ps.executeBatch();
    }
    return new NounMetadata(true, PixelDataType.BOOLEAN);
  }
}
