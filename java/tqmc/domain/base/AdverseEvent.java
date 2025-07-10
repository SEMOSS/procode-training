package tqmc.domain.base;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AdverseEvent {
  @JsonProperty("event_id")
  private String eventId;

  private CategoricalOption trigger;

  @JsonProperty("adverse_event_type")
  private CategoricalOption adverseEventType;

  @JsonProperty("level_of_harm")
  private Option levelOfHarm;

  @JsonProperty("present_on_admission")
  private Boolean presentOnAdmission;

  @JsonProperty("is_deleted")
  private Boolean isDeleted;

  private String description;

  public AdverseEvent() {}

  public Boolean getIsDeleted() {
    return isDeleted;
  }

  public void setIsDeleted(Boolean isDeleted) {
    this.isDeleted = isDeleted;
  }

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public CategoricalOption getTrigger() {
    return trigger;
  }

  public void setTrigger(CategoricalOption trigger) {
    this.trigger = trigger;
  }

  public CategoricalOption getAdverseEventType() {
    return adverseEventType;
  }

  public void setAdverseEventType(CategoricalOption adverseEventType) {
    this.adverseEventType = adverseEventType;
  }

  public Option getLevelOfHarm() {
    return levelOfHarm;
  }

  public void setLevelOfHarm(Option levelOfHarm) {
    this.levelOfHarm = levelOfHarm;
  }

  public Boolean getPresentOnAdmission() {
    return presentOnAdmission;
  }

  public void setPresentOnAdmission(Boolean presentOnAdmission) {
    this.presentOnAdmission = presentOnAdmission;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
