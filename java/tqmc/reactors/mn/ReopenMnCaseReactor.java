package tqmc.reactors.mn;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.ProductTables;
import tqmc.domain.base.TQMCException;
import tqmc.domain.mn.MnWorkflow;
import tqmc.domain.user.TQMCUserInfo;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;
import tqmc.util.TQMCProperties;

public class ReopenMnCaseReactor extends AbstractTQMCReactor {
  private static final Logger LOGGER = LogManager.getLogger(ReopenMnCaseReactor.class);

  private static final String REASON = "reason";
  private final LocalDateTime localTime = ConversionUtils.getUTCFromLocalNow();

  public ReopenMnCaseReactor() {
    this.keysToGet = new String[] {TQMCConstants.CASE_ID, REASON};
    this.keyRequired = new int[] {1, 1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    if (!hasProductManagementPermission(TQMCConstants.MN)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);

    if (payload.getReason() == null || payload.getReason().isEmpty()) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid request: missing reason");
    }

    MnWorkflow latestMnWorkflow = TQMCHelper.getLatestMnWorkflow(con, payload.getCaseId());
    latestMnWorkflow.setReopenReason(payload.getReason());

    if (!TQMCConstants.CASE_STEP_STATUS_COMPLETED.equals(latestMnWorkflow.getStepStatus())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid request: case already open");
    }

    TQMCUserInfo assignee = TQMCHelper.getTQMCUserInfo(con, latestMnWorkflow.getRecipientUserId());
    if (assignee == null || !assignee.getIsActive()) {
      throw new TQMCException(
          ErrorCode.NOT_FOUND, "Invalid request: assigned user no longer exists");
    }

    MnWorkflow mnWorkflow = new MnWorkflow();
    mnWorkflow.setCaseId(payload.getCaseId());
    mnWorkflow.setGuid(UUID.randomUUID().toString());
    mnWorkflow.setIsLatest(true);
    // TODO add a check to make sure the user is still eligible for the case
    mnWorkflow.setRecipientUserId(latestMnWorkflow.getRecipientUserId());
    mnWorkflow.setSendingUserId(userId);
    mnWorkflow.setSmssTimestamp(localTime);
    mnWorkflow.setStepStatus(TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS);
    String workflowNote =
        TQMCHelper.generateWorkflowNote(
            latestMnWorkflow.getStepStatus(), TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS, userId);
    mnWorkflow.setWorkflowNotes(workflowNote);
    mnWorkflow.setReopenReason(payload.getReason());

    boolean first = false;
    TQMCHelper.updateMnWorkflow(con, mnWorkflow, first);

    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE MN_CASE SET attestation_signature = NULL, attested_at = NULL, updated_at = ?, reopening_reason = ? WHERE case_id = ?")) {
      int parameterIndex = 1;
      ps.setObject(parameterIndex++, localTime);
      ps.setString(parameterIndex++, mnWorkflow.getReopenReason());
      ps.setString(parameterIndex++, mnWorkflow.getCaseId());
      ps.execute();
    }

    String appealCaseName =
        (String)
            TQMCHelper.getMnAppealInfo(con, latestMnWorkflow.getAppealTypeId()).get("appeal_type");

    // get due date
    Date dueDate = null;
    try (PreparedStatement ps =
        con.prepareStatement("SELECT DUE_DATE_REVIEW FROM MN_CASE WHERE CASE_ID = ?")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, payload.getCaseId());
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        dueDate = rs.getDate("DUE_DATE_REVIEW");
      }
    }

    if (dueDate == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Missing due date");
    }

    try {
      TQMCHelper.sendMNAssignmentEmail(
          TQMCConstants.MN,
          appealCaseName,
          TQMCProperties.getInstance().getTqmcUrl(),
          payload.getCaseId(),
          true,
          TQMCHelper.getAliasRecordId(con, payload.getCaseId(), ProductTables.MN),
          assignee.getEmail(),
          tqmcUserInfo.getEmail(),
          ConversionUtils.getLocalDateStringFromDateSlashes(dueDate));
    } catch (TQMCException e) {
      LOGGER.warn(
          "Email failed for mn case assignment for case "
              + payload.getCaseId()
              + " and assignee "
              + assignee.getUserId(),
          e);
    }

    return new NounMetadata(this.keyValue, PixelDataType.MAP);
  }

  public static class Payload {

    private String caseId;
    private String reason;

    public String getCaseId() {
      return this.caseId;
    }

    public void setCaseId(String caseId) {
      this.caseId = caseId;
    }

    public String getReason() {
      return this.reason;
    }

    public void setReason(String reason) {
      this.reason = reason;
    }
  }
}
