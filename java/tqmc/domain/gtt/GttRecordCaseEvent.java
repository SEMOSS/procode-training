package tqmc.domain.gtt;

import java.time.LocalDateTime;

public class GttRecordCaseEvent {
  private String recordCaseEventId;
  private String caseId;
  private String eventTypeTemplateId;
  private String triggerTemplateId;
  private String harmCategoryTemplateId;
  private String caseType;
  private String consensusLinkCaseEventId; // TODO: how is this field intended to work?
  private Boolean presentOnAdmission;
  private String eventDescription;
  private LocalDateTime updatedAt;
  private Boolean isDeleted;

  public GttRecordCaseEvent() {}

  public Boolean getIsDeleted() {
    return isDeleted;
  }

  public void setIsDeleted(Boolean isDeleted) {
    this.isDeleted = isDeleted;
  }

  public String getRecordCaseEventId() {
    return recordCaseEventId;
  }

  public void setRecordCaseEventId(String recordCaseEventId) {
    this.recordCaseEventId = recordCaseEventId;
  }

  public String getCaseId() {
    return caseId;
  }

  public void setCaseId(String caseId) {
    this.caseId = caseId;
  }

  public String getEventTypeTemplateId() {
    return eventTypeTemplateId;
  }

  public void setEventTypeTemplateId(String eventTypeTemplateId) {
    this.eventTypeTemplateId = eventTypeTemplateId;
  }

  public String getTriggerTemplateId() {
    return triggerTemplateId;
  }

  public void setTriggerTemplateId(String triggerTemplateId) {
    this.triggerTemplateId = triggerTemplateId;
  }

  public String getHarmCategoryTemplateId() {
    return harmCategoryTemplateId;
  }

  public void setHarmCategoryTemplateId(String harmCategoryTemplateId) {
    this.harmCategoryTemplateId = harmCategoryTemplateId;
  }

  public String getCaseType() {
    return caseType;
  }

  public void setCaseType(String caseType) {
    this.caseType = caseType;
  }

  public String getConsensusLinkCaseEventId() {
    return consensusLinkCaseEventId;
  }

  public void setConsensusLinkCaseEventId(String consensusLinkCaseEventId) {
    this.consensusLinkCaseEventId = consensusLinkCaseEventId;
  }

  public Boolean getPresentOnAdmission() {
    return presentOnAdmission;
  }

  public void setPresentOnAdmission(Boolean presentOnAdmission) {
    this.presentOnAdmission = presentOnAdmission;
  }

  public String getEventDescription() {
    return eventDescription;
  }

  public void setEventDescription(String eventDescription) {
    this.eventDescription = eventDescription;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
