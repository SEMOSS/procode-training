package tqmc.reactors.gtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.AdverseEvent;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.gtt.GttIrrMatch;
import tqmc.domain.gtt.GttRecordCaseEvent;
import tqmc.domain.gtt.GttWorkflow;
import tqmc.domain.mapper.CustomMapper;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class UpdateGttConsensusCaseReactor extends AbstractTQMCReactor {

  private static final String COMPLETE = "complete";
  private static final String UPDATED_AT = "updatedAt";
  private static final String MATCH_EVENTS = "matchEvents";

  private LocalDateTime currentTimestamp = ConversionUtils.getUTCFromLocalNow();

  public UpdateGttConsensusCaseReactor() {
    this.keysToGet = new String[] {TQMCConstants.CASE_ID, COMPLETE, UPDATED_AT, MATCH_EVENTS};
    this.keyRequired = new int[] {1, 0, 1, 1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    if (!hasProductPermission(TQMCConstants.GTT)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);
    if (Boolean.TRUE == payload.getComplete() && payload.getMatchEvents().isEmpty()) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Events are required to complete case");
    }

    LocalDateTime currentUpdatedAt =
        TQMCHelper.getGttCaseUpdatedAt(
            con, payload.getCaseId(), TQMCConstants.TABLE_GTT_CONSENSUS_CASE);
    if (currentUpdatedAt != null && !currentUpdatedAt.equals(payload.getUpdatedAt())) {
      throw new TQMCException(ErrorCode.CONFLICT);
    } else if (currentUpdatedAt == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND, "Case not found");
    }

    GttWorkflow gttWorkflow = TQMCHelper.getLatestGttWorkflow(con, payload.getCaseId());
    if (gttWorkflow == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND);
    }

    if (!TQMCConstants.GTT_CASE_TYPE_CONSENSUS.equalsIgnoreCase(gttWorkflow.getCaseType())
        || TQMCConstants.CASE_STEP_STATUS_COMPLETED.equalsIgnoreCase(gttWorkflow.getStepStatus())
        || (!hasRole(TQMCConstants.ADMIN) && !gttWorkflow.getVisibleTo().contains(userId))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    GttWorkflow newWorkflow = null;
    if (Boolean.TRUE == payload.getComplete()
        || TQMCConstants.CASE_STEP_STATUS_NOT_STARTED.equalsIgnoreCase(
            gttWorkflow.getStepStatus())) {
      newWorkflow = new GttWorkflow();
      newWorkflow.setCaseId(gttWorkflow.getCaseId());
      newWorkflow.setCaseType(gttWorkflow.getCaseType());
      newWorkflow.setGuid(UUID.randomUUID().toString());
      newWorkflow.setIsLatest(true);
      newWorkflow.setRecipientStage(gttWorkflow.getRecipientStage());
      newWorkflow.setRecipientUserId(TQMCConstants.DEFAULT_SYSTEM_USER);
      newWorkflow.setSendingStage(gttWorkflow.getRecipientStage());
      newWorkflow.setSendingUserId(gttWorkflow.getRecipientUserId());
      newWorkflow.setSmssTimestamp(currentTimestamp);
      newWorkflow.setStepStatus(
          Boolean.TRUE == payload.getComplete()
              ? TQMCConstants.CASE_STEP_STATUS_COMPLETED
              : TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS);
    }

    boolean allEventsHaveSelection = true;
    List<GttRecordCaseEvent> newEvents = new ArrayList<>();
    Map<String, String> eventDescriptions = new HashMap<>();
    List<GttIrrMatch> matches = new ArrayList<>();

    for (Entry<String, MatchEvent> matchEntry : payload.getMatchEvents().entrySet()) {
      String irrMatchId = matchEntry.getKey();
      MatchEvent matchEvent = matchEntry.getValue();

      GttIrrMatch match = new GttIrrMatch();
      match.setIrrMatchId(irrMatchId);
      match.setConsensusCaseId(payload.getCaseId());
      match.setIsDeleted(matchEvent.getIsDeleted());
      matches.add(match);

      // sync any description changes
      String abstractorOneEventId =
          matchEvent.getAbstractorOneEvent() == null
              ? null
              : matchEvent.getAbstractorOneEvent().getEventId();
      String abstractorTwoEventId =
          matchEvent.getAbstractorTwoEvent() == null
              ? null
              : matchEvent.getAbstractorTwoEvent().getEventId();
      if (matchEvent.getAbstractorOneEvent() != null) {
        eventDescriptions.put(
            abstractorOneEventId, matchEvent.getAbstractorOneEvent().getDescription());
      }
      if (matchEvent.getAbstractorTwoEvent() != null) {
        eventDescriptions.put(
            abstractorTwoEventId, matchEvent.getAbstractorTwoEvent().getDescription());
      }

      // alternate event update
      boolean hasNewAlternateEvent = false;
      AdverseEvent alternateEvent = matchEvent.getAlternateRecordCaseEvent();
      String alternateEventId = alternateEvent == null ? null : alternateEvent.getEventId();
      if (alternateEvent == null) {
        match.setAlternateRecordCaseEventId(null);
      } else {
        throwExceptionIfInvalidEvent(alternateEvent, payload.getComplete());

        if (alternateEventId == null) {
          hasNewAlternateEvent = true;
          alternateEventId = UUID.randomUUID().toString();
          alternateEvent.setEventId(alternateEventId);
        }

        String getAETypeStr =
            (alternateEvent.getAdverseEventType() != null)
                ? alternateEvent.getAdverseEventType().getValue()
                : null;
        String getLOHarmStr =
            (alternateEvent.getLevelOfHarm() != null)
                ? alternateEvent.getLevelOfHarm().getValue()
                : null;
        String getTrigStr =
            (alternateEvent.getTrigger() != null) ? alternateEvent.getTrigger().getValue() : null;

        GttRecordCaseEvent event = new GttRecordCaseEvent();
        event.setCaseId(payload.getCaseId());
        event.setCaseType(TQMCConstants.GTT_CASE_TYPE_CONSENSUS);
        event.setEventDescription(alternateEvent.getDescription());
        event.setEventTypeTemplateId(getAETypeStr);
        event.setHarmCategoryTemplateId(getLOHarmStr);
        event.setPresentOnAdmission(alternateEvent.getPresentOnAdmission());
        event.setRecordCaseEventId(alternateEvent.getEventId());
        event.setTriggerTemplateId(getTrigStr);
        event.setUpdatedAt(currentTimestamp);
        newEvents.add(event);

        match.setAlternateRecordCaseEventId(alternateEventId);
      }

      // changing the selected event
      AdverseEvent selectedEvent = matchEvent.getSelectedEvent();
      String selectedEventId = selectedEvent == null ? null : selectedEvent.getEventId();
      if (selectedEvent == null
          || selectedEventId == null && !hasNewAlternateEvent
          || selectedEventId != null
              && !selectedEventId.equals(abstractorOneEventId)
              && !selectedEventId.equals(abstractorTwoEventId)
              && !selectedEventId.equals(alternateEventId)) {
        // no selected event ok when deleting
        allEventsHaveSelection &= matchEvent.getIsDeleted();
        match.setSelectedCaseEventId(null);
      } else {
        match.setSelectedCaseEventId(selectedEventId == null ? alternateEventId : selectedEventId);
      }
    }

    if (Boolean.TRUE == payload.getComplete()) {
      if (!allEventsHaveSelection) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "Cannot submit case without event selections completed");
      }
      String physicianCaseId =
          TQMCHelper.getGttPhysicianCaseIdForConsensusCaseId(con, payload.getCaseId());
      for (Entry<String, MatchEvent> matchEntry : payload.getMatchEvents().entrySet()) {
        MatchEvent matchEvent = matchEntry.getValue();
        AdverseEvent adverseEvent = matchEvent.getSelectedEvent();

        if (adverseEvent != null && !matchEvent.getIsDeleted()) {

          throwExceptionIfInvalidEvent(adverseEvent, payload.getComplete());

          String getAETypeStr =
              (adverseEvent.getAdverseEventType() != null)
                  ? adverseEvent.getAdverseEventType().getValue()
                  : null;
          String getLOHarmStr =
              (adverseEvent.getLevelOfHarm() != null)
                  ? adverseEvent.getLevelOfHarm().getValue()
                  : null;
          String getTrigStr =
              (adverseEvent.getTrigger() != null) ? adverseEvent.getTrigger().getValue() : null;

          GttRecordCaseEvent e = new GttRecordCaseEvent();
          e.setCaseId(physicianCaseId);
          e.setCaseType(TQMCConstants.GTT_CASE_TYPE_PHYSICIAN);
          e.setEventDescription(adverseEvent.getDescription());
          e.setEventTypeTemplateId(getAETypeStr);
          e.setHarmCategoryTemplateId(getLOHarmStr);
          e.setPresentOnAdmission(adverseEvent.getPresentOnAdmission());
          e.setConsensusLinkCaseEventId(adverseEvent.getEventId());
          e.setRecordCaseEventId(UUID.randomUUID().toString());
          e.setTriggerTemplateId(getTrigStr);
          e.setUpdatedAt(currentTimestamp);
          newEvents.add(e);
        }
      }
    }

    TQMCHelper.createNewGttWorkflowEntry(con, newWorkflow);
    TQMCHelper.updateGttRecordCaseEventDescriptions(con, eventDescriptions, currentTimestamp);
    TQMCHelper.updateGttRecordCaseEventsForCase(
        con, newEvents, matches, payload.getCaseId(), TQMCConstants.GTT_CASE_TYPE_CONSENSUS);

    TQMCHelper.updateGttCaseUpdatedAt(
        con, payload.getCaseId(), TQMCConstants.TABLE_GTT_CONSENSUS_CASE, currentTimestamp);

    return new NounMetadata(
        CustomMapper.MAPPER.convertValue(
            payload.getMatchEvents(), new TypeReference<Map<String, Object>>() {}),
        PixelDataType.MAP);
  }

  private void throwExceptionIfInvalidEvent(AdverseEvent selectedEvent, Boolean completingCase) {
    if (selectedEvent.getAdverseEventType() != null
        && selectedEvent.getAdverseEventType().getValue().equals(TQMCConstants.NO_ADVERSE_EVENT_ID)
        && (selectedEvent.getLevelOfHarm() != null
            || selectedEvent.getPresentOnAdmission() != null)) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST, "Level of Harm and POA must be empty when AE 43: None");
    }
    if (selectedEvent.getTrigger() == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "An event trigger is required to save");
    }
    if (completingCase
        && (selectedEvent.getDescription() == null || selectedEvent.getDescription().isEmpty())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Description is required for selected events");
    }
    if (Boolean.TRUE == completingCase) {
      if (selectedEvent.getAdverseEventType() == null
          || (selectedEvent.getAdverseEventType() != null
              && !selectedEvent
                  .getAdverseEventType()
                  .getValue()
                  .equals(TQMCConstants.NO_ADVERSE_EVENT_ID)
              && (selectedEvent.getLevelOfHarm() == null
                  || selectedEvent.getPresentOnAdmission() == null))
          || selectedEvent.getDescription() == null) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST,
            "Event type, harm category, trigger selections and description are required to submit");
      }
    }
  }

  public static class Payload {
    private String caseId;
    private Boolean complete;
    private LocalDateTime updatedAt;
    private Map<String, MatchEvent> matchEvents;

    public Payload() {}

    public String getCaseId() {
      return caseId;
    }

    public void setCaseId(String caseId) {
      this.caseId = caseId;
    }

    public Boolean getComplete() {
      return complete;
    }

    public void setComplete(Boolean complete) {
      this.complete = complete;
    }

    public LocalDateTime getUpdatedAt() {
      return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
      this.updatedAt = updatedAt;
    }

    public Map<String, MatchEvent> getMatchEvents() {
      return matchEvents;
    }

    public void setMatchEvents(Map<String, MatchEvent> matchEvents) {
      this.matchEvents = matchEvents;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class MatchEvent {
    @JsonProperty("abstractor_one_event")
    private AdverseEvent abstractorOneEvent;

    @JsonProperty("abstractor_two_event")
    private AdverseEvent abstractorTwoEvent;

    @JsonProperty("alternate_record_case_event")
    private AdverseEvent alternateRecordCaseEvent;

    @JsonProperty("selected_event")
    private AdverseEvent selectedEvent;

    @JsonProperty("is_deleted")
    private boolean isDeleted;

    public MatchEvent() {}

    public AdverseEvent getAbstractorOneEvent() {
      return abstractorOneEvent;
    }

    public void setAbstractorOneEvent(AdverseEvent abstractorOneEvent) {
      this.abstractorOneEvent = abstractorOneEvent;
    }

    public AdverseEvent getAbstractorTwoEvent() {
      return abstractorTwoEvent;
    }

    public void setAbstractorTwoEvent(AdverseEvent abstractorTwoEvent) {
      this.abstractorTwoEvent = abstractorTwoEvent;
    }

    public AdverseEvent getSelectedEvent() {
      return selectedEvent;
    }

    public void setSelectedEvent(AdverseEvent selectedEvent) {
      this.selectedEvent = selectedEvent;
    }

    public boolean getIsDeleted() {
      return isDeleted;
    }

    public void setDeleted(boolean isDeleted) {
      this.isDeleted = isDeleted;
    }

    public AdverseEvent getAlternateRecordCaseEvent() {
      return alternateRecordCaseEvent;
    }

    public void setAlternateRecordCaseEvent(AdverseEvent alternateRecordCaseEvent) {
      this.alternateRecordCaseEvent = alternateRecordCaseEvent;
    }
  }
}
