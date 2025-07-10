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
import tqmc.domain.soc.SocWorkflow;
import tqmc.domain.user.TQMCUserInfo;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;
import tqmc.util.TQMCProperties;

public class ReopenSocNurseReviewReactor extends AbstractTQMCReactor {
  private static final Logger LOGGER = LogManager.getLogger(ReopenSocNurseReviewReactor.class);

  private static final String REASON = "reason";
  private final LocalDateTime LOCAL_TIME = ConversionUtils.getUTCFromLocalNow();

  public ReopenSocNurseReviewReactor() {
    this.keysToGet = new String[] {TQMCConstants.NURSE_REVIEW_ID, REASON};
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

    // Remove the check that the SOC Case is completed before reopening
    // Commenting out in case we want to revert back to having the check reinstated
    //    String recordId = TQMCHelper.getNurseReviewRecordId(con, payload.getNurseReviewId());
    //    if (TQMCHelper.recordHasCompletedCase(con, recordId, ProductTables.SOC)) {
    //      throw new TQMCException(
    //          ErrorCode.BAD_REQUEST, "Invalid request: an SOC case has already been completed");
    //    }

    SocWorkflow latestSocWorkflow =
        TQMCHelper.getLatestSocWorkflow(con, payload.getNurseReviewId());
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
    socWorkflow.setCaseId(payload.getNurseReviewId());
    socWorkflow.setCaseType(latestSocWorkflow.getCaseType());
    socWorkflow.setGuid(UUID.randomUUID().toString());
    socWorkflow.setIsLatest(true);

    // TODO add a check to make sure the user is still eligible for the case
    socWorkflow.setRecipientUserId(latestSocWorkflow.getRecipientUserId());
    socWorkflow.setSendingUserId(userId);

    socWorkflow.setSmssTimestamp(LOCAL_TIME);
    socWorkflow.setStepStatus(TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS);
    String workflowNote =
        TQMCHelper.generateWorkflowNote(
            latestSocWorkflow.getStepStatus(), TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS, userId);
    socWorkflow.setWorkflowNotes(workflowNote);
    socWorkflow.setReopenReason(payload.getReason());

    TQMCHelper.updateSocWorkflow(con, socWorkflow);

    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE SOC_NURSE_REVIEW SET updated_at = ?, reopening_reason = ? WHERE nurse_review_id = ?")) {
      int parameterIndex = 1;
      ps.setObject(parameterIndex++, LOCAL_TIME);
      ps.setString(parameterIndex++, socWorkflow.getReopenReason());
      ps.setString(parameterIndex++, socWorkflow.getCaseId());
      ps.execute();
    }

    // Get due date
    Date dueDate = null;
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT DUE_DATE_REVIEW FROM SOC_NURSE_REVIEW WHERE NURSE_REVIEW_ID = ?")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, payload.getNurseReviewId());
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
          "soc chronology",
          TQMCProperties.getInstance().getTqmcUrl(),
          payload.getNurseReviewId(),
          true,
          TQMCHelper.getAliasRecordId(
              con,
              payload.getNurseReviewId(),
              ProductTables.SOC,
              TQMCConstants.CASE_TYPE_NURSE_REVIEW),
          assignee.getEmail(),
          tqmcUserInfo.getEmail(),
          ConversionUtils.getLocalDateStringFromDateSlashes(dueDate));
    } catch (TQMCException e) {
      LOGGER.warn(
          "Email failed for soc case assignment for case "
              + payload.getNurseReviewId()
              + " and assignee "
              + assignee.getUserId(),
          e);
    }

    return new NounMetadata(this.keyValue, PixelDataType.MAP);
  }

  public static class Payload {

    private String nurseReviewId;
    private String reason;

    public String getNurseReviewId() {
      return this.nurseReviewId;
    }

    public void setNurseReviewId(String nurseReviewId) {
      this.nurseReviewId = nurseReviewId;
    }

    public String getReason() {
      return this.reason;
    }

    public void setReason(String reason) {
      this.reason = reason;
    }
  }
}
