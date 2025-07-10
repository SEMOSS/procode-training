package tqmc.domain.base;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CaseQuestion {
  @JsonProperty("case_id")
  private String caseId;

  public CaseQuestion() {}

  public String getCaseId() {
    return caseId;
  }

  public void setCaseId(String caseId) {
    this.caseId = caseId;
  }
}
