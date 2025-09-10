package reactors.examples;

import java.sql.Connection;
import java.sql.SQLException;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import reactors.AbstractProjectReactor;
import util.HelperMethods;

public class GetAnimalByIdReactor extends AbstractProjectReactor {

  public static final String animalIdColumn = "animal_id";

  public GetAnimalByIdReactor() {
    this.keysToGet = new String[] {animalIdColumn};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    int animalId = Integer.parseInt(this.keyValue.get(animalIdColumn));

    return new NounMetadata(HelperMethods.getAnimalByIdHelper(con, animalId), PixelDataType.MAP);
  }
}
