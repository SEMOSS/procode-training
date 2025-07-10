package tqmc.reactors.base;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;

public class GetTqmcDocsReactor extends AbstractTQMCReactor {
  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    List<Map<String, Object>> results = new ArrayList<>();

    if (tqmcProperties.getDocsProjectId() != null) {
      try (PreparedStatement ps =
          con.prepareStatement(
              "SELECT NAME, UPDATED_AT, LINK, IS_VIDEO, DESCRIPTION FROM TQMC_DOCS ORDER BY ID ASC")) {
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
          Map<String, Object> result = new HashMap<>();
          result.put("name", rs.getString("NAME"));
          result.put(
              "updated_at",
              ConversionUtils.getLocalDateTimeStringFromTimestamp(rs.getTimestamp("UPDATED_AT")));
          result.put("link", rs.getString("LINK"));
          result.put("is_video", rs.getBoolean("IS_VIDEO"));
          result.put("description", rs.getString("DESCRIPTION"));
          results.add(result);
        }
      }
    }

    return new NounMetadata(results, PixelDataType.VECTOR);
  }
}
