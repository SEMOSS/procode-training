package tqmc.reactors.base;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCHelper;

public class GetActiveProductsReactor extends AbstractTQMCReactor {

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  public NounMetadata doExecute(Connection con) throws SQLException {
    Set<String> result = TQMCHelper.getActiveProductIds(con);
    return new NounMetadata(result.toArray(new String[0]), PixelDataType.VECTOR);
  }
}
