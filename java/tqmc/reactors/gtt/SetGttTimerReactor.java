package tqmc.reactors.gtt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.gtt.GttCaseTime;
import tqmc.domain.gtt.GttWorkflow;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class SetGttTimerReactor extends AbstractTQMCReactor {

  public SetGttTimerReactor() {
    this.keysToGet =
        new String[] {
          TQMCConstants.CASE_ID, TQMCConstants.TIME_SECONDS, TQMCConstants.TIMER_STATUS
        };
    this.keyRequired = new int[] {1, 0, 1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    String caseId = keyValue.get(TQMCConstants.CASE_ID);

    boolean toggleToRunning;
    String requestedTimerStatus = keyValue.get(TQMCConstants.TIMER_STATUS);
    if (TQMCConstants.TIMER_RUNNING.equalsIgnoreCase(requestedTimerStatus)) {
      toggleToRunning = Boolean.TRUE;
    } else if (TQMCConstants.TIMER_PAUSED.equalsIgnoreCase(requestedTimerStatus)) {
      toggleToRunning = Boolean.FALSE;
    } else {
      throw new TQMCException(ErrorCode.BAD_REQUEST);
    }

    long timeInSeconds = 0L;
    String timeInSecondsString = keyValue.get(TQMCConstants.TIME_SECONDS);
    if (!toggleToRunning) {
      try {
        timeInSeconds = Long.parseLong(timeInSecondsString);
      } catch (NumberFormatException e) {
        throw new TQMCException(ErrorCode.BAD_REQUEST);
      }
    }

    if (!hasProductPermission(TQMCConstants.GTT)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    GttWorkflow w = TQMCHelper.getLatestGttWorkflow(con, caseId);
    if (w == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND);
    }

    if ((!hasRole(TQMCConstants.ADMIN) && !w.getVisibleTo().contains(userId))
        || TQMCConstants.CASE_STEP_STATUS_COMPLETED.equalsIgnoreCase(w.getStepStatus())) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    GttCaseTime gct = TQMCHelper.getGttCaseTime(con, caseId);
    if (gct == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND, "Timer for case not found");
    }

    if (!toggleToRunning && timeInSeconds < gct.getCumulativeTime()) {
      throw new TQMCException(ErrorCode.BAD_REQUEST);
    }

    if (!(gct.isRunning() ^ toggleToRunning)) {
      throw new TQMCException(ErrorCode.CONFLICT, "Timer already in given status");
    }

    GttCaseTime newGct = new GttCaseTime();
    GttWorkflow newW = null;
    LocalDateTime currentTimestamp = ConversionUtils.getUTCFromLocalNow();
    if (toggleToRunning) {
      if (TQMCConstants.CASE_STEP_STATUS_NOT_STARTED.equalsIgnoreCase(w.getStepStatus())) {
        newW = new GttWorkflow();
        newW.setCaseId(w.getCaseId());
        newW.setCaseType(w.getCaseType());
        newW.setGuid(UUID.randomUUID().toString());
        newW.setIsLatest(true);
        newW.setRecipientStage(w.getRecipientStage());
        newW.setRecipientUserId(userId);
        newW.setSendingStage(w.getRecipientStage());
        newW.setSendingUserId(w.getRecipientUserId());
        newW.setSmssTimestamp(currentTimestamp);
        newW.setStepStatus(TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS);
      }

      newGct.setCaseTimeId(gct.getCaseTimeId());
      newGct.setStartTime(currentTimestamp);
      newGct.setEndTime(null);
      newGct.setCumulativeTime(gct.getCumulativeTime() == null ? 0L : gct.getCumulativeTime());
    } else {
      newGct.setCaseTimeId(gct.getCaseTimeId());
      newGct.setStartTime(gct.getStartTime());
      newGct.setEndTime(
          gct.getStartTime().plus((timeInSeconds - gct.getCumulativeTime()), ChronoUnit.SECONDS));
      newGct.setCumulativeTime(timeInSeconds);
    }

    if (newW != null) {
      TQMCHelper.createNewGttWorkflowEntry(con, newW);
    }

    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE GTT_CASE_TIME SET START_TIME = ?, STOP_TIME = ?, "
                + "CUMULATIVE_TIME = ? WHERE CASE_TIME_ID = ?")) {
      int parameterIndex = 1;
      ps.setObject(parameterIndex++, newGct.getStartTime());
      ps.setObject(parameterIndex++, newGct.getEndTime());
      ps.setObject(parameterIndex++, newGct.getCumulativeTime());
      ps.setObject(parameterIndex++, newGct.getCaseTimeId());
      ps.execute();
    }

    return new NounMetadata(true, PixelDataType.BOOLEAN);
  }
}
