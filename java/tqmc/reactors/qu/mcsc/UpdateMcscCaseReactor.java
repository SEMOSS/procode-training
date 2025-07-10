package tqmc.reactors.qu.mcsc;

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
import tqmc.domain.base.QualityReviewEvent;
import tqmc.domain.base.TQMCException;
import tqmc.domain.qu.base.QuQuestionResponse;
import tqmc.domain.qu.mcsc.McscCase;
import tqmc.domain.qu.mcsc.McscWorkflow;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class UpdateMcscCaseReactor extends AbstractTQMCReactor {

  private static final String MCSC_CASE = "mcsc_case";

  private final LocalDateTime localTime = ConversionUtils.getUTCFromLocalNow();

  public UpdateMcscCaseReactor() {
    this.keysToGet = new String[] {MCSC_CASE};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);
    McscCase mcscCase = payload.getMcscCase();

    // check permissions
    if (!TQMCHelper.hasCaseAccess(con, userId, mcscCase.getCaseId(), TQMCConstants.MCSC)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    // check input fields
    if (mcscCase.getCaseId() == null
        || mcscCase.getComplete() == null
        || mcscCase.getQualityReviewComplete() == null
        || mcscCase.getUpdatedAt() == null) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST,
          "Invalid request: case_id, events, complete(s), utilization responses, and updated_at time are required");
    }

    if (mcscCase.getQualityReviewComplete() && mcscCase.getQualityReviewEvents() == null) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST, "Quality review can not be submitted without notes");
    }

    if (mcscCase.getComplete() && mcscCase.getUtilizationResponses() == null) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST, "Utilization review can not be submitted without notes");
    }

    if (!mcscCase.getQualityReviewComplete() && mcscCase.getComplete()) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST, "Utilization review can not be completed without quality review");
    }

    String TRIGGER_QUERY = "SELECT DISTINCT TRIGGER_TEMPLATE_ID FROM GTT_TRIGGER_TEMPLATE";
    String HARM_QUERY = "SELECT DISTINCT HARM_CATEGORY_TEMPLATE_ID FROM QU_HARM_CATEGORY_TEMPLATE";
    String RESPONSE_QUERY = "SELECT DISTINCT RESPONSE_TEMPLATE_ID FROM MCSC_RESPONSE_TEMPLATE";

    Set<String> validTriggerTemplateIds = new HashSet<>();
    Set<String> validHarmTemplateIds = new HashSet<>();
    Set<String> validResponseTemplateIds = new HashSet<>();

    try (PreparedStatement psTrigger = con.prepareStatement(TRIGGER_QUERY);
        PreparedStatement psHarm = con.prepareStatement(HARM_QUERY);
        PreparedStatement psResponse = con.prepareStatement(RESPONSE_QUERY)) {
      ResultSet rsTrigger = psTrigger.executeQuery();
      ResultSet rsHarm = psHarm.executeQuery();
      ResultSet rsResponse = psResponse.executeQuery();
      while (rsTrigger.next()) {
        validTriggerTemplateIds.add(rsTrigger.getString("TRIGGER_TEMPLATE_ID"));
      }
      while (rsHarm.next()) {
        validHarmTemplateIds.add(rsHarm.getString("HARM_CATEGORY_TEMPLATE_ID"));
      }
      while (rsResponse.next()) {
        validResponseTemplateIds.add(rsResponse.getString("RESPONSE_TEMPLATE_ID"));
      }
    }

    if (mcscCase.getQualityReviewEvents() != null && !mcscCase.getQualityReviewEvents().isEmpty()) {
      for (QualityReviewEvent qre : mcscCase.getQualityReviewEvents()) {
        if (mcscCase.getQualityReviewComplete()) {
          if (qre.getTriggerTemplateId() == null
              || !validTriggerTemplateIds.contains(qre.getTriggerTemplateId())
              || qre.getHarmCategoryTemplateId() == null
              || !validHarmTemplateIds.contains(qre.getHarmCategoryTemplateId())
              || qre.getEventDescription() == null) {
            throw new TQMCException(
                ErrorCode.BAD_REQUEST,
                "Valid harm category, trigger selections, and description are required to submit");
          }
        }
        if (qre.getQualityReviewEventId() == null) {
          qre.setQualityReviewEventId(UUID.randomUUID().toString());
        }
        qre.setCaseId(mcscCase.getCaseId());
      }
    }

    if (mcscCase.getUtilizationResponses() != null
        && !mcscCase.getUtilizationResponses().isEmpty()) {
      for (QuQuestionResponse q : mcscCase.getUtilizationResponses()) {
        if (q.getQuestionResponseId() == null
            || q.getQuestionTemplateId() == null
            || (q.getResponseTemplateId() != null
                && !validResponseTemplateIds.contains(q.getResponseTemplateId()))) {
          throw new TQMCException(
              ErrorCode.BAD_REQUEST,
              "Invalid request: util responses must have question response ids and question ids");
        }
      }
    }

    // check if updatedAt matches DB
    String recentUpdate = null;
    try (PreparedStatement ps =
        con.prepareStatement("SELECT updated_at FROM MCSC_CASE WHERE case_id = ?")) {
      ps.setString(1, mcscCase.getCaseId());
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          recentUpdate = ConversionUtils.getLocalDateTimeStringFromTimestamp(rs.getTimestamp(1));
        }
      }
    }

    if (!recentUpdate.equals(ConversionUtils.getLocalDateTimeString(mcscCase.getUpdatedAt()))) {
      throw new TQMCException(ErrorCode.CONFLICT);
    }

    // check if case has already been completed
    McscWorkflow latestMcscWorkflow = TQMCHelper.getLatestMcscWorkflow(con, mcscCase.getCaseId());
    if (TQMCConstants.CASE_STEP_STATUS_COMPLETED.equals(latestMcscWorkflow.getStepStatus())) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST, "Invalid request: form has already been completed");
    }

    TQMCHelper.replaceMcscQualityReviewEventsForCase(
        con, mcscCase.getCaseId(), mcscCase.getQualityReviewEvents());

    if (mcscCase.getUtilizationResponses() != null
        && !mcscCase.getUtilizationResponses().isEmpty()) {
      // get the current case questions that exist in the table for the inputted case
      Map<String, Map<String, String>> curq = new HashMap<>();
      try (PreparedStatement ps =
          con.prepareStatement(
              "SELECT question_template_id, review_response_id FROM MCSC_REVIEW_RESPONSE WHERE case_id = ?")) {
        ps.setString(1, mcscCase.getCaseId());
        if (ps.execute()) {
          ResultSet rs = ps.getResultSet();
          while (rs.next()) {
            Map<String, String> questionMap = new HashMap<>();
            questionMap.put("question_template_id", rs.getString("question_template_id"));
            questionMap.put("inputted", "no");

            curq.put(rs.getString("review_response_id"), questionMap);
          }
        }
      }

      // check if the inputted questions match the database, and if so, update the response
      try (PreparedStatement ps =
          con.prepareStatement(
              "UPDATE MCSC_REVIEW_RESPONSE SET response_template_id = ? WHERE review_response_id = ?")) {
        int count = 0;
        int responseCount = 0;
        for (QuQuestionResponse q : mcscCase.getUtilizationResponses()) {
          if (curq.containsKey(q.getQuestionResponseId())
              && q.getQuestionTemplateId()
                  .equals(curq.get(q.getQuestionResponseId()).get("question_template_id"))) {
            if (q.getResponseTemplateId() != null) {
              responseCount++;
            }
            int parameterIndex = 1;
            ps.setString(parameterIndex++, q.getResponseTemplateId());
            ps.setString(parameterIndex++, q.getQuestionResponseId());
            ps.addBatch();
            curq.get(q.getQuestionResponseId()).put("inputted", "yes");
            count++;
          } else {
            throw new TQMCException(
                ErrorCode.BAD_REQUEST, "Invalid request: questions do not match the database");
          }
        }
        // checks that each case question in the db has a corresponding input
        for (String cq : curq.keySet()) {
          if (curq.get(cq).get("inputted").equals("no")) {
            throw new TQMCException(
                ErrorCode.BAD_REQUEST, "Invalid request: questions do not match the database");
          }
        }
        // checks there is no duplicate questions
        if (count != curq.keySet().size()) {
          throw new TQMCException(
              ErrorCode.BAD_REQUEST, "Invalid request: questions do not match the database");
        }
        if (mcscCase.getComplete() && responseCount != curq.keySet().size()) {
          throw new TQMCException(
              ErrorCode.BAD_REQUEST,
              "Invalid request: all questions must have responses when completed");
        }
        ps.executeBatch();
      }
    }

    // update mcsc case table
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE MCSC_CASE SET "
                + "QUALITY_NOTES = ?, "
                + "UTILIZATION_NOTES = ?, "
                + "QUALITY_REVIEW_COMPLETE = ?, "
                + "UPDATED_AT = ? "
                + "WHERE CASE_ID = ?")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, mcscCase.getQualityNotes());
      ps.setString(parameterIndex++, mcscCase.getUtilizationNotes());
      ps.setInt(parameterIndex++, Boolean.TRUE == mcscCase.getQualityReviewComplete() ? 1 : 0);
      ps.setObject(parameterIndex++, localTime);
      ps.setString(parameterIndex++, mcscCase.getCaseId());
      ps.execute();
    }

    // if workflow is moving on to the next step, update the workflow table
    if (TQMCConstants.CASE_STEP_STATUS_NOT_STARTED.equals(latestMcscWorkflow.getStepStatus())
        || mcscCase.getComplete()) {

      McscWorkflow mcscWorkflow = new McscWorkflow();
      mcscWorkflow.setCaseId(mcscCase.getCaseId());
      mcscWorkflow.setGuid(UUID.randomUUID().toString());
      mcscWorkflow.setIsLatest(true);
      mcscWorkflow.setRecipientUserId(userId);
      mcscWorkflow.setSendingUserId(latestMcscWorkflow.getRecipientUserId());
      mcscWorkflow.setSmssTimestamp(localTime);
      String stepStatus;
      if (mcscCase.getComplete()) {
        stepStatus = TQMCConstants.CASE_STEP_STATUS_COMPLETED;
      } else {
        stepStatus = TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS;
      }
      String workflowNote =
          TQMCHelper.generateWorkflowNote(latestMcscWorkflow.getStepStatus(), stepStatus, userId);
      mcscWorkflow.setStepStatus(stepStatus);
      mcscWorkflow.setWorkflowNotes(workflowNote);

      TQMCHelper.updateMcscWorkflow(con, mcscWorkflow);
    }

    Map<String, Object> returnMap = new HashMap<>();
    returnMap.put("case_id", mcscCase.getCaseId());
    returnMap.put("complete", mcscCase.getComplete());
    returnMap.put("quality_review_complete", mcscCase.getQualityReviewComplete());
    returnMap.put("quality_review_events", mcscCase.getQualityReviewEvents());
    returnMap.put("quality_notes", mcscCase.getQualityNotes());
    returnMap.put("utilization_responses", mcscCase.getUtilizationResponses());
    returnMap.put("utilization_notes", mcscCase.getUtilizationNotes());
    returnMap.put("updated_at", ConversionUtils.getLocalDateTimeString(localTime));

    return new NounMetadata(returnMap, PixelDataType.MAP);
  }

  public static class Payload {
    @JsonProperty("mcsc_case")
    private McscCase mcscCase;

    public McscCase getMcscCase() {
      return this.mcscCase;
    }

    public void setMcscCase(McscCase mcscCase) {
      this.mcscCase = mcscCase;
    }
  }
}
