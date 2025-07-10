package tqmc.domain.qu.mcsc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import tqmc.domain.base.QualityReviewEvent;
import tqmc.domain.qu.base.QuQuestionResponse;

public class McscCase {
  @JsonProperty("assigned_at")
  private LocalDateTime assignedAt;

  @JsonProperty("case_id")
  private String caseId;

  @JsonProperty("created_at")
  private LocalDateTime createdAt;

  @JsonProperty("deleted_at")
  private LocalDateTime deletedAt;

  @JsonProperty("record_id")
  private String recordId;

  @JsonProperty("reopening_reason")
  private String reopeningReason;

  @JsonProperty("updated_at")
  private LocalDateTime updatedAt;

  @JsonProperty("user_id")
  private String userId;

  private Boolean complete;

  @JsonProperty("quality_review_complete")
  private Boolean qualityReviewComplete;

  @JsonProperty("utilization_responses")
  private List<QuQuestionResponse> utilizationResponses;

  @JsonProperty("quality_review_events")
  private List<QualityReviewEvent> qualityReviewEvents;

  @JsonProperty("requested_at")
  private LocalDateTime requestedAt;

  @JsonProperty("received_at")
  private LocalDateTime receivedAt;

  @JsonProperty("quality_notes")
  private String qualityNotes;

  @JsonProperty("utilization_notes")
  private String utilizationNotes;

  public McscCase() {}

  public McscCase(
      String caseId,
      LocalDateTime assignedAt,
      LocalDateTime createdAt,
      LocalDateTime updatedAt,
      LocalDateTime deletedAt,
      String recordId,
      String userId,
      LocalDateTime requestedAt,
      LocalDateTime receivedAt,
      String reopeningReason) {
    this.caseId = caseId;
    this.assignedAt = assignedAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.deletedAt = deletedAt;
    this.recordId = recordId;
    this.userId = userId;
    this.requestedAt = requestedAt;
    this.receivedAt = receivedAt;
    this.reopeningReason = reopeningReason;
  }

  public LocalDateTime getAssignedAt() {
    return assignedAt;
  }

  public void setAssignedAt(LocalDateTime assignedAt) {
    this.assignedAt = assignedAt;
  }

  public String getCaseId() {
    return caseId;
  }

  public void setCaseId(String caseId) {
    this.caseId = caseId;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }

  public String getRecordId() {
    return recordId;
  }

  public void setRecordId(String recordId) {
    this.recordId = recordId;
  }

  public String getReopeningReason() {
    return reopeningReason;
  }

  public void setReopeningReason(String reopeningReason) {
    this.reopeningReason = reopeningReason;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public Boolean getComplete() {
    return complete;
  }

  public void setComplete(Boolean complete) {
    this.complete = complete;
  }

  public Boolean getQualityReviewComplete() {
    return qualityReviewComplete;
  }

  public void setQualityReviewComplete(Boolean qualityReviewComplete) {
    this.qualityReviewComplete = qualityReviewComplete;
  }

  public List<QuQuestionResponse> getUtilizationResponses() {
    return utilizationResponses;
  }

  public void setUtilizationResponses(List<QuQuestionResponse> utilizationResponses) {
    this.utilizationResponses = utilizationResponses;
  }

  public List<QualityReviewEvent> getQualityReviewEvents() {
    return qualityReviewEvents;
  }

  public void setQualityReviewEvents(List<QualityReviewEvent> qualityReviewEvents) {
    this.qualityReviewEvents = qualityReviewEvents;
  }

  public LocalDateTime getRequestedAt() {
    return requestedAt;
  }

  public void setRequestedAt(LocalDateTime requestedAt) {
    this.requestedAt = requestedAt;
  }

  public LocalDateTime getReceivedAt() {
    return receivedAt;
  }

  public void setReceivedAt(LocalDateTime receivedAt) {
    this.receivedAt = receivedAt;
  }

  public String getQualityNotes() {
    return qualityNotes;
  }

  public void setQualityNotes(String qualityNotes) {
    this.qualityNotes = qualityNotes;
  }

  public String getUtilizationNotes() {
    return utilizationNotes;
  }

  public void setUtilizationNotes(String utilizationNotes) {
    this.utilizationNotes = utilizationNotes;
  }
}
