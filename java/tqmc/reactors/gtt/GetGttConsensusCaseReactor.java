package tqmc.reactors.gtt;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetGttConsensusCaseReactor extends AbstractTQMCReactor {

  public GetGttConsensusCaseReactor() {
    this.keysToGet = new String[] {TQMCConstants.CASE_ID};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    if (!hasProductPermission(TQMCConstants.GTT)) {
      throw new TQMCException(
          ErrorCode.FORBIDDEN, "User is unauthorized to perform this operation");
    }

    String caseId = this.keyValue.get(TQMCConstants.CASE_ID);
    Map<String, Object> c = TQMCHelper.getGttConsensusCaseMap(con, userId, caseId);

    return new NounMetadata(c, PixelDataType.MAP);
  }
}
