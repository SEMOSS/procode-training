package util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TQMCHelper {
  public static int addIntegerExampleHelper(Connection con, int a, int b) throws SQLException {
    int output = 0;
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT ? + ? AS OUTPUT"); ) {
      int parameterIndex = 1;
      ps.setInt(parameterIndex++, a);
      ps.setInt(parameterIndex++, b);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          output = rs.getInt("OUTPUT");
        }
      }
    }
    return output;
  }
}
