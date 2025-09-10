package examples.reactors;

import examples.domain.base.ErrorCode;
import examples.domain.base.ProjectException;
import examples.util.AnimalHelperMethods;
import examples.util.Constants;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;

public class DeleteAnimalReactor extends AbstractAnimalReactor {

  public DeleteAnimalReactor() {
    this.keysToGet = new String[] {Constants.ANIMAL_ID};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute() {
    String animalId = this.keyValue.get(Constants.ANIMAL_ID);

    // The below method will throw an exception if the animal does not exist
    List<Map<String, Object>> animals = AnimalHelperMethods.getAnimalById(database, animalId);
    if (animals.isEmpty()) {
      throw new ProjectException(ErrorCode.NOT_FOUND, "Animal not found");
    }

    AnimalHelperMethods.deleteAnimal(database, animalId);

    return new NounMetadata(true, PixelDataType.BOOLEAN);
  }
}
