package tqmc.reactors.qu.dp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.qu.dp.DpWorkflow;
import tqmc.domain.user.TQMCUserInfo;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class ReopenDpCaseReactor extends AbstractTQMCReactor {

  private static final String REASON = "reason";
  private final LocalDateTime LOCAL_DATE = ConversionUtils.getUTCFromLocalNow();

  public ReopenDpCaseReactor() {
    this.keysToGet = new String[] {TQMCConstants.CASE_ID, REASON};
    this.keyRequired = new int[] {1, 1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    // checking the current user's permissions
    if (!hasProductManagementPermission(TQMCConstants.DP)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    // gathering values from reactor keys
    String caseId = this.keyValue.get(TQMCConstants.CASE_ID);
    String reasonVal = this.keyValue.get(REASON);

    // make sure the reason passed in isn'ta empty string
    if (reasonVal == null || reasonVal.isEmpty()) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid request: missing reason");
    }

    // getting the latest dp case workflow block and the case to update reopen reason
    DpWorkflow reopenCase = TQMCHelper.getLatestDpWorkflow(con, caseId);

    // getting the previous user assigned to the case and the management lead
    String assignedUserId = reopenCase.getRecipientUserId();
    String reassigner = this.userId;
    TQMCUserInfo assignee = TQMCHelper.getTQMCUserInfo(con, assignedUserId);

    // checking if the assignee still has the correct permissions
    if (assignee == null || !assignee.getIsActive()) {
      throw new TQMCException(
          ErrorCode.NOT_FOUND, "Invalid request: assigned user no longer exists");
    }
    if (!assignee.getProducts().contains(TQMCConstants.DP)) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST, "Invalid request: assigned user no longer has DP Product");
    }

    // check to make sure the case isn't already open
    if (!TQMCConstants.CASE_STEP_STATUS_COMPLETED.equals(reopenCase.getStepStatus())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid request: case already open");
    }

    // setting reopen reason
    try (PreparedStatement updateReason =
        con.prepareStatement(
            "UPDATE DP_CASE dc " + " SET dc.REOPENING_REASON = ? " + " WHERE dc.CASE_ID = ?")) {
      updateReason.setString(1, reasonVal);
      updateReason.setString(2, caseId);
      updateReason.execute();
    }

    // creating a new workflow block to set the step status to in progress so it's reopened
    reopenCase.setStepStatus(TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS);
    reopenCase.setReopenReason(reasonVal);
    reopenCase.setSmssTimestamp(LOCAL_DATE);
    reopenCase.setIsLatest(true);
    reopenCase.setSendingUserId(reassigner);
    reopenCase.setRecipientUserId(assignedUserId);
    reopenCase.setGuid(UUID.randomUUID().toString());
    String workflowNote =
        TQMCHelper.generateReopenWorkflowNote(reassigner, assignedUserId, reasonVal);
    reopenCase.setWorkflowNotes(workflowNote);

    TQMCHelper.createNewDpWorkflowEntry(con, reopenCase);

    return new NounMetadata(true, PixelDataType.BOOLEAN);
  }
}
