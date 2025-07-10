package tqmc.reactors.qu.dp;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.qu.base.QuQuestionResponse;
import tqmc.domain.qu.dp.DpCase;
import tqmc.domain.qu.dp.DpWorkflow;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class UpdateDpCaseReactor extends AbstractTQMCReactor {

  private static final String DP_CASE = "dp_case";

  private final LocalDateTime localTime = ConversionUtils.getUTCFromLocalNow();

  public UpdateDpCaseReactor() {
    this.keysToGet = new String[] {DP_CASE};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);
    DpCase dpCase = payload.getDpCase();

    // check permissions
    if (!TQMCHelper.hasCaseAccess(con, userId, dpCase.getCaseId(), TQMCConstants.DP)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    // check input fields
    if (dpCase.getCaseId() == null
        || dpCase.getAuditResponses() == null
        || dpCase.getComplete() == null
        || dpCase.getUpdatedAt() == null) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST,
          "Invalid request: case_id, complete, audit_responses, and updated_at time are required");
    }

    Set<String> validResponseTemplateIds = new HashSet<>();
    try (PreparedStatement ps =
        con.prepareStatement("SELECT DISTINCT RESPONSE_TEMPLATE_ID FROM DP_RESPONSE_TEMPLATE")) {
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        validResponseTemplateIds.add(rs.getString("RESPONSE_TEMPLATE_ID"));
      }
    }

    for (QuQuestionResponse q : dpCase.getAuditResponses()) {
      if (q.getQuestionResponseId() == null
          || q.getQuestionTemplateId() == null
          || (q.getResponseTemplateId() != null
              && !validResponseTemplateIds.contains(q.getResponseTemplateId()))) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "Invalid request: missing or invalid required audit fields");
      }
    }

    // check if updatedAt matches DB
    String recentUpdate = null;
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT updated_at FROM DP_CASE WHERE case_id = ? AND DELETED_AT IS NULL")) {
      ps.setString(1, dpCase.getCaseId());
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          recentUpdate = ConversionUtils.getLocalDateTimeStringFromTimestamp(rs.getTimestamp(1));
        }
      }
    }

    if (!ConversionUtils.getLocalDateTimeString(dpCase.getUpdatedAt()).equals(recentUpdate)) {
      throw new TQMCException(ErrorCode.CONFLICT);
    }

    // check if case has already been completed
    DpWorkflow latestDpWorkflow = TQMCHelper.getLatestDpWorkflow(con, dpCase.getCaseId());
    if (TQMCConstants.CASE_STEP_STATUS_COMPLETED.equals(latestDpWorkflow.getStepStatus())) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST, "Invalid request: form has already been completed");
    }

    // get the current case questions that exist in the table for the inputted case
    Map<String, Map<String, String>> carq = new HashMap<>();
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT question_template_id, audit_response_id FROM DP_AUDIT_RESPONSE WHERE case_id = ?")) {
      ps.setString(1, dpCase.getCaseId());
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          Map<String, String> questionMap = new HashMap<>();
          questionMap.put("question_template_id", rs.getString("question_template_id"));
          questionMap.put("inputted", "no");

          carq.put(rs.getString("audit_response_id"), questionMap);
        }
      }
    }

    // check if the inputted questions match the database, and if so, update the response
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE DP_AUDIT_RESPONSE SET response_template_id = ? WHERE audit_response_id = ?")) {
      int count = 0;
      int responseCount = 0;
      for (QuQuestionResponse q : dpCase.getAuditResponses()) {
        if (carq.containsKey(q.getQuestionResponseId())
            && q.getQuestionTemplateId()
                .equals(carq.get(q.getQuestionResponseId()).get("question_template_id"))) {
          if (q.getResponseTemplateId() != null) {
            responseCount++;
          }
          int parameterIndex = 1;
          ps.setString(parameterIndex++, q.getResponseTemplateId());
          ps.setString(parameterIndex++, q.getQuestionResponseId());
          ps.addBatch();
          carq.get(q.getQuestionResponseId()).put("inputted", "yes");
          count++;
        } else {
          throw new TQMCException(
              ErrorCode.BAD_REQUEST, "Invalid request: questions do not match the database");
        }
      }
      // checks that each case question in the db has a corresponding input
      for (String cq : carq.keySet()) {
        if (carq.get(cq).get("inputted").equals("no")) {
          throw new TQMCException(
              ErrorCode.BAD_REQUEST, "Invalid request: questions do not match the database");
        }
      }
      // checks there is no duplicate questions
      if (count != carq.keySet().size()) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "Invalid request: questions do not match the database");
      }
      if (dpCase.getComplete() && responseCount != carq.keySet().size()) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST,
            "Invalid request: all questions must have responses when completed");
      }
      ps.executeBatch();
    }

    // update dp case table
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE DP_CASE SET CASE_NOTES = ?, UPDATED_AT = ? WHERE CASE_ID = ?")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, dpCase.getCaseNotes());
      ps.setObject(parameterIndex++, localTime);
      ps.setString(parameterIndex++, dpCase.getCaseId());
      ps.execute();
    }

    // if workflow is moving on to the next step, update the workflow table
    if (TQMCConstants.CASE_STEP_STATUS_NOT_STARTED.equals(latestDpWorkflow.getStepStatus())
        || dpCase.getComplete()) {

      DpWorkflow dpWorkflow = new DpWorkflow();
      dpWorkflow.setCaseId(dpCase.getCaseId());
      dpWorkflow.setGuid(UUID.randomUUID().toString());
      dpWorkflow.setIsLatest(true);
      dpWorkflow.setRecipientUserId(userId);
      dpWorkflow.setSendingUserId(latestDpWorkflow.getRecipientUserId());
      dpWorkflow.setSmssTimestamp(localTime);
      String stepStatus;
      if (dpCase.getComplete()) {
        stepStatus = TQMCConstants.CASE_STEP_STATUS_COMPLETED;
      } else {
        stepStatus = TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS;
      }
      String workflowNote =
          TQMCHelper.generateWorkflowNote(latestDpWorkflow.getStepStatus(), stepStatus, userId);
      dpWorkflow.setStepStatus(stepStatus);
      dpWorkflow.setWorkflowNotes(workflowNote);

      TQMCHelper.updateDpWorkflow(con, dpWorkflow);
    }

    Map<String, Object> returnMap = new HashMap<>();
    returnMap.put("case_id", dpCase.getCaseId());
    returnMap.put("complete", dpCase.getComplete());
    returnMap.put("audit_responses", dpCase.getAuditResponses());
    returnMap.put("case_notes", dpCase.getCaseNotes());
    returnMap.put("updated_at", ConversionUtils.getLocalDateTimeString(localTime));

    return new NounMetadata(returnMap, PixelDataType.MAP);
  }

  public static class Payload {
    @JsonProperty("dp_case")
    private DpCase dpCase;

    public DpCase getDpCase() {
      return this.dpCase;
    }

    public void setDpCase(DpCase dpCase) {
      this.dpCase = dpCase;
    }
  }
}
