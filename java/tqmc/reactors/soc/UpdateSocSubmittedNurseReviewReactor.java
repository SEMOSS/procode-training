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
import tqmc.domain.soc.SocNurseReview;
import tqmc.domain.soc.SocWorkflow;
import tqmc.domain.user.TQMCUserInfo;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;
import tqmc.util.TQMCProperties;

public class UpdateSocSubmittedNurseReviewReactor extends AbstractTQMCReactor {

  private static final Logger LOGGER = LogManager.getLogger(UpdateSocSubmittedCaseReactor.class);
  private final LocalDateTime localTime = ConversionUtils.getUTCFromLocalNow();

  private static final String NURSE_REVIEW = "nurse_review";
  private static final String IS_REOPENING = "is_reopening";
  private static final String REASON = "reason";

  public UpdateSocSubmittedNurseReviewReactor() {
    this.keysToGet = new String[] {NURSE_REVIEW, IS_REOPENING, REASON};
    this.keyRequired = new int[] {1, 1, 0};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    // permissions only for management leads
    if (!(hasProductManagementPermission(TQMCConstants.SOC))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);
    SocNurseReview nr = payload.getSocNurseReview();

    // check nurse review payload
    TQMCHelper.checkSocNurseReviewPayload(con, nr);

    // Getting the current case status
    SocWorkflow w = TQMCHelper.getLatestSocWorkflow(con, nr.getNurseReviewId());

    if (payload.getReason() == null || payload.getReason().isEmpty()) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid request: missing reason");
    }
    nr.setReopeningReason(payload.getReason());

    TQMCUserInfo assignee = null;
    if (payload.getIsReopening()) {
      assignee = TQMCHelper.getTQMCUserInfo(con, w.getRecipientUserId());
      if (assignee == null || !assignee.getIsActive()) {
        throw new TQMCException(
            ErrorCode.NOT_FOUND, "Invalid request: assigned user no longer exists");
      }
    }

    SocNurseReview currentNurseReview = TQMCHelper.getSocNurseReview(con, nr.getNurseReviewId());
    TQMCHelper.createSocNurseReviewSubmissionEntry(con, currentNurseReview, w.getGuid());
    nr.setUpdatedAt(localTime);

    // update corresponding tables
    TQMCHelper.updateNurseReview(con, nr);

    SocWorkflow newW = null;

    newW = new SocWorkflow();
    newW.setCaseId(w.getCaseId());
    newW.setGuid(UUID.randomUUID().toString());
    newW.setIsLatest(true);
    newW.setRecipientUserId(nr.getComplete() ? userId : currentNurseReview.getUserId());
    newW.setSendingUserId(userId);
    newW.setSmssTimestamp(nr.getUpdatedAt());
    String stepStatus;
    if (payload.getIsReopening()) {
      stepStatus = TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS;
    } else {
      stepStatus = TQMCConstants.CASE_STEP_STATUS_COMPLETED;
    }
    newW.setStepStatus(stepStatus);
    newW.setCaseType(TQMCConstants.CASE_TYPE_NURSE_REVIEW);
    TQMCHelper.updateSocWorkflow(con, newW);

    if (payload.getIsReopening()) {
      try {
        TQMCHelper.sendAssignmentEmail(
            "soc chronology",
            TQMCProperties.getInstance().getTqmcUrl(),
            nr.getNurseReviewId(),
            true,
            TQMCHelper.getAliasRecordId(
                con,
                nr.getNurseReviewId(),
                ProductTables.SOC,
                TQMCConstants.CASE_TYPE_NURSE_REVIEW),
            assignee.getEmail(),
            tqmcUserInfo.getEmail(),
            ConversionUtils.getLocalDateStringFromLocalDateSlashes(
                currentNurseReview.getDueDateReview()));
      } catch (TQMCException e) {
        LOGGER.warn(
            "Email failed for soc case assignment for case "
                + nr.getNurseReviewId()
                + " and assignee "
                + assignee.getUserId(),
            e);
      }
    }

    return new NounMetadata(nr, PixelDataType.MAP);
  }

  public static class Payload {
    @JsonProperty("nurse_review")
    private SocNurseReview nurseReview;

    @JsonProperty("is_reopening")
    private Boolean isReopening;

    private String reason;

    public SocNurseReview getSocNurseReview() {
      return this.nurseReview;
    }

    public void setSocNurseReview(SocNurseReview nurseReview) {
      this.nurseReview = nurseReview;
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
