package reactors.examples;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import reactors.AbstractProjectReactor;
import util.HelperMethods;

public class DeleteAnimalReactor extends AbstractProjectReactor {

  public static final String animalIdColumn = "animal_id";

  public DeleteAnimalReactor() {
    this.keysToGet = new String[] {animalIdColumn};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    int animalId = Integer.parseInt(this.keyValue.get(animalIdColumn));

    // The below method will throw an exception if the animal does not exist
    HelperMethods.getAnimalByIdHelper(con, animalId);

    try (PreparedStatement ps = con.prepareStatement("DELETE FROM animal WHERE animal_id = ?")) {
      int parameterIndex = 1;
      ps.setInt(parameterIndex++, animalId);
      ps.execute();
    }

    return new NounMetadata(true, PixelDataType.BOOLEAN);
  }
}
