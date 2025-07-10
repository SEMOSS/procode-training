package tqmc.domain.base;

import java.time.LocalDateTime;

public abstract class Workflow {
  private String caseId;
  private String guid;
  private Boolean isLatest;
  private String recipientUserId;
  private String sendingUserId;
  private String stepStatus;
  private LocalDateTime smssTimestamp;
  private String workflowNotes;

  public String getCaseId() {
    return caseId;
  }

  public void setCaseId(String caseId) {
    this.caseId = caseId;
  }

  public String getGuid() {
    return guid;
  }

  public void setGuid(String guid) {
    this.guid = guid;
  }

  public Boolean getIsLatest() {
    return isLatest;
  }

  public void setIsLatest(Boolean isLatest) {
    this.isLatest = isLatest;
  }

  public String getRecipientUserId() {
    return recipientUserId;
  }

  public void setRecipientUserId(String recipientUserId) {
    this.recipientUserId = recipientUserId;
  }

  public String getSendingUserId() {
    return sendingUserId;
  }

  public void setSendingUserId(String sendingUserId) {
    this.sendingUserId = sendingUserId;
  }

  public String getStepStatus() {
    return stepStatus;
  }

  public void setStepStatus(String stepStatus) {
    this.stepStatus = stepStatus;
  }

  public LocalDateTime getSmssTimestamp() {
    return smssTimestamp;
  }

  public void setSmssTimestamp(LocalDateTime smssTimestamp) {
    this.smssTimestamp = smssTimestamp;
  }

  public String getWorkflowNotes() {
    return workflowNotes;
  }

  public void setWorkflowNotes(String workflowNotes) {
    this.workflowNotes = workflowNotes;
  }
}
