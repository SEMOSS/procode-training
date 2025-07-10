package tqmc.domain.qu.dp;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import tqmc.domain.qu.base.QuQuestionResponse;

public class DpCase {
  @JsonProperty("assigned_at")
  private LocalDateTime assignedAt;

  @JsonProperty("case_id")
  private String caseId;

  @JsonProperty("created_at")
  private LocalDateTime createdAt;

  @JsonProperty("case_notes")
  private String caseNotes;

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

  @JsonProperty("audit_responses")
  private List<QuQuestionResponse> auditResponses;

  @JsonProperty("requested_at")
  private LocalDateTime requestedAt;

  @JsonProperty("received_at")
  private LocalDateTime receivedAt;

  public DpCase() {}

  public DpCase(
      String caseId,
      LocalDateTime assignedAt,
      LocalDateTime createdAt,
      LocalDateTime caseUpdatedAt,
      LocalDateTime deletedAt,
      String recordId,
      String userId,
      LocalDateTime requestedAt,
      LocalDateTime receivedAt,
      String reopeningReason) {
    this.caseId = caseId;
    this.assignedAt = assignedAt;
    this.createdAt = createdAt;
    this.updatedAt = caseUpdatedAt;
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

  public String getCaseNotes() {
    return caseNotes;
  }

  public void setCaseNotes(String caseNotes) {
    this.caseNotes = caseNotes;
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

  public List<QuQuestionResponse> getAuditResponses() {
    return auditResponses;
  }

  public void setAuditResponses(List<QuQuestionResponse> auditResponses) {
    this.auditResponses = auditResponses;
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
}
