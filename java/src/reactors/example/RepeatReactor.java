package reactors.example;

import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.ReactorKeysEnum;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import reactors.AbstractProjectReactor;

public class RepeatReactor extends AbstractProjectReactor {

  // Note: Has access to protected variables defined in AbstractProjectReactor

  public RepeatReactor() {

    // list of keys the reactor is expecting
    this.keysToGet = new String[] {ReactorKeysEnum.COMMAND.getKey()};

    // 1 for required keys, 0 for optional
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute() {

    // returns null if the argument is not found
    String inputString = this.keyValue.get(ReactorKeysEnum.COMMAND.getKey());

    // grabbing user from AbstractProjectReactor
    String response = user.getPrimaryLoginToken().getId() + ": " + inputString;

    return new NounMetadata(response, PixelDataType.CONST_STRING);
  }
}
