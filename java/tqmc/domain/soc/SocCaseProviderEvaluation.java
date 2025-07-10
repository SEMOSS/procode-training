package tqmc.domain.soc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public class SocCaseProviderEvaluation {

  @JsonProperty("case_provider_evaluation_id")
  private String caseProviderEvaluationId;

  private String caseId;

  @JsonProperty("provider_name")
  private String providerName;

  @JsonProperty("standards_met")
  private String standardsMet;

  @JsonProperty("standards_met_rationale")
  private String standardsMetRationale;

  @JsonProperty("standards_met_justification")
  private String standardsMetJustification;

  @JsonProperty("deviation_claim")
  private String deviationClaim;

  @JsonProperty("deviation_claim_rationale")
  private String deviationClaimRationale;

  @JsonProperty("deviation_claim_justification")
  private String deviationClaimJustification;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime deletedAt;

  public SocCaseProviderEvaluation() {}

  public String getCaseProviderEvaluationId() {
    return caseProviderEvaluationId;
  }

  public void setCaseProviderEvaluationId(String caseProviderEvaluationId) {
    this.caseProviderEvaluationId = caseProviderEvaluationId;
  }

  public String getCaseId() {
    return caseId;
  }

  public void setCaseId(String caseId) {
    this.caseId = caseId;
  }

  public String getProviderName() {
    return providerName;
  }

  public void setProviderName(String providerName) {
    this.providerName = providerName;
  }

  public String getStandardsMet() {
    return standardsMet;
  }

  public void setStandardsMet(String standardMet) {
    this.standardsMet = standardMet;
  }

  public String getStandardsMetRationale() {
    return standardsMetRationale;
  }

  public void setStandardsMetRationale(String standardMetRationale) {
    this.standardsMetRationale = standardMetRationale;
  }

  public String getStandardsMetJustification() {
    return standardsMetJustification;
  }

  public void setStandardsMetJustification(String standardMetJustification) {
    this.standardsMetJustification = standardMetJustification;
  }

  public String getDeviationClaim() {
    return deviationClaim;
  }

  public void setDeviationClaim(String deviationClaim) {
    this.deviationClaim = deviationClaim;
  }

  public String getDeviationClaimRationale() {
    return deviationClaimRationale;
  }

  public void setDeviationClaimRationale(String deviationClaimRationale) {
    this.deviationClaimRationale = deviationClaimRationale;
  }

  public String getDeviationClaimJustification() {
    return deviationClaimJustification;
  }

  public void setDeviationClaimJustification(String deviationClaimJustification) {
    this.deviationClaimJustification = deviationClaimJustification;
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

  public LocalDateTime getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }
}
