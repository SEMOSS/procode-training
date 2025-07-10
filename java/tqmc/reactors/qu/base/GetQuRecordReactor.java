package tqmc.reactors.qu.base;

import java.sql.Connection;
import java.sql.SQLException;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetQuRecordReactor extends AbstractTQMCReactor {

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  public GetQuRecordReactor() {
    this.keysToGet = new String[] {TQMCConstants.RECORD_ID};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    if (!(hasProductManagementPermission(TQMCConstants.DP)
        || hasProductManagementPermission(TQMCConstants.MCSC))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    String recordId = this.keyValue.get(TQMCConstants.RECORD_ID);

    return new NounMetadata(TQMCHelper.getQuRecord(con, recordId), PixelDataType.MAP);
  }
}
