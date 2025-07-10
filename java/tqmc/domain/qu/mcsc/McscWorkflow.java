package tqmc.domain.qu.mcsc;

import tqmc.domain.base.Workflow;

public class McscWorkflow extends Workflow {
  private String reopenReason;

  public String getReopenReason() {
    return reopenReason;
  }

  public void setReopenReason(String reopenReason) {
    this.reopenReason = reopenReason;
  }
}
