package reactors.animals;

import domain.animals.AnimalData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import prerna.date.SemossDate;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import reactors.AbstractProjectReactor;
import util.HelperMethods;

public class GetAnimalsReactor extends AbstractProjectReactor {

  @Override
  protected NounMetadata doExecute() {

    List<Map<String, Object>> animals = HelperMethods.getAnimals(database);
    List<AnimalData> output = new ArrayList<>();

    for (Map<String, Object> animal : animals) {
      AnimalData row =
          new AnimalData(
              (String) animal.get("animalId"),
              (String) animal.get("animalName"),
              (String) animal.get("animalType"),
              (SemossDate) animal.get("dateOfBirth"));
      output.add(row);
    }

    return new NounMetadata(output, PixelDataType.VECTOR);
  }
}
