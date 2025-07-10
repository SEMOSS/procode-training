package tqmc.reactors.gtt;

import java.sql.Connection;
import java.sql.SQLException;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class ReassignGttCaseReactor extends AbstractTQMCReactor {
  public ReassignGttCaseReactor() {
    this.keysToGet =
        new String[] {
          TQMCConstants.CASE_ID, TQMCConstants.CASE_TYPE, TQMCConstants.NEW_ASSIGNEE_USER_ID
        };
    this.keyRequired = new int[] {1, 1, 1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    // Assigning the keys passed in to variables
    String caseId = this.keyValue.get(TQMCConstants.CASE_ID);
    String caseType = this.keyValue.get(TQMCConstants.CASE_TYPE);
    String newAssigneeUserId = this.keyValue.get(TQMCConstants.NEW_ASSIGNEE_USER_ID);

    if (!hasProductManagementPermission(TQMCConstants.GTT)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    // Checking the permissions of the current logged in user and the new assignee
    TQMCHelper.checkReassignGttPermissions(con, caseId, caseType, newAssigneeUserId);
    TQMCHelper.onReassignUpdateGttWorkflowAndEditCases(
        con, caseId, caseType, userId, newAssigneeUserId);

    return new NounMetadata(true, PixelDataType.BOOLEAN);
  }
}
