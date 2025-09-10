package reactors.examples;

import java.sql.Connection;
import java.sql.SQLException;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import reactors.AbstractProjectReactor;
import util.Constants;

public class GetMonthsReactor extends AbstractProjectReactor {

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    return new NounMetadata(Constants.MONTH_WORDS, PixelDataType.VECTOR);
  }
}
