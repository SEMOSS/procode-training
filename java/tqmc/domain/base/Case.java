package tqmc.domain.base;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Case {

  @JsonProperty("case_id")
  protected String caseId;

  public Case() {}

  public Case(String caseId) {
    this.caseId = caseId;
  }

  public String getCaseId() {
    return caseId;
  }

  public void setCaseId(String caseId) {
    this.caseId = caseId;
  }
}
