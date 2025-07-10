package tqmc.reactors.gtt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.gtt.GttWorkflow;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class EditGttCaseNoteReactor extends AbstractTQMCReactor {

  public EditGttCaseNoteReactor() {
    this.keysToGet = new String[] {TQMCConstants.NOTE_ID, TQMCConstants.NOTE};
    this.keyRequired = new int[] {1, 1};
  }

  private String caseId = null;

  private void checkPermissions(Connection con) throws SQLException {
    if (!hasProductPermission(TQMCConstants.GTT)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    String noteAuthor = null;

    try (PreparedStatement checkUser =
        con.prepareStatement(
            "SELECT USER_ID, CASE_ID FROM GTT_CASE_NOTE n WHERE n.CASE_NOTE_ID = ?"); ) {
      checkUser.setString(1, this.keyValue.get(TQMCConstants.NOTE_ID));
      ResultSet resultSet = checkUser.executeQuery();
      if (resultSet.next()) {
        noteAuthor = resultSet.getString("USER_ID");
        this.caseId = resultSet.getString("CASE_ID");
      } else {
        throw new TQMCException(ErrorCode.NOT_FOUND);
      }
    }
    if (caseId == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND);
    }
    if (!hasRole(TQMCConstants.MANAGEMENT_LEAD)
        && !TQMCHelper.hasGttCaseAccess(con, userId, caseId)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }
    if (!userId.equalsIgnoreCase(noteAuthor)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    checkPermissions(con);

    GttWorkflow wf = TQMCHelper.getLatestGttWorkflow(con, caseId);
    if (!TQMCConstants.CASE_STEP_STATUS_IN_PROGRESS.equalsIgnoreCase(wf.getStepStatus())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST);
    }

    // Update the note
    String currTimeStamp = ConversionUtils.getUTCFromLocalNow().toString();
    String noteId = this.keyValue.get(TQMCConstants.NOTE_ID);
    String updatedNote = this.keyValue.get(TQMCConstants.NOTE);
    try (PreparedStatement updateQuery =
        con.prepareStatement(
            "UPDATE GTT_CASE_NOTE SET NOTE = ?, UPDATED_AT = ? WHERE CASE_NOTE_ID = ?")) {
      updateQuery.setString(1, updatedNote);
      updateQuery.setString(2, currTimeStamp);
      updateQuery.setString(3, noteId);
      updateQuery.executeUpdate();
    }
    return new NounMetadata(true, PixelDataType.BOOLEAN);
  }
}
