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

// Remove an animal from the database!
public class DeleteAnimalReactor extends AbstractProjectReactor {

  public DeleteAnimalReactor() {
    this.keysToGet = new String[] {Constants.ANIMAL_ID};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute() {
    String animalId = this.keyValue.get(Constants.ANIMAL_ID);

    // The below method will throw an exception if the animal does not exist
    List<Map<String, Object>> animals = HelperMethods.getAnimalById(database, animalId);
    if (animals.isEmpty()) {
      throw new ProjectException(ErrorCode.NOT_FOUND, "Animal not found");
    }

    HelperMethods.deleteAnimal(database, animalId);

    return new NounMetadata(true, PixelDataType.BOOLEAN);
  }
}
