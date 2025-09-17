package reactors.examples.database;

import domain.base.ErrorCode;
import domain.base.ProjectException;
import java.util.List;
import java.util.Map;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import reactors.AbstractProjectReactor;
import util.Constants;
import util.HelperMethods;

// Get an animal by its ID!
public class GetAnimalByIdReactor extends AbstractProjectReactor {

  public GetAnimalByIdReactor() {
    this.keysToGet = new String[] {Constants.ANIMAL_ID};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute() {
    String animalId = this.keyValue.get(Constants.ANIMAL_ID);
    List<Map<String, Object>> animalData = HelperMethods.getAnimalById(database, animalId);
    if (animalData.isEmpty()) {
      throw new ProjectException(ErrorCode.NOT_FOUND, "Animal not found");
    }

    if (animalData.size() > 1) {
      throw new ProjectException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Multiple animals found with that id");
    }

    Map<String, Object> animal = animalData.get(0);

    return new NounMetadata(animal, PixelDataType.MAP);
  }
}
