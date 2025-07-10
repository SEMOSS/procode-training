package tqmc.reactors.base;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.MTFData;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCHelper;

public class GetMTFsReactor extends AbstractTQMCReactor {

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    List<MTFData> output = TQMCHelper.getMtfs(con);
    return new NounMetadata(output, PixelDataType.VECTOR);
  }
}
