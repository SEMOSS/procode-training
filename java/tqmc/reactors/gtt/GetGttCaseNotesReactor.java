package tqmc.reactors.gtt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetGttCaseNotesReactor extends AbstractTQMCReactor {

  public GetGttCaseNotesReactor() {
    this.keysToGet = new String[] {TQMCConstants.CASE_ID, TQMCConstants.CASE_TYPE};
    this.keyRequired = new int[] {1, 1};
  }

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    String caseId = this.keyValue.get(TQMCConstants.CASE_ID);
    String caseType = this.keyValue.get(TQMCConstants.CASE_TYPE);

    // check correct case type
    if (!TQMCConstants.GTT_CASE_TYPE_ABSTRACTOR.equals(caseType)
        && !TQMCConstants.GTT_CASE_TYPE_CONSENSUS.equals(caseType)
        && !TQMCConstants.GTT_CASE_TYPE_PHYSICIAN.equals(caseType)) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, caseType + " is an invalid case type.");
    }

    // check for access to the case
    if (!hasProductPermission(TQMCConstants.GTT)
        || (!hasProductManagementPermission(TQMCConstants.GTT)
            && !TQMCHelper.hasGttCaseAccess(con, userId, caseId))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    // return will be a list of maps getting a bulk return of all notes
    // order by creation time
    List<HashMap<String, Object>> result = new ArrayList<HashMap<String, Object>>();
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT note.CASE_NOTE_ID, note.NOTE, note.IS_EXTERNAL, note.CREATED_AT, note.UPDATED_AT, "
                + "note.USER_ID, tu.FIRST_NAME, tu.LAST_NAME "
                + "FROM GTT_CASE_NOTE note LEFT JOIN TQMC_USER tu ON note.USER_ID = tu.USER_ID "
                + "WHERE CASE_ID = ? AND CASE_TYPE = ?"
                + "ORDER BY CREATED_AT")) {
      ps.setString(1, caseId);
      ps.setString(2, caseType);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        HashMap<String, Object> row;
        while (rs.next()) {
          // take sql output and store it in result by proxy rows
          row = new HashMap<String, Object>();
          row.put("note_id", rs.getString("CASE_NOTE_ID"));
          row.put("note", rs.getString("NOTE"));
          row.put("is_external", rs.getInt("IS_EXTERNAL") == 1);
          row.put(
              "created_at",
              ConversionUtils.getLocalDateTimeStringFromTimestamp(rs.getTimestamp("CREATED_AT")));
          row.put(
              "updated_at",
              ConversionUtils.getLocalDateTimeStringFromTimestamp(rs.getTimestamp("UPDATED_AT")));
          row.put("author_user_id", rs.getString("USER_ID"));
          row.put("author_first_name", rs.getString("FIRST_NAME"));
          row.put("author_last_name", rs.getString("LAST_NAME"));

          result.add(row);
        }
      }
    }
    return new NounMetadata(result, PixelDataType.VECTOR);
  }
}
