package examples.reactors;

import examples.util.AnimalHelperMethods;
import examples.util.Constants;
import java.util.Map;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;

public class GetAnimalByIdReactor extends AbstractAnimalReactor {

  public GetAnimalByIdReactor() {
    this.keysToGet = new String[] {Constants.ANIMAL_ID};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute() {
    String animalId = this.keyValue.get(Constants.ANIMAL_ID);
    Map<String, Object> animalData = AnimalHelperMethods.getAnimalById(database, animalId).get(0);

    return new NounMetadata(animalData, PixelDataType.MAP);
  }
}
