package tqmc.domain.soc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import tqmc.domain.base.Case;

public class SocCase extends Case {

  public SocCase() {}

  public SocCase(String caseId) {
    super(caseId);
  }

  @JsonProperty("assigned_at")
  private LocalDateTime assignedAt;

  @JsonProperty("attestation_signature")
  private String attestationSignature;

  @JsonProperty("attested_at")
  private LocalDateTime attestedAt;

  @JsonProperty("attestation_specialty")
  private String attestationSpecialty;

  @JsonProperty("attestation_subspecialty")
  private String attestationSubspecialty;

  @JsonProperty("created_at")
  private LocalDateTime createdAt;

  private LocalDateTime deletedAt;

  @JsonProperty("due_date_dha")
  private LocalDate dueDateDha;

  @JsonProperty("due_date_review")
  private LocalDate dueDateReview;

  @JsonProperty("record_id")
  private String recordId;

  private String reopeningReason;

  @JsonProperty("specialty_id")
  private String specialtyId;

  @JsonProperty("updated_at")
  private LocalDateTime updatedAt;

  @JsonProperty("user_id")
  private String userId;

  private Boolean complete;

  @JsonProperty("provider_evaluations")
  private List<SocCaseProviderEvaluation> providerEvaluations;

  private String submissionGuid;

  @JsonProperty("system_evaluation")
  private SocCaseSystemEvaluation systemEvalution;

  public String getAttestationSpecialty() {
    return attestationSpecialty;
  }

  public void setAttestationSpecialty(String attestationSpecialty) {
    this.attestationSpecialty = attestationSpecialty;
  }

  public String getAttestationSubspecialty() {
    return attestationSubspecialty;
  }

  public void setAttestationSubspecialty(String attestationSubspecialty) {
    this.attestationSubspecialty = attestationSubspecialty;
  }

  public LocalDate getDueDateDha() {
    return dueDateDha;
  }

  public void setDueDateDha(LocalDate dueDateDha) {
    this.dueDateDha = dueDateDha;
  }

  public LocalDate getDueDateReview() {
    return dueDateReview;
  }

  public void setDueDateReview(LocalDate dueDateReview) {
    this.dueDateReview = dueDateReview;
  }

  public String getSubmissionGuid() {
    return submissionGuid;
  }

  public void setSubmissionGuid(String submissionGuid) {
    this.submissionGuid = submissionGuid;
  }

  public LocalDateTime getAssignedAt() {
    return assignedAt;
  }

  public void setAssignedAt(LocalDateTime assignedAt) {
    this.assignedAt = assignedAt;
  }

  public String getAttestationSignature() {
    return attestationSignature;
  }

  public void setAttestationSignature(String attestationSignature) {
    this.attestationSignature = attestationSignature;
  }

  public LocalDateTime getAttestedAt() {
    return attestedAt;
  }

  public void setAttestedAt(LocalDateTime attestedAt) {
    this.attestedAt = attestedAt;
  }

  public String getCaseId() {
    return super.caseId;
  }

  public void setCaseId(String caseId) {
    super.caseId = caseId;
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

  public String getSpecialtyId() {
    return specialtyId;
  }

  public void setSpecialtyId(String specialtyId) {
    this.specialtyId = specialtyId;
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

  public List<SocCaseProviderEvaluation> getProviderEvaluations() {
    return providerEvaluations;
  }

  public void setProviderEvaluations(List<SocCaseProviderEvaluation> providerEvaluations) {
    this.providerEvaluations = providerEvaluations;
  }

  public SocCaseSystemEvaluation getSystemEvalution() {
    return systemEvalution;
  }

  public void setSystemEvaluation(SocCaseSystemEvaluation systemEvalution) {
    this.systemEvalution = systemEvalution;
  }
}
