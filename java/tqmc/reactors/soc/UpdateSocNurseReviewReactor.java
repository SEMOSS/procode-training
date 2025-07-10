package tqmc.reactors.soc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.soc.SocNurseReview;
import tqmc.domain.soc.SocWorkflow;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class UpdateSocNurseReviewReactor extends AbstractTQMCReactor {

  private LocalDateTime currentTimestamp = ConversionUtils.getUTCFromLocalNow();

  public UpdateSocNurseReviewReactor() {
    this.keysToGet = new String[] {TQMCConstants.NURSE_REVIEW};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);

    SocNurseReview nr = payload.getSocNurseReview();

    if (!TQMCHelper.hasNurseReviewEditAccess(con, userId, nr.getNurseReviewId())) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    // check nurse review payload
    TQMCHelper.checkSocNurseReviewPayload(con, nr);

    // Getting the current case status
    SocWorkflow w = TQMCHelper.getLatestSocWorkflow(con, nr.getNurseReviewId());

    // Can the user edit the case? Error if no
    if (TQMCConstants.CASE_STEP_STATUS_COMPLETED.equalsIgnoreCase(w.getStepStatus())) {
      throw new TQMCException(ErrorCode.FORBIDDEN, "Review already completed");
    }

    // update time after the check is done
    nr.setUpdatedAt(currentTimestamp);
    nr.setReopeningReason(w.getReopenReason());

    // Create a new Workflow item to record changes
    SocWorkflow newW = null;
    if (Boolean.TRUE == nr.getComplete()
        || TQMCConstants.CASE_STEP_STATUS_NOT_STARTED.equalsIgnoreCase(w.getStepStatus())) {
      newW = new SocWorkflow();
      newW.setCaseId(w.getCaseId());
      newW.setGuid(UUID.randomUUID().toString());
      newW.setIsLatest(true);
      newW.setRecipientUserId(userId);
      newW.setSendingUserId(w.getRecipientUserId());
      newW.setSmssTimestamp(currentTimestamp);
      newW.setStepStatus(
          Boolean.TRUE == nr.getComplete()
              ? TQMCConstants.CASE_STEP_STATUS_COMPLETED
              : TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS);
      newW.setCaseType(TQMCConstants.CASE_TYPE_NURSE_REVIEW);
      TQMCHelper.updateSocWorkflow(con, newW);
    }

    // update corresponding tables
    TQMCHelper.updateNurseReview(con, nr);

    return new NounMetadata(nr, PixelDataType.MAP);
  }

  public static class Payload {
    @JsonProperty(TQMCConstants.NURSE_REVIEW)
    private SocNurseReview socNurseReview;

    public SocNurseReview getSocNurseReview() {
      return socNurseReview;
    }

    public void setSocNurseReview(SocNurseReview socNurseReview) {
      this.socNurseReview = socNurseReview;
    }
  }
}
