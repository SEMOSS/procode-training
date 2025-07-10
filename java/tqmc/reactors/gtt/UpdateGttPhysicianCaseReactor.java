package tqmc.reactors.gtt;

import com.fasterxml.jackson.core.type.TypeReference;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.AdverseEvent;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.gtt.GttRecordCaseEvent;
import tqmc.domain.gtt.GttWorkflow;
import tqmc.domain.mapper.CustomMapper;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class UpdateGttPhysicianCaseReactor extends AbstractTQMCReactor {

  private static final String COMPLETE = "complete";
  private static final String UPDATED_AT = "updatedAt";
  private static final String EVENTS = "events";

  private LocalDateTime currentTimestamp = ConversionUtils.getUTCFromLocalNow();

  public UpdateGttPhysicianCaseReactor() {
    this.keysToGet = new String[] {TQMCConstants.CASE_ID, COMPLETE, UPDATED_AT, EVENTS};
    this.keyRequired = new int[] {1, 0, 1, 1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    if (!hasProductPermission(TQMCConstants.GTT)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);

    if (Boolean.TRUE == payload.getComplete() && payload.getEvents().isEmpty()) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Events are required to complete case");
    }

    for (AdverseEvent ae : payload.getEvents()) {
      if (ae.getAdverseEventType() != null
          && ae.getAdverseEventType().getValue().equals(TQMCConstants.NO_ADVERSE_EVENT_ID)
          && (ae.getLevelOfHarm() != null || ae.getPresentOnAdmission() != null)) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "Level of Harm and POA must be empty when AE 43: None");
      }
      if (ae.getTrigger() == null) {
        throw new TQMCException(ErrorCode.BAD_REQUEST, "An event trigger is required to save");
      }
      if (Boolean.TRUE == payload.getComplete()) {
        if (ae.getAdverseEventType() == null
            || (ae.getAdverseEventType() != null
                && !ae.getAdverseEventType().getValue().equals(TQMCConstants.NO_ADVERSE_EVENT_ID)
                && (ae.getLevelOfHarm() == null || ae.getPresentOnAdmission() == null))
            || ae.getDescription() == null) {
          throw new TQMCException(
              ErrorCode.BAD_REQUEST,
              "Event type, harm category, trigger selections and description are required to submit");
        }
      }
    }

    GttWorkflow w = TQMCHelper.getLatestGttWorkflow(con, payload.getCaseId());
    if (w == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND);
    }

    if (!TQMCConstants.GTT_CASE_TYPE_PHYSICIAN.equalsIgnoreCase(w.getCaseType())
        || TQMCConstants.CASE_STEP_STATUS_COMPLETED.equalsIgnoreCase(w.getStepStatus())
        || (!hasRole(TQMCConstants.ADMIN) && !w.getVisibleTo().contains(userId))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    LocalDateTime currentUpdatedAt =
        TQMCHelper.getGttCaseUpdatedAt(
            con, payload.getCaseId(), TQMCConstants.TABLE_GTT_PHYSICIAN_CASE);
    if (currentUpdatedAt != null && !currentUpdatedAt.equals(payload.getUpdatedAt())) {
      throw new TQMCException(ErrorCode.CONFLICT);
    } else if (currentUpdatedAt == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND, "Case not found");
    }

    GttWorkflow newW = null;
    if (Boolean.TRUE == payload.getComplete()
        || TQMCConstants.CASE_STEP_STATUS_NOT_STARTED.equalsIgnoreCase(w.getStepStatus())) {
      newW = new GttWorkflow();
      newW.setCaseId(w.getCaseId());
      newW.setCaseType(w.getCaseType());
      newW.setGuid(UUID.randomUUID().toString());
      newW.setIsLatest(true);
      newW.setRecipientStage(w.getRecipientStage());
      newW.setRecipientUserId(userId);
      newW.setSendingStage(w.getRecipientStage());
      newW.setSendingUserId(w.getRecipientUserId());
      newW.setSmssTimestamp(currentTimestamp);
      newW.setStepStatus(
          Boolean.TRUE == payload.getComplete()
              ? TQMCConstants.CASE_STEP_STATUS_COMPLETED
              : TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS);
    }

    List<GttRecordCaseEvent> newEvents = new ArrayList<>();
    for (AdverseEvent ae : payload.getEvents()) {
      GttRecordCaseEvent e = new GttRecordCaseEvent();

      String getAETypeStr =
          (ae.getAdverseEventType() != null) ? ae.getAdverseEventType().getValue() : null;
      String getLOHarmStr = (ae.getLevelOfHarm() != null) ? ae.getLevelOfHarm().getValue() : null;
      String getTrigStr = (ae.getTrigger() != null) ? ae.getTrigger().getValue() : null;

      if (ae.getEventId() == null) {
        ae.setEventId(UUID.randomUUID().toString());
      }

      e.setCaseId(payload.getCaseId());
      e.setCaseType(TQMCConstants.GTT_CASE_TYPE_PHYSICIAN);
      e.setEventDescription(ae.getDescription());
      e.setEventTypeTemplateId(getAETypeStr);
      e.setHarmCategoryTemplateId(getLOHarmStr);
      e.setPresentOnAdmission(ae.getPresentOnAdmission());
      e.setRecordCaseEventId(ae.getEventId());
      e.setTriggerTemplateId(getTrigStr);
      e.setUpdatedAt(currentTimestamp);
      e.setIsDeleted(ae.getIsDeleted());
      newEvents.add(e);
    }

    TQMCHelper.createNewGttWorkflowEntry(con, newW);
    TQMCHelper.updateGttRecordCaseEventsForCase(
        con, newEvents, null, payload.getCaseId(), TQMCConstants.GTT_CASE_TYPE_PHYSICIAN);
    TQMCHelper.updateGttCaseUpdatedAt(
        con, payload.getCaseId(), TQMCConstants.TABLE_GTT_PHYSICIAN_CASE, currentTimestamp);

    return new NounMetadata(
        CustomMapper.MAPPER.convertValue(
            payload.getEvents(), new TypeReference<List<Map<String, Object>>>() {}),
        PixelDataType.VECTOR);
  }

  public static class Payload {
    private String caseId;
    private Boolean complete;
    private LocalDateTime updatedAt;
    private List<AdverseEvent> events;

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

    public List<AdverseEvent> getEvents() {
      return events;
    }

    public void setEvents(List<AdverseEvent> events) {
      this.events = events;
    }
  }
}
