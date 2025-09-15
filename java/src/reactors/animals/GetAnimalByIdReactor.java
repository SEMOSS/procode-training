package reactors.animals;

import java.util.List;
import java.util.Map;

import domain.animals.AnimalData;
import domain.base.ErrorCode;
import domain.base.ProjectException;
import prerna.date.SemossDate;
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
    List<Map<String, Object>> animalData = HelperMethods.getAnimalById(database, animalId);
    if (animalData.isEmpty()) {
    	throw new ProjectException(ErrorCode.NOT_FOUND, "Animal not found");
    }
    
    if (animalData.size() > 1) {
    	throw new ProjectException(ErrorCode.INTERNAL_SERVER_ERROR, "Multiple animals found with that id");
    }
    
    Map<String, Object> animal = animalData.get(0);
    
    AnimalData row =
        new AnimalData(
            (String) animal.get("animalId"),
            (String) animal.get("animalName"),
            (String) animal.get("animalType"),
            (SemossDate) animal.get("dateOfBirth"));
    
    return new NounMetadata(row, PixelDataType.MAP);
  }
}
