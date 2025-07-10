package tqmc.domain.soc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public class SocCaseSystemEvaluation {

  @JsonProperty("case_system_evaluation_id")
  private String caseSystemEvaluationId;

  // should be passed at a different point in the payload
  private String caseId;

  @JsonProperty("references")
  private String references;

  @JsonProperty("system_issue")
  private String systemIssue;

  @JsonProperty("system_issue_rationale")
  private String systemIssueRationale;

  @JsonProperty("system_issue_justification")
  private String systemIssueJustification;

  @JsonProperty("created_at")
  private LocalDateTime createdAt;

  @JsonProperty("updated_at")
  private LocalDateTime updatedAt;

  public String getCaseId() {
    return caseId;
  }

  public void setCaseId(String caseId) {
    this.caseId = caseId;
  }

  // Getter and Setter for systemIssueJustification
  public String getSystemIssueJustification() {
    return systemIssueJustification;
  }

  public void setSystemIssueJustification(String systemIssueJustification) {
    this.systemIssueJustification = systemIssueJustification;
  }

  // Getter and Setter for references
  public String getReferences() {
    return references;
  }

  public void setReferences(String references) {
    this.references = references;
  }

  // Getter and Setter for systemIssue
  public String getSystemIssue() {
    return systemIssue;
  }

  public void setSystemIssue(String systemIssue) {
    this.systemIssue = systemIssue;
  }

  // Getter and Setter for caseSystemEvaluationId
  public String getCaseSystemEvaluationId() {
    return caseSystemEvaluationId;
  }

  public void setCaseSystemEvaluationId(String caseSystemEvaluationId) {
    this.caseSystemEvaluationId = caseSystemEvaluationId;
  }

  // Getter and Setter for systemIssueRationale
  public String getSystemIssueRationale() {
    return systemIssueRationale;
  }

  public void setSystemIssueRationale(String systemIssueRationale) {
    this.systemIssueRationale = systemIssueRationale;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
