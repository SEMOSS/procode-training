package tqmc.reactors.gtt;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
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

public class StartGttCaseReactor extends AbstractTQMCReactor {

  private final LocalDateTime localTime = ConversionUtils.getUTCFromLocalNow();

  public StartGttCaseReactor() {
    this.keysToGet = new String[] {TQMCConstants.CASE_ID, TQMCConstants.CASE_TYPE};
    this.keyRequired = new int[] {1, 1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    String caseId = this.keyValue.get(TQMCConstants.CASE_ID);

    if (!TQMCHelper.hasGttCaseAccess(con, userId, caseId)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    GttWorkflow wf = TQMCHelper.getLatestGttWorkflow(con, caseId);

    if (wf.getCaseId() == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND);
    }

    if (!TQMCHelper.canStartGttCase(con, wf)) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "case cannot be started");
    }

    if (TQMCConstants.CASE_STEP_STATUS_NOT_STARTED.equals(wf.getStepStatus())) {
      wf.setGuid(UUID.randomUUID().toString());
      wf.setIsLatest(true);
      wf.setRecipientUserId(userId);
      wf.setStepStatus(TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS);
      wf.setSmssTimestamp(localTime);
      TQMCHelper.createNewGttWorkflowEntry(con, wf);
    }

    return new NounMetadata(true, PixelDataType.BOOLEAN);
  }
}
