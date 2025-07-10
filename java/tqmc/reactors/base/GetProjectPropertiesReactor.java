package tqmc.reactors.base;

import java.sql.Connection;
import java.sql.SQLException;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;

public class GetProjectPropertiesReactor extends AbstractTQMCReactor {

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    if (!hasRole(TQMCConstants.MANAGEMENT_LEAD)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    return new NounMetadata(this.tqmcProperties, PixelDataType.MAP);
  }
}
