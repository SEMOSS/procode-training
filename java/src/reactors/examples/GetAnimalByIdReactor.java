package reactors.examples;

import java.util.Map;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import reactors.AbstractProjectReactor;
import util.Constants;
import util.HelperMethods;

public class GetAnimalByIdReactor extends AbstractProjectReactor {

  public GetAnimalByIdReactor() {
    this.keysToGet = new String[] {Constants.ANIMAL_ID};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute() {
    String animalId = this.keyValue.get(Constants.ANIMAL_ID);
    Map<String, Object> animalData = HelperMethods.getAnimalById(database, animalId).get(0);

    return new NounMetadata(animalData, PixelDataType.MAP);
  }
}
