package util;

import domain.base.ErrorCode;
import domain.base.ProjectException;
import domain.examples.AnimalData;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class HelperMethods {
  public static int addIntegerExampleHelper(Connection con, int a, int b) throws SQLException {
    int output = 0;
    try (PreparedStatement ps = con.prepareStatement("SELECT ? + ? AS OUTPUT"); ) {
      int parameterIndex = 1;
      ps.setInt(parameterIndex++, a);
      ps.setInt(parameterIndex++, b);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          output = rs.getInt("OUTPUT");
        }
      }
    }

    return output;
  }

  public static AnimalData getAnimalByIdHelper(Connection con, int animalId) throws SQLException {

    AnimalData output = null;

    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT animal_id, animal_type, animal_name, date_of_birth FROM animal WHERE animal_id = ?")) {
      int parameterIndex = 1;
      ps.setInt(parameterIndex++, animalId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          int id = rs.getInt("animal_id");
          String type = rs.getString("animal_type");
          String name = rs.getString("animal_name");
          String dateOfBirth = rs.getString("date_of_birth");
          output = new AnimalData(id, type, name, dateOfBirth);
        }
      }
    }

    if (output == null) {
      throw new ProjectException(ErrorCode.NOT_FOUND, "No animal found with that ID");
    }

    return output;
  }
}
