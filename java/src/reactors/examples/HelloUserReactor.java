package reactors.examples;

import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.ReactorKeysEnum;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import reactors.AbstractProjectReactor;

/**
 * Example reactor that demonstrates basic user interaction. Accepts optional name parameter or uses
 * current user's name.
 */
public class HelloUserReactor extends AbstractProjectReactor {

  // Note: Has access to protected variables defined in AbstractProjectReactor

  public HelloUserReactor() {

    // list of keys the reactor is expecting
    this.keysToGet = new String[] {ReactorKeysEnum.NAME.getKey()};

    // 1 for required keys, 0 for optional
    this.keyRequired = new int[] {0};
  }

  @Override
  protected NounMetadata doExecute() {

    // returns null if the argument is not found
    String name = this.keyValue.get(ReactorKeysEnum.NAME.getKey());

    // if name is not provided, use the user's name
    name = (name == null) ? user.getPrimaryLoginToken().getName() : name;

    // grabbing user from AbstractProjectReactor
    String response = "Hello, " + name + "! Welcome to SEMOSS.";

    return new NounMetadata(response, PixelDataType.CONST_STRING);
  }
}
