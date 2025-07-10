package tqmc.reactors.gtt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.gtt.GttWorkflow;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class CreateGttCaseNoteReactor extends AbstractTQMCReactor {

  public CreateGttCaseNoteReactor() {
    this.keysToGet =
        new String[] {
          TQMCConstants.CASE_ID,
          TQMCConstants.CASE_TYPE,
          TQMCConstants.IS_EXTERNAL,
          TQMCConstants.NOTE
        };
    this.keyRequired = new int[] {1, 1, 1, 1};
  }

  String caseId;
  String caseType;
  String notes;
  Boolean isExternal;
  String currentTime;

  private void setProperties() {
    caseId = this.keyValue.get(TQMCConstants.CASE_ID);
    caseType = this.keyValue.get(TQMCConstants.CASE_TYPE);
    notes = this.keyValue.get(TQMCConstants.NOTE);
    isExternal = "true".equalsIgnoreCase(keyValue.get(TQMCConstants.IS_EXTERNAL));
    currentTime = ConversionUtils.getUTCFromLocalNow().toString();
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    setProperties();
    if (!hasProductPermission(TQMCConstants.GTT)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }
    if (!hasRole(TQMCConstants.MANAGEMENT_LEAD)
        && !TQMCHelper.hasGttCaseAccess(con, userId, caseId)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    GttWorkflow wf = TQMCHelper.getLatestGttWorkflow(con, caseId);
    if (!TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS.equalsIgnoreCase(wf.getStepStatus())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST);
    }

    try (PreparedStatement insertQuery =
        con.prepareStatement(
            "INSERT INTO GTT_CASE_NOTE (CASE_ID, CASE_NOTE_ID, CASE_TYPE, USER_ID, NOTE, IS_EXTERNAL, CREATED_AT, UPDATED_AT) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
      int i = 1;
      insertQuery.setString(i++, caseId);
      insertQuery.setString(i++, UUID.randomUUID().toString());
      insertQuery.setString(i++, caseType);
      insertQuery.setString(i++, userId);
      insertQuery.setString(i++, notes);
      insertQuery.setBoolean(i++, isExternal);
      insertQuery.setString(i++, currentTime);
      insertQuery.setString(i++, currentTime);
      insertQuery.execute();
    }
    return new NounMetadata(true, PixelDataType.BOOLEAN);
  }
}
