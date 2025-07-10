package tqmc.reactors.soc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;

public class AprilFourLinkFilesReactor extends AbstractTQMCReactor {

  String caseFileId = "CASE_FILE_ID";
  String recordFileId = "RECORD_FILE_ID";
  String caseId = "CASE_ID";

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    if (!hasProductManagementPermission(TQMCConstants.SOC)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    List<Map<String, String>> output = new ArrayList<>();
    try (PreparedStatement query =
        con.prepareStatement(
            "SELECT srf.RECORD_FILE_ID, sc.CASE_ID FROM SOC_RECORD_FILE AS srf INNER JOIN SOC_CASE AS sc ON sc.RECORD_ID = srf.RECORD_ID WHERE sc.DELETED_AT IS NULL AND srf.DELETED_AT IS NULL"); ) {
      if (query.execute()) {
        ResultSet rs = query.getResultSet();
        while (rs.next()) {
          Map<String, String> map = new HashMap<String, String>();
          map.put(recordFileId, rs.getString(recordFileId));
          map.put(caseId, rs.getString(caseId));
          map.put(caseFileId, UUID.randomUUID().toString());
          output.add(map);
        }
      }
    }

    try (PreparedStatement insertQuery =
        con.prepareStatement(
            "INSERT INTO SOC_CASE_FILE (CASE_FILE_ID, RECORD_FILE_ID, CASE_ID) VALUES (?, ?, ?)")) {
      for (Map<String, String> row : output) {
        int i = 1;
        insertQuery.setString(i++, row.get(caseFileId));
        insertQuery.setString(i++, row.get(recordFileId));
        insertQuery.setString(i++, row.get(caseId));
        insertQuery.addBatch();
      }
      insertQuery.executeBatch();
    }

    try (PreparedStatement updateQuery =
        con.prepareStatement("UPDATE SOC_RECORD_FILE SET SHOW_NURSE_REVIEW = TRUE")) {
      updateQuery.execute();
    }

    return new NounMetadata(output, PixelDataType.VECTOR);
  }
}
