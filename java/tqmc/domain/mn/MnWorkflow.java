package tqmc.domain.mn;

import tqmc.domain.base.Workflow;

public class MnWorkflow extends Workflow {
  private String reopenReason;
  private String appealTypeId;
  private String specialtyId;

  public String getReopenReason() {
    return reopenReason;
  }

  public void setReopenReason(String reopenReason) {
    this.reopenReason = reopenReason;
  }

  public String getAppealTypeId() {
    return appealTypeId;
  }

  public void setAppealTypeId(String appealTypeId) {
    this.appealTypeId = appealTypeId;
  }

  public String getSpecialtyId() {
    return specialtyId;
  }

  public void setSpecialtyId(String specialtyId) {
    this.specialtyId = specialtyId;
  }
}
