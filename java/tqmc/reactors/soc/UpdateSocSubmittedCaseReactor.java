package tqmc.reactors.soc;

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
import tqmc.domain.soc.SocCase;
import tqmc.domain.soc.SocCaseType;
import tqmc.domain.soc.SocWorkflow;
import tqmc.domain.user.TQMCUserInfo;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;
import tqmc.util.TQMCProperties;

public class UpdateSocSubmittedCaseReactor extends AbstractTQMCReactor {

  private static final Logger LOGGER = LogManager.getLogger(UpdateSocSubmittedCaseReactor.class);
  private final LocalDateTime localTime = ConversionUtils.getUTCFromLocalNow();

  private static final String SOC_CASE = "soc_case";
  private static final String IS_REOPENING = "is_reopening";
  private static final String REASON = "reason";

  public UpdateSocSubmittedCaseReactor() {
    this.keysToGet = new String[] {SOC_CASE, IS_REOPENING, REASON};
    this.keyRequired = new int[] {1, 1, 1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);
    SocCase socCase = payload.getSocCase();

    // permissions only for management leads
    if (!(hasProductManagementPermission(TQMCConstants.SOC))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    boolean submitted = true;
    TQMCHelper.checkSocCasePayload(con, socCase, submitted);

    // Getting the current case status
    SocWorkflow w = TQMCHelper.getLatestSocWorkflow(con, socCase.getCaseId());

    if (payload.getReason() == null || payload.getReason().isEmpty()) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid request: missing reason");
    }
    socCase.setReopeningReason(payload.getReason());

    TQMCUserInfo assignee = null;
    if (payload.getIsReopening()) {
      socCase.setAttestedAt(null);
      socCase.setAttestationSignature(null);
      socCase.setAttestationSpecialty(null);
      socCase.setAttestationSubspecialty(null);
      socCase.setReopeningReason(payload.getReason());

      assignee = TQMCHelper.getTQMCUserInfo(con, w.getRecipientUserId());
      if (assignee == null || !assignee.getIsActive()) {
        throw new TQMCException(
            ErrorCode.NOT_FOUND, "Invalid request: assigned user no longer exists");
      }
    }

    String submissionGuid = w.getGuid();
    SocCase currentSocCase = TQMCHelper.getSocCase(con, socCase.getCaseId());
    TQMCHelper.createSocSubmissionEntry(con, currentSocCase, submissionGuid);
    socCase.setUpdatedAt(localTime);

    TQMCHelper.updateCurrentSocCaseAndEvaluations(con, socCase);

    // Create a new Workflow item to record changes
    SocWorkflow newW = null;

    newW = new SocWorkflow();
    newW.setCaseId(w.getCaseId());
    newW.setGuid(UUID.randomUUID().toString());
    newW.setIsLatest(true);
    newW.setRecipientUserId(socCase.getComplete() ? userId : currentSocCase.getUserId());
    newW.setSendingUserId(userId);
    newW.setSmssTimestamp(socCase.getUpdatedAt());
    newW.setCaseType(SocCaseType.PEER_REVIEW.getCaseType());
    String stepStatus;
    if (payload.getIsReopening()) {
      stepStatus = TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS;
    } else {
      stepStatus = TQMCConstants.CASE_STEP_STATUS_COMPLETED;
    }
    newW.setStepStatus(stepStatus);
    TQMCHelper.updateSocWorkflow(con, newW);

    if (payload.getIsReopening()) {
      try {
        TQMCHelper.sendAssignmentEmail(
            TQMCConstants.SOC,
            TQMCProperties.getInstance().getTqmcUrl(),
            socCase.getCaseId(),
            true,
            TQMCHelper.getAliasRecordId(
                con, socCase.getCaseId(), ProductTables.SOC, TQMCConstants.CASE_TYPE_PEER_REVIEW),
            assignee.getEmail(),
            tqmcUserInfo.getEmail(),
            ConversionUtils.getLocalDateStringFromLocalDateSlashes(
                currentSocCase.getDueDateReview()));
      } catch (TQMCException e) {
        LOGGER.warn(
            "Email failed for soc case assignment for case "
                + socCase.getCaseId()
                + " and assignee "
                + assignee.getUserId(),
            e);
      }
    }

    return new NounMetadata(socCase, PixelDataType.MAP);
  }

  public static class Payload {
    @JsonProperty("soc_case")
    private SocCase socCase;

    @JsonProperty("is_reopening")
    private Boolean isReopening;

    private String reason;

    public SocCase getSocCase() {
      return this.socCase;
    }

    public void setSocCase(SocCase socCase) {
      this.socCase = socCase;
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
