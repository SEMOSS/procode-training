package tqmc.domain.soc;

public enum SocCaseType {
  PEER_REVIEW("peer_review"),
  NURSE_REVIEW("nurse_review");

  private String caseType;

  private SocCaseType(String caseType) {
    this.caseType = caseType;
  }

  public String getCaseType() {
    return this.caseType;
  }
}
