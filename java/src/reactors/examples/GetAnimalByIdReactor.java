package reactors.examples;

import domain.base.ErrorCode;
import domain.base.ProjectException;
import domain.examples.AnimalData;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import reactors.AbstractProjectReactor;

public class GetAnimalByIdReactor extends AbstractProjectReactor {

  public static final String animalIdColumn = "animal_id";

  public GetAnimalByIdReactor() {
    this.keysToGet = new String[] {animalIdColumn};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    int animalId = Integer.parseInt(this.keyValue.get(animalIdColumn));

    AnimalData output = null;

    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT animal_id, animal_type, animal_name FROM animal WHERE animal_id = ?")) {
      int parameterIndex = 1;
      ps.setInt(parameterIndex++, animalId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          int id = rs.getInt(animalIdColumn);
          String type = rs.getString("animal_type");
          String name = rs.getString("animal_name");
          output = new AnimalData(id, type, name);
        }
      }
    }

    if (output == null) {
      throw new ProjectException(ErrorCode.NOT_FOUND, "No animal found with that ID");
    }

    return new NounMetadata(output, PixelDataType.MAP);
  }
}
