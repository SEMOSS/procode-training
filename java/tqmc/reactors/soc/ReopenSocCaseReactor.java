package tqmc.reactors.soc;

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
import tqmc.domain.soc.SocCaseType;
import tqmc.domain.soc.SocWorkflow;
import tqmc.domain.user.TQMCUserInfo;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;
import tqmc.util.TQMCProperties;

public class ReopenSocCaseReactor extends AbstractTQMCReactor {
  private static final Logger LOGGER = LogManager.getLogger(ReopenSocCaseReactor.class);

  private static final String REASON = "reason";

  private final LocalDateTime localTime = ConversionUtils.getUTCFromLocalNow();

  public ReopenSocCaseReactor() {
    this.keysToGet = new String[] {TQMCConstants.CASE_ID, REASON};
    this.keyRequired = new int[] {1, 1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    if (!hasProductManagementPermission(TQMCConstants.SOC)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);

    if (payload.getReason() == null || payload.getReason().isEmpty()) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid request: missing reason");
    }

    SocWorkflow latestSocWorkflow = TQMCHelper.getLatestSocWorkflow(con, payload.getCaseId());
    latestSocWorkflow.setReopenReason(payload.getReason());

    if (!TQMCConstants.CASE_STEP_STATUS_COMPLETED.equals(latestSocWorkflow.getStepStatus())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid request: case already open");
    }

    TQMCUserInfo assignee = TQMCHelper.getTQMCUserInfo(con, latestSocWorkflow.getRecipientUserId());
    if (assignee == null || !assignee.getIsActive()) {
      throw new TQMCException(
          ErrorCode.NOT_FOUND, "Invalid request: assigned user no longer exists");
    }

    SocWorkflow socWorkflow = new SocWorkflow();
    socWorkflow.setCaseId(payload.getCaseId());
    socWorkflow.setGuid(UUID.randomUUID().toString());
    socWorkflow.setIsLatest(true);

    // TODO add a check to make sure the user is still eligible for the case
    socWorkflow.setRecipientUserId(latestSocWorkflow.getRecipientUserId());
    socWorkflow.setSendingUserId(userId);

    socWorkflow.setSmssTimestamp(localTime);
    socWorkflow.setStepStatus(TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS);
    socWorkflow.setCaseType(SocCaseType.PEER_REVIEW.getCaseType());
    String workflowNote =
        TQMCHelper.generateWorkflowNote(
            latestSocWorkflow.getStepStatus(), TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS, userId);
    socWorkflow.setWorkflowNotes(workflowNote);
    socWorkflow.setReopenReason(payload.getReason());

    TQMCHelper.updateSocWorkflow(con, socWorkflow);

    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE SOC_CASE SET attestation_signature = NULL, attested_at = NULL, updated_at = ?, reopening_reason = ? WHERE case_id = ?")) {
      int parameterIndex = 1;
      ps.setObject(parameterIndex++, localTime);
      ps.setString(parameterIndex++, socWorkflow.getReopenReason());
      ps.setString(parameterIndex++, socWorkflow.getCaseId());
      ps.execute();
    }

    // get due date
    Date dueDate = null;
    try (PreparedStatement ps =
        con.prepareStatement("SELECT DUE_DATE_REVIEW FROM SOC_CASE WHERE CASE_ID = ?")) {
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
      TQMCHelper.sendAssignmentEmail(
          TQMCConstants.SOC,
          TQMCProperties.getInstance().getTqmcUrl(),
          payload.getCaseId(),
          true,
          TQMCHelper.getAliasRecordId(
              con, payload.getCaseId(), ProductTables.SOC, TQMCConstants.CASE_TYPE_PEER_REVIEW),
          assignee.getEmail(),
          tqmcUserInfo.getEmail(),
          ConversionUtils.getLocalDateStringFromDateSlashes(
              dueDate)); // TODO: Change to alias_record_id
    } catch (TQMCException e) {
      LOGGER.warn(
          "Email failed for soc case assignment for case "
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
