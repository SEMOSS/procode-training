package tqmc.domain.qu.dp;

import tqmc.domain.base.Workflow;

public class DpWorkflow extends Workflow {
  private String reopenReason;

  public String getReopenReason() {
    return reopenReason;
  }

  public void setReopenReason(String reopenReason) {
    this.reopenReason = reopenReason;
  }
}
