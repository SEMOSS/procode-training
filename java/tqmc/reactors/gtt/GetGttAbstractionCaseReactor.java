package tqmc.reactors.gtt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.gtt.GttWorkflow;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetGttAbstractionCaseReactor extends AbstractTQMCReactor {

  public GetGttAbstractionCaseReactor() {
    this.keysToGet = new String[] {TQMCConstants.CASE_ID, TQMCConstants.START_TIMER};
    this.keyRequired = new int[] {1, 0};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    if (!hasProductPermission(TQMCConstants.GTT)) {
      throw new TQMCException(
          ErrorCode.FORBIDDEN, "User is unauthorized to perform this operation");
    }

    String caseId = this.keyValue.get(TQMCConstants.CASE_ID);
    GttWorkflow w = TQMCHelper.getLatestGttWorkflow(con, caseId);
    if (w == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND);
    }

    if (!hasRole(TQMCConstants.ADMIN) && !w.getVisibleTo().contains(userId)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    if (Boolean.valueOf(keyValue.get(TQMCConstants.START_TIMER))) {
      // can't start the timer when the case is already complete
      if (TQMCConstants.CASE_STEP_STATUS_COMPLETED.equalsIgnoreCase(w.getStepStatus())) {
        throw new TQMCException(ErrorCode.BAD_REQUEST);
      }

      // open the case if not already
      if (TQMCConstants.CASE_STEP_STATUS_NOT_STARTED.equalsIgnoreCase(w.getStepStatus())) {
        GttWorkflow newW = new GttWorkflow();
        newW.setCaseId(caseId);
        newW.setCaseType(TQMCConstants.GTT_CASE_TYPE_ABSTRACTOR);
        newW.setGuid(UUID.randomUUID().toString());
        newW.setIsLatest(true);
        newW.setRecipientStage(TQMCConstants.GTT_STAGE_ABSTRACTION);
        newW.setRecipientUserId(userId);
        newW.setSendingStage(TQMCConstants.GTT_STAGE_ABSTRACTION);
        newW.setSendingUserId(userId);
        newW.setSmssTimestamp(ConversionUtils.getUTCFromLocalNow());
        newW.setStepStatus(TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS);

        TQMCHelper.createNewGttWorkflowEntry(con, newW);
      }

      // start the timer if not already
      try (PreparedStatement ps =
          con.prepareStatement(
              "UPDATE GTT_CASE_TIME SET START_TIME = ?, STOP_TIME = NULL, "
                  + "CUMULATIVE_TIME = COALESCE(CUMULATIVE_TIME, 0) WHERE CASE_ID = ? "
                  + "AND (START_TIME IS NULL OR STOP_TIME IS NOT NULL)")) {
        int parameterIndex = 1;
        ps.setObject(parameterIndex++, ConversionUtils.getUTCFromLocalNow());
        ps.setObject(parameterIndex++, caseId);
        ps.execute();
      }
    }

    Map<String, Object> c = TQMCHelper.getGttAbstractionCaseMap(con, userId, caseId);

    return new NounMetadata(c, PixelDataType.MAP);
  }
}
