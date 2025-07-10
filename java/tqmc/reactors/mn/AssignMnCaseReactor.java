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
import tqmc.domain.mn.MnCase;
import tqmc.domain.mn.MnWorkflow;
import tqmc.domain.user.TQMCUserInfo;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;
import tqmc.util.TQMCProperties;

public class AssignMnCaseReactor extends AbstractTQMCReactor {
  private static final Logger LOGGER = LogManager.getLogger(AssignMnCaseReactor.class);

  private LocalDateTime currentTimestamp = ConversionUtils.getUTCFromLocalNow();
  private Payload payload;
  private MnCase currentMnCase;
  private MnWorkflow latestMnWorkflow;
  private TQMCUserInfo assignee;
  private String recordId;
  private String aliasRecordId;

  public AssignMnCaseReactor() {
    this.keysToGet = new String[] {TQMCConstants.CASE_ID, TQMCConstants.USER_ID};
    this.keyRequired = new int[] {1, 1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    loadAndValidate(con);

    // clear the contents of any question related to the case
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE MN_CASE_QUESTION "
                + "SET QUESTION_RATIONALE = NULL, QUESTION_REFERENCE = NULL, QUESTION_RESPONSE = NULL "
                + "WHERE CASE_ID = ? AND DELETED_AT IS NULL")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, payload.getCaseId());
      ps.execute();
    }

    // add assignment to case
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE MN_CASE SET USER_ID = ?, UPDATED_AT = ?, ASSIGNED_AT = ?, RECOMMENDATION_RESPONSE = NULL, REOPENING_REASON = NULL, RECOMMENDATION_EXPLANATION = NULL WHERE CASE_ID = ?")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, payload.getUserId());
      ps.setObject(parameterIndex++, currentTimestamp);
      ps.setObject(parameterIndex++, currentTimestamp);
      ps.setString(parameterIndex++, payload.getCaseId());
      ps.execute();
    }

    // update workflow
    MnWorkflow newW = new MnWorkflow();
    newW.setCaseId(latestMnWorkflow.getCaseId());
    newW.setGuid(UUID.randomUUID().toString());
    newW.setIsLatest(true);
    newW.setRecipientUserId(payload.getUserId());
    newW.setSendingUserId(latestMnWorkflow.getRecipientUserId());
    newW.setSmssTimestamp(currentTimestamp);
    if (payload.getUserId() == null) {
      newW.setStepStatus(TQMCConstants.CASE_STEP_STATUS_UNASSIGNED);
      newW.setWorkflowNotes(
          TQMCHelper.generateWorkflowNote(
              latestMnWorkflow.getStepStatus(), TQMCConstants.CASE_STEP_STATUS_UNASSIGNED, userId));
    } else {
      newW.setStepStatus(TQMCConstants.CASE_STEP_STATUS_NOT_STARTED);
      newW.setWorkflowNotes(
          TQMCHelper.generateWorkflowNote(
              latestMnWorkflow.getStepStatus(),
              TQMCConstants.CASE_STEP_STATUS_NOT_STARTED,
              userId));
    }

    TQMCHelper.updateMnWorkflow(con, newW, false);

    String appealCaseName =
        (String)
            TQMCHelper.getMnAppealInfo(con, currentMnCase.getAppealTypeId()).get("appeal_type");

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

    if (payload.getUserId() != null) {
      try {
        TQMCHelper.sendMNAssignmentEmail(
            TQMCConstants.MN,
            appealCaseName,
            TQMCProperties.getInstance().getTqmcUrl(),
            currentMnCase.getCaseId(),
            false,
            aliasRecordId,
            assignee.getEmail(),
            tqmcUserInfo.getEmail(),
            ConversionUtils.getLocalDateStringFromDateSlashes(dueDate));
      } catch (TQMCException e) {
        LOGGER.warn(
            "Email failed for mn case assignment for case "
                + currentMnCase.getCaseId()
                + " and assignee "
                + assignee.getUserId(),
            e);
      }
    }

    return new NounMetadata(true, PixelDataType.BOOLEAN);
  }

  private void loadAndValidate(Connection con) throws SQLException {
    if (!hasProductManagementPermission(TQMCConstants.MN)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);

    currentMnCase = TQMCHelper.getMnCase(con, payload.getCaseId());
    if (currentMnCase == null || currentMnCase.getDeletedAt() != null) {
      throw new TQMCException(ErrorCode.NOT_FOUND, "Case not found");
    }

    latestMnWorkflow = TQMCHelper.getLatestMnWorkflow(con, payload.getCaseId());
    if (TQMCConstants.COMPLETE_STATUS.equals(latestMnWorkflow.getStepStatus())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Case already complete");
    }

    assignee = TQMCHelper.getTQMCUserInfo(con, payload.getUserId());
    if (payload.getUserId() != null) {
      if (assignee == null || !assignee.getIsActive()) {
        throw new TQMCException(ErrorCode.NOT_FOUND, "User not found");
      }

      if (assignee.getProducts() == null
          || !assignee.getProducts().contains(TQMCConstants.MN)
          || !TQMCConstants.PEER_REVIEWER.equals(assignee.getRole())
          || assignee.getNpi() == null) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "User is not eligible for this case assignment");
      }

      if (assignee.getUserId().equals(currentMnCase.getUserId())) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "Cannot reassign to the same assignee as before");
      }

      recordId = TQMCHelper.getRecordId(con, payload.getCaseId(), ProductTables.MN);
      if (!TQMCHelper.hasFilesForRecord(con, recordId, ProductTables.MN)) {
        throw new TQMCException(ErrorCode.FORBIDDEN, "Cannot assign when record has no files");
      }
    }

    aliasRecordId = TQMCHelper.getAliasRecordId(con, payload.getCaseId(), ProductTables.MN);
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
