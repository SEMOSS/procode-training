package tqmc.domain.base;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Provider {
  @JsonProperty("case_provider_evaluation_id")
  private String caseProviderEvaluationId;

  @JsonProperty("provider_name")
  private String providerName;

  @JsonProperty("specialty_id")
  private String specialtyId;

  @JsonProperty("case_assigned")
  private boolean caseAssigned;

  public String getCaseProviderEvaluationId() {
    return caseProviderEvaluationId;
  }

  public void setCaseProviderEvaluationId(String caseProviderEvaluationId) {
    this.caseProviderEvaluationId = caseProviderEvaluationId;
  }

  public String getProviderName() {
    return providerName;
  }

  public void setProviderName(String providerName) {
    this.providerName = providerName;
  }

  public String getSpecialtyId() {
    return specialtyId;
  }

  public void setSpecialtyId(String specialtyId) {
    this.specialtyId = specialtyId;
  }

  public boolean isCaseAssigned() {
    return caseAssigned;
  }

  public void setCaseAssigned(boolean caseAssigned) {
    this.caseAssigned = caseAssigned;
  }
}
