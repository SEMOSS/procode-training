package tqmc.reactors.mn;

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
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;

public class GetCareContractorsReactor extends AbstractTQMCReactor {

  private final String query = "SELECT * FROM " + TQMCConstants.TABLE_MN_CARE_CONTRACTORS;

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    if (!hasProductPermission(TQMCConstants.MN)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    List<Map<String, String>> output = new ArrayList<>();

    try (PreparedStatement ps = con.prepareStatement(query)) {

      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          String stepCCID = rs.getString("CARE_CONTRACTOR_ID");
          String stepCCN = rs.getString("CARE_CONTRACTOR_NAME");
          Map<String, String> row = new HashMap<>();
          row.put("value", stepCCID);
          row.put("display", stepCCN);
          output.add(row);
        }
      }
    }

    output.sort(
        (s1, s2) -> {
          return s1.get("display").compareTo(s2.get("display"));
        });

    return new NounMetadata(output, PixelDataType.MAP);
  }
}
