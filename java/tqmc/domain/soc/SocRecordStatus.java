package tqmc.domain.soc;

public enum SocRecordStatus {
  AWAITING_ASSIGNMENT("awaiting_assignment", "0"),
  ASSIGNED("assigned", "1"),
  COMPLETED("completed", "2");

  private String recordStatus;
  private String orderString;

  private SocRecordStatus(String recordStatus, String orderString) {
    this.recordStatus = recordStatus;
    this.orderString = orderString;
  }

  public String getRecordStatus() {
    return this.recordStatus;
  }

  public String getOrderString() {
    return this.orderString;
  }

  public static SocRecordStatus valueOfOrderString(String orderString) {
    for (SocRecordStatus status : SocRecordStatus.values()) {
      if (status.getOrderString().equals(orderString)) {
        return status;
      }
    }
    throw new IllegalArgumentException("No enum constant with orderString: " + orderString);
  }
}
