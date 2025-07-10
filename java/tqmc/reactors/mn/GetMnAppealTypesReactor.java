package tqmc.reactors.mn;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;

public class GetMnAppealTypesReactor extends AbstractTQMCReactor {

  private final String query =
      "SELECT mat.APPEAL_TYPE_ID, mat.APPEAL_TYPE_NAME, mat.IS_SECOND_LEVEL FROM "
          + TQMCConstants.TABLE_MN_APPEAL_TYPE
          + " mat";

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    if (!hasProductPermission(TQMCConstants.MN)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Map<String, Map<String, Object>> output = new HashMap<>();

    try (PreparedStatement ps = con.prepareStatement(query)) {

      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          String appealId = rs.getString("APPEAL_TYPE_ID");
          String appealName = rs.getString("APPEAL_TYPE_NAME");
          Boolean isSecondLevel = rs.getInt("IS_SECOND_LEVEL") == 1;
          Map<String, Object> row = new HashMap<>();
          row.put("value", appealId);
          row.put("display", appealName);
          row.put("is_second_level", isSecondLevel);
          output.put(appealId, row);
        }
      }
    }

    return new NounMetadata(output, PixelDataType.MAP);
  }
}
