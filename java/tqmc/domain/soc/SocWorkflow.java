package tqmc.domain.soc;

import tqmc.domain.base.Workflow;

public class SocWorkflow extends Workflow {
  private String reopenReason;
  private String specialtyId;
  private String caseType;

  public String getReopenReason() {
    return reopenReason;
  }

  public void setReopenReason(String reopenReason) {
    this.reopenReason = reopenReason;
  }

  public String getSpecialtyId() {
    return specialtyId;
  }

  public void setSpecialtyId(String specialtyId) {
    this.specialtyId = specialtyId;
  }

  public String getCaseType() {
    return caseType;
  }

  public void setCaseType(String caseType) {
    this.caseType = caseType;
  }
}
