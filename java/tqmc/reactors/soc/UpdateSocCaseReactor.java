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
import tqmc.domain.soc.SocCase;
import tqmc.domain.soc.SocCaseType;
import tqmc.domain.soc.SocWorkflow;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class UpdateSocCaseReactor extends AbstractTQMCReactor {

  private static final String SOC_CASE = "soc_case";
  private LocalDateTime currentTimestamp = ConversionUtils.getUTCFromLocalNow();

  public UpdateSocCaseReactor() {
    this.keysToGet = new String[] {SOC_CASE};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    if (!hasProductPermission(TQMCConstants.SOC)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);
    SocCase socCase = payload.getSocCase();

    if (!TQMCHelper.hasCaseAccess(con, userId, socCase.getCaseId(), TQMCConstants.SOC)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    TQMCHelper.checkSocCasePayload(con, socCase);

    socCase.setUpdatedAt(currentTimestamp);
    if (socCase.getComplete()) {
      socCase.setAttestedAt(currentTimestamp);
    }

    // Getting the current case status
    SocWorkflow w = TQMCHelper.getLatestSocWorkflow(con, socCase.getCaseId());
    if (w == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND, "Workflow didn't find case");
    }

    // Can the user edit the case? Error if no
    if (TQMCConstants.CASE_STEP_STATUS_COMPLETED.equalsIgnoreCase(w.getStepStatus())) {
      throw new TQMCException(ErrorCode.FORBIDDEN, "Case already complete or user is unauthorized");
    }

    // Create a new Workflow item to record changes
    SocWorkflow newW = null;
    if (Boolean.TRUE == socCase.getComplete()
        || TQMCConstants.CASE_STEP_STATUS_NOT_STARTED.equalsIgnoreCase(w.getStepStatus())) {
      newW = new SocWorkflow();
      newW.setCaseId(w.getCaseId());
      newW.setGuid(UUID.randomUUID().toString());
      newW.setIsLatest(true);
      newW.setRecipientUserId(userId);
      newW.setSendingUserId(w.getRecipientUserId());
      newW.setSmssTimestamp(currentTimestamp);
      newW.setCaseType(SocCaseType.PEER_REVIEW.getCaseType());
      newW.setStepStatus(
          Boolean.TRUE == socCase.getComplete()
              ? TQMCConstants.CASE_STEP_STATUS_COMPLETED
              : TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS);
      TQMCHelper.updateSocWorkflow(con, newW);
    }

    TQMCHelper.updateCurrentSocCaseAndEvaluations(con, socCase);

    return new NounMetadata(socCase, PixelDataType.MAP);
  }

  public static class Payload {

    @JsonProperty("soc_case")
    private SocCase socCase;

    public SocCase getSocCase() {
      return this.socCase;
    }

    public void setSocCase(SocCase socCase) {
      this.socCase = socCase;
    }
  }
}
