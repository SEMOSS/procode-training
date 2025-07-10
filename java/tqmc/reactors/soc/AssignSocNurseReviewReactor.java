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
import tqmc.domain.soc.SocCaseType;
import tqmc.domain.soc.SocNurseReview;
import tqmc.domain.soc.SocWorkflow;
import tqmc.domain.user.TQMCUserInfo;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;
import tqmc.util.TQMCProperties;

public class AssignSocNurseReviewReactor extends AbstractTQMCReactor {
  private static final Logger LOGGER = LogManager.getLogger(AssignSocNurseReviewReactor.class);

  private LocalDateTime currentTimestamp = ConversionUtils.getUTCFromLocalNow();
  private Payload payload;
  private SocNurseReview currentNurseReview;
  private SocWorkflow latestSocWorkflow;
  private TQMCUserInfo assignee;
  private String recordId;
  private String aliasRecordId;

  public AssignSocNurseReviewReactor() {
    this.keysToGet = new String[] {TQMCConstants.NURSE_REVIEW_ID, TQMCConstants.USER_ID};
    this.keyRequired = new int[] {1, 1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    loadAndValidate(con);

    // change assignment to case
    // nurse will continue the previous work
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE SOC_NURSE_REVIEW SET USER_ID = ?, UPDATED_AT = ?, ASSIGNED_AT = ? WHERE NURSE_REVIEW_ID = ?")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, payload.getUserId());
      ps.setObject(parameterIndex++, currentTimestamp);
      ps.setObject(parameterIndex++, currentTimestamp);
      ps.setString(parameterIndex++, payload.getNurseReviewId());
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
    newW.setCaseType(SocCaseType.NURSE_REVIEW.getCaseType());
    // Even if the previous nurse started, state the new nurse has not started
    if (payload.getUserId() == null) {
      newW.setStepStatus(TQMCConstants.CASE_STEP_STATUS_UNASSIGNED);
    } else {
      newW.setStepStatus(TQMCConstants.CASE_STEP_STATUS_NOT_STARTED);
    }
    TQMCHelper.updateSocWorkflow(con, newW);

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

    if (payload.getUserId() != null) {
      try {
        TQMCHelper.sendAssignmentEmail(
            "soc chronology",
            TQMCProperties.getInstance().getTqmcUrl(),
            currentNurseReview.getNurseReviewId(),
            false,
            aliasRecordId,
            assignee.getEmail(),
            tqmcUserInfo.getEmail(),
            ConversionUtils.getLocalDateStringFromDateSlashes(dueDate));
      } catch (TQMCException e) {
        LOGGER.warn(
            "Email failed for soc nurse review assignment for case "
                + currentNurseReview.getNurseReviewId()
                + " and assignee "
                + assignee.getUserId(),
            e);
      }
      LOGGER.info(
          currentNurseReview.getNurseReviewId() + " review assigned to " + assignee.getUserId());
    }

    return new NounMetadata(true, PixelDataType.BOOLEAN);
  }

  private void loadAndValidate(Connection con) throws SQLException {
    if (!hasProductManagementPermission(TQMCConstants.SOC)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);

    currentNurseReview = TQMCHelper.getSocNurseReview(con, payload.getNurseReviewId());
    if (currentNurseReview == null || currentNurseReview.getDeletedAt() != null) {
      throw new TQMCException(ErrorCode.NOT_FOUND, "Review not found");
    }

    latestSocWorkflow = TQMCHelper.getLatestSocWorkflow(con, payload.getNurseReviewId());
    if (TQMCConstants.CASE_STEP_STATUS_COMPLETED.equals(latestSocWorkflow.getStepStatus())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Review already complete");
    }

    assignee = TQMCHelper.getTQMCUserInfo(con, payload.getUserId());
    if (assignee != null && !payload.userId.isEmpty()) {
      if (!assignee.getIsActive()) {
        throw new TQMCException(ErrorCode.NOT_FOUND, "User not active");
      }

      if (assignee.getProducts() == null
          || !assignee.getProducts().contains(TQMCConstants.SOC)
          || !(TQMCConstants.NURSE_REVIEWER.equals(assignee.getRole())
              || TQMCConstants.MANAGEMENT_LEAD.equals(assignee.getRole())
              || TQMCConstants.CONTRACTING_LEAD.equals(assignee.getRole()))
          || assignee.getNpi() == null) {

        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "User is not eligible for this review assignment");
      }
      if (assignee.getUserId().equals(currentNurseReview.getUserId())) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "Cannot reassign to the same assignee as before");
      }
      recordId =
          TQMCHelper.getRecordId(
              con,
              payload.getNurseReviewId(),
              ProductTables.SOC,
              TQMCConstants.CASE_TYPE_NURSE_REVIEW);
      if (!TQMCHelper.hasFilesForRecord(con, recordId, ProductTables.SOC)) {
        throw new TQMCException(ErrorCode.FORBIDDEN, "Cannot assign when record has no files");
      }
    }

    aliasRecordId =
        TQMCHelper.getAliasRecordId(
            con,
            payload.getNurseReviewId(),
            ProductTables.SOC,
            TQMCConstants.CASE_TYPE_NURSE_REVIEW);
  }

  public static class Payload {
    private String nurseReviewId;
    private String userId;

    public Payload() {}

    public String getNurseReviewId() {
      return nurseReviewId;
    }

    public void setNurseReviewId(String nurseReviewId) {
      this.nurseReviewId = nurseReviewId;
    }

    public String getUserId() {
      return userId;
    }

    public void setUserId(String userId) {
      this.userId = userId;
    }
  }
}
