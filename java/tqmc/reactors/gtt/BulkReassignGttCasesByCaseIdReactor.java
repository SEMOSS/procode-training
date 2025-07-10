package tqmc.reactors.gtt;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.ProductTables;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class BulkReassignGttCasesByCaseIdReactor extends AbstractTQMCReactor {

  public BulkReassignGttCasesByCaseIdReactor() {
    this.keysToGet =
        new String[] {"caseIds", TQMCConstants.CASE_TYPE, TQMCConstants.NEW_ASSIGNEE_USER_ID};
    this.keyRequired = new int[] {1, 1, 1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);

    if (!hasProductManagementPermission(TQMCConstants.GTT)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    for (String caseId : payload.getCaseIds()) {
      try {
        TQMCHelper.checkReassignGttPermissions(
            con, caseId, payload.getCaseType(), payload.getNewAssigneeUserId());

        TQMCHelper.onReassignUpdateGttWorkflowAndEditCases(
            con, caseId, payload.getCaseType(), userId, payload.getNewAssigneeUserId());
      } catch (TQMCException e) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST,
            e.toString().split(":")[1]
                + " on record "
                + TQMCHelper.getRecordId(con, caseId, ProductTables.GTT));
      }
    }

    return new NounMetadata(true, PixelDataType.VECTOR);
  }

  public static class Payload {
    private List<String> caseIds;
    private String caseType;
    private String newAssigneeUserId;

    public List<String> getCaseIds() {
      return caseIds;
    }

    public void setCaseIds(List<String> caseIds) {
      this.caseIds = caseIds;
    }

    public String getCaseType() {
      return caseType;
    }

    public void setCaseType(String caseType) {
      this.caseType = caseType;
    }

    public String getNewAssigneeUserId() {
      return newAssigneeUserId;
    }

    public void setNewAssigneeUserId(String newAssigneeUserId) {
      this.newAssigneeUserId = newAssigneeUserId;
    }
  }
}
