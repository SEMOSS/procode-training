package tqmc.domain.base;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public class QualityReviewEvent {
  @JsonProperty("quality_review_event_id")
  private String qualityReviewEventId;

  private String caseId;

  @JsonProperty("trigger_template_id")
  private String triggerTemplateId;

  @JsonProperty("harm_category_template_id")
  private String harmCategoryTemplateId;

  @JsonProperty("event_description")
  private String eventDescription;

  private LocalDateTime updatedAt;

  public QualityReviewEvent() {}

  public String getQualityReviewEventId() {
    return qualityReviewEventId;
  }

  public void setQualityReviewEventId(String qualityReviewEventId) {
    this.qualityReviewEventId = qualityReviewEventId;
  }

  public String getCaseId() {
    return caseId;
  }

  public void setCaseId(String caseId) {
    this.caseId = caseId;
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
