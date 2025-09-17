package reactors.examples.database;

import domain.base.ErrorCode;
import domain.base.ProjectException;
import domain.examples.database.AnimalData;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import reactors.AbstractProjectReactor;
import util.Constants;
import util.HelperMethods;

// Add an animal to the database!
public class AddAnimalReactor extends AbstractProjectReactor {

  public AddAnimalReactor() {
    this.keysToGet =
        new String[] {Constants.ANIMAL_NAME, Constants.ANIMAL_TYPE, Constants.DATE_OF_BIRTH};
    this.keyRequired = new int[] {1, 1, 1};
  }

  @Override
  protected NounMetadata doExecute() {
    String animalName = this.keyValue.get(Constants.ANIMAL_NAME);
    String animalType = this.keyValue.get(Constants.ANIMAL_TYPE);
    String dateOfBirth = this.keyValue.get(Constants.DATE_OF_BIRTH);

    if (StringUtils.trimToNull(animalName) == null
        || StringUtils.trimToNull(animalType) == null
        || StringUtils.trimToNull(dateOfBirth) == null) {
      throw new ProjectException(
          ErrorCode.BAD_REQUEST, "Animal name, type, and date of birth cannot be empty");
    }

    AnimalData animalData =
        new AnimalData(
            UUID.randomUUID().toString(),
            animalType,
            animalName,
            new prerna.date.SemossDate(dateOfBirth, "yyyy-MM-dd"));

    HelperMethods.addAnimal(database, animalData);

    return new NounMetadata(true, PixelDataType.BOOLEAN);
  }
}
