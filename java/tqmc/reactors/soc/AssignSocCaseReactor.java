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
import tqmc.domain.soc.SocCase;
import tqmc.domain.soc.SocCaseType;
import tqmc.domain.soc.SocWorkflow;
import tqmc.domain.user.TQMCUserInfo;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;
import tqmc.util.TQMCProperties;

public class AssignSocCaseReactor extends AbstractTQMCReactor {
  private static final Logger LOGGER = LogManager.getLogger(AssignSocCaseReactor.class);

  private LocalDateTime currentTimestamp = ConversionUtils.getUTCFromLocalNow();
  private Payload payload;
  private SocCase currentSocCase;
  private SocWorkflow latestSocWorkflow;
  private TQMCUserInfo assignee;
  private String recordId;
  private String aliasRecordId;

  public AssignSocCaseReactor() {
    this.keysToGet = new String[] {TQMCConstants.CASE_ID, TQMCConstants.USER_ID};
    this.keyRequired = new int[] {1, 1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    loadAndValidate(con);

    // clear the contents of any evaluation related to the case
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE SOC_CASE_PROVIDER_EVALUATION "
                + "SET STANDARDS_MET = NULL, STANDARDS_RATIONALE = NULL, STANDARDS_JUSTIFICATION = NULL, "
                + "DEVIATION_CLAIM = NULL, DEVIATION_CLAIM_RATIONALE = NULL, DEVIATION_CLAIM_JUSTIFICATION = NULL,"
                + "UPDATED_AT = ? "
                + "WHERE CASE_ID = ? AND DELETED_AT IS NULL")) {
      int parameterIndex = 1;
      ps.setObject(parameterIndex++, currentTimestamp);
      ps.setString(parameterIndex++, payload.getCaseId());
      ps.execute();
    }
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE SOC_CASE_SYSTEM_EVALUATION "
                + "SET \"REFERENCES\" = NULL, SYSTEM_ISSUE = NULL, SYSTEM_ISSUE_RATIONALE = NULL, "
                + "SYSTEM_ISSUE_JUSTIFICATION = NULL, UPDATED_AT = ? "
                + "WHERE CASE_ID = ?")) {
      int parameterIndex = 1;
      ps.setObject(parameterIndex++, currentTimestamp);
      ps.setString(parameterIndex++, payload.getCaseId());
      ps.execute();
    }

    // add assignment to case
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE SOC_CASE SET USER_ID = ?, UPDATED_AT = ?, ASSIGNED_AT = ? WHERE CASE_ID = ?")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, payload.getUserId());
      ps.setObject(parameterIndex++, currentTimestamp);
      ps.setObject(parameterIndex++, currentTimestamp);
      ps.setString(parameterIndex++, payload.getCaseId());
      ps.execute();
    }

    // update workflow
    SocWorkflow newW = new SocWorkflow();
    newW.setCaseId(latestSocWorkflow.getCaseId());
    newW.setGuid(UUID.randomUUID().toString());
    newW.setIsLatest(true);
    newW.setRecipientUserId(payload.getUserId());
    newW.setSendingUserId(latestSocWorkflow.getRecipientUserId());
    newW.setSmssTimestamp(currentTimestamp);
    if (payload.getUserId() == null) {
      newW.setStepStatus(TQMCConstants.CASE_STEP_STATUS_UNASSIGNED);
    } else {
      newW.setStepStatus(TQMCConstants.CASE_STEP_STATUS_NOT_STARTED);
    }
    newW.setCaseType(SocCaseType.PEER_REVIEW.getCaseType());
    TQMCHelper.updateSocWorkflow(con, newW);

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

    if (payload.getUserId() != null) {
      try {
        TQMCHelper.sendAssignmentEmail(
            TQMCConstants.SOC,
            TQMCProperties.getInstance().getTqmcUrl(),
            currentSocCase.getCaseId(),
            false,
            aliasRecordId,
            assignee.getEmail(),
            tqmcUserInfo.getEmail(),
            ConversionUtils.getLocalDateStringFromDateSlashes(dueDate));
      } catch (TQMCException e) {
        LOGGER.warn(
            "Email failed for soc case assignment for case "
                + currentSocCase.getCaseId()
                + " and assignee "
                + assignee.getUserId(),
            e);
      }
    }

    return new NounMetadata(true, PixelDataType.BOOLEAN);
  }

  private void loadAndValidate(Connection con) throws SQLException {
    if (!hasProductManagementPermission(TQMCConstants.SOC)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);

    currentSocCase = TQMCHelper.getSocCase(con, payload.getCaseId());
    if (currentSocCase == null || currentSocCase.getDeletedAt() != null) {
      throw new TQMCException(ErrorCode.NOT_FOUND, "Case not found");
    }

    latestSocWorkflow = TQMCHelper.getLatestSocWorkflow(con, payload.getCaseId());
    if (TQMCConstants.COMPLETE_STATUS.equals(latestSocWorkflow.getStepStatus())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Case already complete");
    }

    assignee = TQMCHelper.getTQMCUserInfo(con, payload.getUserId());
    if (payload.getUserId() != null) {
      if (assignee == null || !assignee.getIsActive()) {
        throw new TQMCException(ErrorCode.NOT_FOUND, "User not found");
      }

      if (assignee.getProducts() == null
          || !assignee.getProducts().contains(TQMCConstants.SOC)
          || !TQMCConstants.PEER_REVIEWER.equals(assignee.getRole())
          || assignee.getNpi() == null) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "User is not eligible for this case assignment");
      }

      if (assignee.getUserId().equals(currentSocCase.getUserId())) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "Cannot reassign to the same assignee as before");
      }

      recordId =
          TQMCHelper.getRecordId(
              con, payload.getCaseId(), ProductTables.SOC, TQMCConstants.CASE_TYPE_PEER_REVIEW);
      if (!TQMCHelper.hasFilesForRecord(con, recordId, ProductTables.SOC)) {
        throw new TQMCException(ErrorCode.FORBIDDEN, "Cannot assign when record has no files");
      }
    }

    aliasRecordId =
        TQMCHelper.getAliasRecordId(
            con, payload.getCaseId(), ProductTables.SOC, TQMCConstants.CASE_TYPE_PEER_REVIEW);
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
