package tqmc.reactors.base;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;

public class GetTqmcMaintenanceReactor extends AbstractTQMCReactor {

  private static final String query =
      "SELECT tm.START_TIME, tm.END_TIME\n"
          + "FROM TQMC_MAINTENANCE tm\n"
          + "WHERE DATEDIFF(second, NOW() AT TIME ZONE 'UTC', CAST(tm.END_TIME AS DATETIME)) > 0\n"
          + "AND (tm.APPEARANCE_TIME IS NULL\n"
          + "OR DATEDIFF(second, NOW() AT TIME ZONE 'UTC', CAST(tm.APPEARANCE_TIME AS DATETIME)) < 0)\n"
          + "ORDER BY CAST(tm.START_TIME AS DATETIME) ASC\n"
          + "OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY\n";

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    Map<String, String> output = new HashMap<>();

    try (PreparedStatement ps = con.prepareStatement(query)) {

      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          output.put(
              "start_time",
              ConversionUtils.getLocalDateTimeStringFromTimestamp(rs.getTimestamp("START_TIME")));
          output.put(
              "end_time",
              ConversionUtils.getLocalDateTimeStringFromTimestamp(rs.getTimestamp("END_TIME")));
        }

        return new NounMetadata(output, PixelDataType.MAP);
      }
    }

    return new NounMetadata(null, PixelDataType.NULL_VALUE);
  }
}
