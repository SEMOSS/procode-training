package tqmc.reactors.gtt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class May5DeleteOldGttRecordsReactor extends AbstractTQMCReactor {

  public May5DeleteOldGttRecordsReactor() {
    this.keysToGet = new String[] {"recordIds"};
    this.keyRequired = new int[] {1};
  }

  public static final List<String> QUERIES =
      Arrays.asList(
          "DELETE FROM GTT_WORKFLOW WHERE CASE_ID IN (SELECT gw.CASE_ID FROM GTT_WORKFLOW gw LEFT JOIN GTT_ABSTRACTOR_CASE gac ON gac.CASE_ID = gw.CASE_ID AND gw.CASE_TYPE = 'abstraction' LEFT JOIN GTT_CONSENSUS_CASE gcc ON gcc.CASE_ID = gw.CASE_ID AND gw.CASE_TYPE = 'consensus' LEFT JOIN GTT_PHYSICIAN_CASE gpc ON gpc.CASE_ID = gw.CASE_ID AND gw.CASE_TYPE = 'physician' WHERE gac.RECORD_ID IN (%s) OR gcc.RECORD_ID IN (%s) OR gpc.RECORD_ID IN (%s));",
          "DELETE FROM GTT_CASE_NOTE WHERE CASE_ID IN (SELECT gcn.CASE_ID FROM GTT_CASE_NOTE gcn LEFT JOIN GTT_ABSTRACTOR_CASE gac ON gac.CASE_ID = gcn.CASE_ID AND gcn.CASE_TYPE = 'abstraction' LEFT JOIN GTT_CONSENSUS_CASE gcc ON gcc.CASE_ID = gcn.CASE_ID AND gcn.CASE_TYPE = 'consensus' LEFT JOIN GTT_PHYSICIAN_CASE gpc ON gpc.CASE_ID = gcn.CASE_ID AND gcn.CASE_TYPE = 'physician' WHERE gac.RECORD_ID IN (%s) OR gcc.RECORD_ID IN (%s) OR gpc.RECORD_ID IN (%s));",
          "DELETE FROM GTT_CASE_TIME WHERE CASE_ID IN (SELECT gct.CASE_ID FROM GTT_CASE_TIME gct LEFT JOIN GTT_ABSTRACTOR_CASE gac ON gac.CASE_ID = gct.CASE_ID AND gct.CASE_TYPE = 'abstraction' LEFT JOIN GTT_CONSENSUS_CASE gcc ON gcc.CASE_ID = gct.CASE_ID AND gct.CASE_TYPE = 'consensus' LEFT JOIN GTT_PHYSICIAN_CASE gpc ON gpc.CASE_ID = gct.CASE_ID AND gct.CASE_TYPE = 'physician' WHERE gac.RECORD_ID IN (%s) OR gcc.RECORD_ID IN (%s) OR gpc.RECORD_ID IN (%s));",
          "DELETE FROM GTT_IRR_MATCH WHERE CONSENSUS_CASE_ID IN (SELECT gim.CONSENSUS_CASE_ID FROM GTT_IRR_MATCH gim INNER JOIN GTT_CONSENSUS_CASE gcc ON gcc.CASE_ID = gim.CONSENSUS_CASE_ID WHERE gcc.RECORD_ID IN (%s));",
          "DELETE FROM GTT_RECORD_CASE_EVENT WHERE CASE_ID IN (SELECT grce.CASE_ID FROM GTT_RECORD_CASE_EVENT grce LEFT JOIN GTT_ABSTRACTOR_CASE gac ON gac.CASE_ID = grce.CASE_ID AND grce.CASE_TYPE = 'abstraction' LEFT JOIN GTT_CONSENSUS_CASE gcc ON gcc.CASE_ID = grce.CASE_ID AND grce.CASE_TYPE = 'consensus' LEFT JOIN GTT_PHYSICIAN_CASE gpc ON gpc.CASE_ID = grce.CASE_ID AND grce.CASE_TYPE = 'physician' WHERE gac.RECORD_ID IN (%s) OR gcc.RECORD_ID IN (%s) OR gpc.RECORD_ID IN (%s));",
          "DELETE FROM GTT_ABSTRACTOR_CASE WHERE RECORD_ID IN (%s);",
          "DELETE FROM GTT_CONSENSUS_CASE WHERE RECORD_ID IN (%s);",
          "DELETE FROM GTT_PHYSICIAN_CASE WHERE RECORD_ID IN (%s);",
          "DELETE FROM TQMC_RECORD WHERE RECORD_ID IN (%s);");

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    if (!(hasProductManagementPermission(TQMCConstants.GTT))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);

    String placeholders = String.join(",", Collections.nCopies(payload.getRecordIds().size(), "?"));

    int totalCount = 0;
    for (String query : QUERIES) {
      int count = query.split("%s", -1).length - 1;
      String formattedQuery = query;
      for (int i = 0; i < count; i++) {
        formattedQuery = formattedQuery.replaceFirst("%s", placeholders);
      }
      try (PreparedStatement ps = con.prepareStatement(formattedQuery)) {
        int index = 1;
        for (int i = 0; i < count; i++) {
          for (String recordId : payload.getRecordIds()) {
            ps.setString(index++, recordId);
          }
        }
        totalCount += ps.executeUpdate();
      } catch (Exception e) {
        throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
      }
    }
    Map<String, Integer> out = new HashMap<>();
    out.put("rows_affected", totalCount);
    return new NounMetadata(out, PixelDataType.MAP);
  }

  public static class Payload {
    private List<String> recordIds;

    public List<String> getRecordIds() {
      return recordIds;
    }

    public void setRecordIds(List<String> recordIds) {
      this.recordIds = recordIds;
    }
  }
}
