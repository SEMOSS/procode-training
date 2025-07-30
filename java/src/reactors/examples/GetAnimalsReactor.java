package reactors.examples;

import domain.examples.AnimalData;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import reactors.AbstractProjectReactor;

public class GetAnimalsReactor extends AbstractProjectReactor {

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    List<AnimalData> output = new ArrayList<>();
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT animal_id, animal_type, animal_name FROM animal ORDER BY animal_id ASC")) {
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          int id = rs.getInt("animal_id");
          String type = rs.getString("animal_type");
          String name = rs.getString("animal_name");
          AnimalData row = new AnimalData(id, type, name);
          output.add(row);
        }
      }
    }

    return new NounMetadata(output, PixelDataType.VECTOR);
  }
}
