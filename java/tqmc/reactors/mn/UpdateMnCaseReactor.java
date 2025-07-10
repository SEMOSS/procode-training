package tqmc.reactors.mn;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.mn.MnCase;
import tqmc.domain.mn.MnWorkflow;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class UpdateMnCaseReactor extends AbstractTQMCReactor {

  private static final String MN_CASE = "mn_case";

  private final LocalDateTime localTime = ConversionUtils.getUTCFromLocalNow();

  public UpdateMnCaseReactor() {
    this.keysToGet = new String[] {MN_CASE};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);

    MnCase mnCase = payload.getMnCase();

    if (!TQMCHelper.hasCaseAccess(con, userId, mnCase.getCaseId(), TQMCConstants.MN)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    // validate the payload
    TQMCHelper.checkMnCasePayload(con, mnCase);

    // check if case is already completed
    MnWorkflow latestMnWorkflow = TQMCHelper.getLatestMnWorkflow(con, mnCase.getCaseId());
    latestMnWorkflow.setSmssTimestamp(localTime);

    if (TQMCConstants.CASE_STEP_STATUS_COMPLETED.equals(latestMnWorkflow.getStepStatus())) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST, "Invalid request: form has already been completed");
    }

    // get the current case questions that exist in the table for the inputted case
    TQMCHelper.validateAndUpdateMnCaseQuestions(con, mnCase);
    mnCase.setUpdatedAt(localTime);
    mnCase.setReopeningReason(latestMnWorkflow.getReopenReason());

    // Update the mn_case table to have inputted fields
    TQMCHelper.updateMnCaseTable(con, mnCase);

    // if workflow is moving on to the next step, update the workflow table
    boolean isComplete = mnCase.getComplete() != null && mnCase.getComplete();
    if (TQMCConstants.CASE_STEP_STATUS_NOT_STARTED.equals(latestMnWorkflow.getStepStatus())
        || (isComplete)) {

      MnWorkflow mnWorkflow = new MnWorkflow();
      mnWorkflow.setCaseId(mnCase.getCaseId());
      mnWorkflow.setGuid(UUID.randomUUID().toString());
      mnWorkflow.setIsLatest(true);
      mnWorkflow.setRecipientUserId(userId);
      mnWorkflow.setSendingUserId(latestMnWorkflow.getRecipientUserId());
      mnWorkflow.setSmssTimestamp(localTime);
      String stepStatus;
      if (isComplete) {
        stepStatus = TQMCConstants.CASE_STEP_STATUS_COMPLETED;
      } else {
        stepStatus = TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS;
      }
      String workflowNote =
          TQMCHelper.generateWorkflowNote(latestMnWorkflow.getStepStatus(), stepStatus, userId);
      mnWorkflow.setStepStatus(stepStatus);
      mnWorkflow.setWorkflowNotes(workflowNote);

      boolean first = false;
      TQMCHelper.updateMnWorkflow(con, mnWorkflow, first);
    }

    Map<String, Object> returnMap = new HashMap<>();
    returnMap.put(TQMCConstants.CASE_ID, mnCase.getCaseId());
    returnMap.put("questions", mnCase.getQuestions());
    returnMap.put("updated_at", ConversionUtils.getLocalDateTimeString(localTime));
    returnMap.put("complete", isComplete);
    returnMap.put("recommendation_response", mnCase.getRecommendationResponse());
    returnMap.put("recommendation_explanation", mnCase.getRecommendationExplanation());
    if (isComplete) {
      returnMap.put("attestation_signature", mnCase.getAttestationSignature());
      returnMap.put("attested_at", ConversionUtils.getLocalDateTimeString(localTime));
    }

    return new NounMetadata(returnMap, PixelDataType.MAP);
  }

  public static class Payload {
    @JsonProperty("mn_case")
    private MnCase mnCase;

    public MnCase getMnCase() {
      return this.mnCase;
    }

    public void setMnCase(MnCase mnCase) {
      this.mnCase = mnCase;
    }
  }
}
