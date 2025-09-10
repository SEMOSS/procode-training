package examples.reactors;

import examples.domain.animal.AnimalData;
import examples.util.AnimalHelperMethods;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;

public class GetAnimalsReactor extends AbstractAnimalReactor {

  @Override
  protected NounMetadata doExecute() {

    List<Map<String, Object>> animals = AnimalHelperMethods.getAnimals(database);
    List<AnimalData> output = new ArrayList<>();

    for (Map<String, Object> animal : animals) {
      AnimalData row =
          new AnimalData(
              (String) animal.get("animalId"),
              (String) animal.get("animalName"),
              (String) animal.get("animalType"),
              (String) animal.get("dateOfBirth"));
      output.add(row);
    }

    return new NounMetadata(output, PixelDataType.VECTOR);
  }
}
