package tqmc.reactors.base;

import java.sql.Connection;
import java.sql.SQLException;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;

public class GetMonthsReactor extends AbstractTQMCReactor {

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    return new NounMetadata(TQMCConstants.MONTH_WORDS, PixelDataType.VECTOR);
  }
}
