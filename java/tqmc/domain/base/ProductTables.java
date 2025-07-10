package tqmc.domain.base;

import tqmc.util.TQMCConstants;

public enum ProductTables {
  GTT(TQMCConstants.GTT, "TQMC_RECORD", "GTT_WORKFLOW", "GTT_ABSTRACTOR_CASE", null),
  SOC(TQMCConstants.SOC, "SOC_RECORD", "SOC_WORKFLOW", "SOC_CASE", "SOC_RECORD_FILE"),
  MN(TQMCConstants.MN, "MN_RECORD", "MN_WORKFLOW", "MN_CASE", "MN_RECORD_FILE"),
  MCSC(TQMCConstants.MCSC, "QU_RECORD", "MCSC_WORKFLOW", "MCSC_CASE", "QU_RECORD_FILE"),
  DP(TQMCConstants.DP, "QU_RECORD", "DP_WORKFLOW", "DP_CASE", "QU_RECORD_FILE");

  private final String productId;
  private final String recordTable;
  private final String workflowTable;
  private final String caseTable;
  private final String fileTable;

  private ProductTables(
      String productId,
      String recordTable,
      String workflowTable,
      String caseTable,
      String fileTable) {
    this.productId = productId;
    this.recordTable = recordTable;
    this.workflowTable = workflowTable;
    this.caseTable = caseTable;
    this.fileTable = fileTable;
  }

  public String getProductId() {
    return productId;
  }

  public String getRecordTable() {
    return recordTable;
  }

  public String getWorkflowTable() {
    return workflowTable;
  }

  public String getCaseTable() {
    return caseTable;
  }

  public String getFileTable() {
    return fileTable;
  }

  public static ProductTables parseProductId(String productId) {
    for (ProductTables pt : ProductTables.values()) {
      if (pt.productId.equals(productId)) {
        return pt;
      }
    }
    return null;
  }
}
