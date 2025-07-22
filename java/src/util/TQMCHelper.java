package util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import domain.base.MTFData;

public class TQMCHelper {
  public static List<MTFData> getMtfs(Connection con) throws SQLException {
    List<MTFData> output = new ArrayList<>();
    try (PreparedStatement mtfQuery =
        con.prepareStatement(
            "SELECT DMIS_ID, MTF_NAME, COALESCE(alias_mtf_name, mtf_name) AS ALIAS_MTF_NAME FROM MTF ORDER BY LOWER(COALESCE(alias_mtf_name, mtf_name)) ASC"); ) {
      if (mtfQuery.execute()) {
        ResultSet rs = mtfQuery.getResultSet();
        while (rs.next()) {
          String id = rs.getString("DMIS_ID");
          String name = rs.getString("MTF_NAME");
          String alias = rs.getString("ALIAS_MTF_NAME");
          MTFData row = new MTFData(name, id, alias);
          output.add(row);
        }
      }
    }
    return output;
  }
}
