package tqmc.reactors.qu.dp;

import java.sql.Connection;
import java.sql.PreparedStatement;
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
import tqmc.domain.qu.dp.DpCase;
import tqmc.domain.qu.dp.DpWorkflow;
import tqmc.domain.user.TQMCUserInfo;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;
import tqmc.util.TQMCProperties;

public class AssignDpCaseReactor extends AbstractTQMCReactor {
  private static final Logger LOGGER = LogManager.getLogger(AssignDpCaseReactor.class);

  private LocalDateTime currentTimestamp = ConversionUtils.getUTCFromLocalNow();
  private Payload payload;
  private DpCase currentDpCase;
  private DpWorkflow latestDpWorkflow;
  private TQMCUserInfo assignee;
  private String recordId;
  private String aliasRecordId;

  public AssignDpCaseReactor() {
    this.keysToGet = new String[] {TQMCConstants.CASE_ID, TQMCConstants.USER_ID};
    this.keyRequired = new int[] {1, 1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    loadAndValidate(con);

    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE DP_AUDIT_RESPONSE SET RESPONSE_TEMPLATE_ID = NULL WHERE CASE_ID = ?")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, payload.getCaseId());
      ps.execute();
    }

    // add assignment to case
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE DP_CASE SET USER_ID = ?, UPDATED_AT = ?, ASSIGNED_AT = ? WHERE CASE_ID = ?")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, assignee.getUserId());
      ps.setObject(parameterIndex++, currentTimestamp);
      ps.setObject(parameterIndex++, currentTimestamp);
      ps.setString(parameterIndex++, payload.getCaseId());
      ps.execute();
    }

    // update workflow
    DpWorkflow newW = new DpWorkflow();
    newW.setCaseId(latestDpWorkflow.getCaseId());
    newW.setGuid(UUID.randomUUID().toString());
    newW.setIsLatest(true);
    newW.setRecipientUserId(assignee.getUserId());
    newW.setSendingUserId(latestDpWorkflow.getRecipientUserId());
    newW.setSmssTimestamp(currentTimestamp);
    newW.setStepStatus(TQMCConstants.CASE_STEP_STATUS_NOT_STARTED);
    TQMCHelper.createNewDpWorkflowEntry(con, newW);

    try {
      TQMCHelper.sendAssignmentEmail(
          TQMCConstants.DP,
          TQMCProperties.getInstance().getTqmcUrl(),
          currentDpCase.getCaseId(),
          false,
          aliasRecordId,
          assignee.getEmail(),
          tqmcUserInfo.getEmail(),
          "insert due date");
    } catch (TQMCException e) {
      LOGGER.warn(
          "Email failed for dp case assignment for case "
              + currentDpCase.getCaseId()
              + " and assignee "
              + assignee.getUserId(),
          e);
    }

    return new NounMetadata(true, PixelDataType.BOOLEAN);
  }

  private void loadAndValidate(Connection con) throws SQLException {
    if (!hasProductManagementPermission(TQMCConstants.MCSC)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);

    currentDpCase = TQMCHelper.getDpCase(con, payload.getCaseId());
    if (currentDpCase == null || currentDpCase.getDeletedAt() != null) {
      throw new TQMCException(ErrorCode.NOT_FOUND, "Case not found");
    }

    latestDpWorkflow = TQMCHelper.getLatestDpWorkflow(con, payload.getCaseId());
    if (TQMCConstants.CASE_STEP_STATUS_COMPLETED.equals(latestDpWorkflow.getStepStatus())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Case already complete");
    }

    assignee = TQMCHelper.getTQMCUserInfo(con, payload.getUserId());
    if (assignee == null || !assignee.getIsActive()) {
      throw new TQMCException(ErrorCode.NOT_FOUND, "User not found");
    }

    if (assignee.getProducts() == null
        || !assignee.getProducts().contains(TQMCConstants.MCSC)
        || !TQMCConstants.QUALITY_REVIEWER.equals(assignee.getRole())
        || assignee.getNpi() == null) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST, "User is not eligible for this case assignment");
    }

    if (assignee.getUserId().equals(currentDpCase.getUserId())) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST, "Cannot reassign to the same assignee as before");
    }

    recordId = TQMCHelper.getRecordId(con, payload.getCaseId(), ProductTables.DP);
    if (!TQMCHelper.hasFilesForRecord(con, recordId, ProductTables.DP)) {
      throw new TQMCException(ErrorCode.FORBIDDEN, "Cannot assign when record has no files");
    }

    aliasRecordId = TQMCHelper.getAliasRecordId(con, payload.getCaseId(), ProductTables.DP);
  }

  public static class Payload {
    private String caseId;
    private String userId;

    public Payload() {}

    public String getCaseId() {
      return caseId;
    }

    public void setCaseId(String caseId) {
      this.caseId = caseId;
    }

    public String getUserId() {
      return userId;
    }

    public void setUserId(String userId) {
      this.userId = userId;
    }
  }
}
