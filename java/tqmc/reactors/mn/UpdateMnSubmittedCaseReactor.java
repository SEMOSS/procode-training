package tqmc.reactors.mn;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.sql.Connection;
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
import tqmc.domain.mn.MnCase;
import tqmc.domain.mn.MnWorkflow;
import tqmc.domain.user.TQMCUserInfo;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;
import tqmc.util.TQMCProperties;

public class UpdateMnSubmittedCaseReactor extends AbstractTQMCReactor {

  private static final Logger LOGGER = LogManager.getLogger(ReopenMnCaseReactor.class);
  private final LocalDateTime localTime = ConversionUtils.getUTCFromLocalNow();

  private static final String MN_CASE = "mn_case";
  private static final String IS_REOPENING = "is_reopening";
  private static final String REASON = "reason";

  public UpdateMnSubmittedCaseReactor() {
    this.keysToGet = new String[] {MN_CASE, IS_REOPENING, REASON};
    this.keyRequired = new int[] {1, 1, 1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);
    MnCase mnCase = payload.getMnCase();

    // permissions only for management leads
    if (!(hasProductManagementPermission(TQMCConstants.MN))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    boolean submitted = true;
    TQMCHelper.checkMnCasePayload(con, mnCase, submitted);

    MnWorkflow latestMnWorkflow = TQMCHelper.getLatestMnWorkflow(con, mnCase.getCaseId());

    if (!TQMCConstants.CASE_STEP_STATUS_COMPLETED.equals(latestMnWorkflow.getStepStatus())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid request: case already open");
    }

    if (payload.getReason() == null || payload.getReason().isEmpty()) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid request: missing reason");
    }
    mnCase.setReopeningReason(payload.getReason());

    TQMCUserInfo assignee = null;
    if (payload.getIsReopening()) {
      mnCase.setAttestedAt(null);
      mnCase.setAttestationSignature(null);
      mnCase.setAttestationSpecialty(null);
      mnCase.setAttestationSubspecialty(null);
      mnCase.setReopeningReason(payload.getReason());

      assignee = TQMCHelper.getTQMCUserInfo(con, latestMnWorkflow.getRecipientUserId());
      if (assignee == null || !assignee.getIsActive()) {
        throw new TQMCException(
            ErrorCode.NOT_FOUND, "Invalid request: assigned user no longer exists");
      }
    }

    String submissionGuid = latestMnWorkflow.getGuid();
    MnCase currentCase = TQMCHelper.getMnCase(con, mnCase.getCaseId());
    TQMCHelper.createMnSubmissionEntry(con, currentCase, submissionGuid);
    mnCase.setUpdatedAt(localTime);

    TQMCHelper.validateAndUpdateMnCaseQuestions(con, mnCase);

    // Update the mn_case table to have inputted fields
    TQMCHelper.updateMnCaseTable(con, mnCase, submitted);

    latestMnWorkflow.setGuid(UUID.randomUUID().toString());
    latestMnWorkflow.setIsLatest(true);
    latestMnWorkflow.setRecipientUserId(mnCase.getComplete() ? userId : currentCase.getUserId());
    latestMnWorkflow.setSendingUserId(userId);
    latestMnWorkflow.setSmssTimestamp(localTime);
    String stepStatus;
    if (payload.getIsReopening()) {
      stepStatus = TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS;
    } else {
      stepStatus = TQMCConstants.CASE_STEP_STATUS_COMPLETED;
    }
    String workflowNote =
        TQMCHelper.generateWorkflowNote(latestMnWorkflow.getStepStatus(), stepStatus, userId);
    latestMnWorkflow.setStepStatus(stepStatus);
    latestMnWorkflow.setWorkflowNotes(workflowNote);

    TQMCHelper.updateMnWorkflow(con, latestMnWorkflow, false);

    if (payload.getIsReopening()) {
      String appealCaseName =
          (String)
              TQMCHelper.getMnAppealInfo(con, latestMnWorkflow.getAppealTypeId())
                  .get("appeal_type");
      try {
        TQMCHelper.sendMNAssignmentEmail(
            TQMCConstants.MN,
            appealCaseName,
            TQMCProperties.getInstance().getTqmcUrl(),
            mnCase.getCaseId(),
            true,
            TQMCHelper.getAliasRecordId(con, mnCase.getCaseId(), ProductTables.MN),
            assignee.getEmail(),
            tqmcUserInfo.getEmail(),
            ConversionUtils.getLocalDateStringFromLocalDateSlashes(currentCase.getDueDateReview()));
      } catch (TQMCException e) {
        LOGGER.warn(
            "Email failed for mn case assignment for case "
                + mnCase.getCaseId()
                + " and assignee "
                + assignee.getUserId(),
            e);
      }
    }

    return new NounMetadata(mnCase, PixelDataType.MAP);
  }

  public static class Payload {
    @JsonProperty("mn_case")
    private MnCase mnCase;

    @JsonProperty("is_reopening")
    private Boolean isReopening;

    private String reason;

    public MnCase getMnCase() {
      return this.mnCase;
    }

    public void setMnCase(MnCase mnCase) {
      this.mnCase = mnCase;
    }

    public Boolean getIsReopening() {
      return this.isReopening;
    }

    public void setIsReopening(Boolean isReopening) {
      this.isReopening = isReopening;
    }

    public String getReason() {
      return this.reason;
    }

    public void setReason(String reason) {
      this.reason = reason;
    }
  }
}
