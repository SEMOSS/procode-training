package reactors.examples.database;

import java.util.List;
import java.util.Map;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import reactors.AbstractProjectReactor;
import util.HelperMethods;

// Get all the animals in the database!
public class GetAnimalsReactor extends AbstractProjectReactor {

  @Override
  protected NounMetadata doExecute() {

    List<Map<String, Object>> animals = HelperMethods.getAnimals(database);

    return new NounMetadata(animals, PixelDataType.VECTOR);
  }
}
