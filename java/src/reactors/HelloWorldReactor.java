package reactors;

import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import util.Constants;

public class HelloWorldReactor extends AbstractProjectReactor {

  // Note: Has access to protected variables defined in AbstractProjectReactor

  @Override
  protected NounMetadata doExecute() {

    // grabbing user from AbstractProjectReactor
    String response = Constants.HELLO_WORLD + " " + user.getPrimaryLoginToken().getId();

    return new NounMetadata(response, PixelDataType.CONST_STRING);
  }
}
