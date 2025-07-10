package tqmc.domain.base;

public enum CaseStatus {
  UNASSIGNED("unassigned"),
  NOT_STARTED("not_started"),
  IN_PROGRESS("in_progress"),
  COMPLETED("completed");

  private String caseStatus;

  private CaseStatus(String input) {
    this.caseStatus = input;
  }

  public String getCaseStatus() {
    return this.caseStatus;
  }
}
