package reactors.examples;

import domain.base.ErrorCode;
import domain.base.ProjectException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import reactors.AbstractProjectReactor;

public class AddAnimalReactor extends AbstractProjectReactor {

  public static final String animalNameColumn = "animal_name";
  public static final String animalTypeColumn = "animal_type";
  public static final String dateOfBirthColumn = "date_of_birth";

  public AddAnimalReactor() {
    this.keysToGet = new String[] {animalNameColumn, animalTypeColumn, dateOfBirthColumn};
    this.keyRequired = new int[] {1, 1, 1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) {
    String animalName = this.keyValue.get(animalNameColumn);
    String animalType = this.keyValue.get(animalTypeColumn);
    String dateOfBirth = this.keyValue.get(dateOfBirthColumn);

    if (animalName == null
        || animalName.isEmpty()
        || animalType == null
        || animalType.isEmpty()
        || dateOfBirth == null
        || dateOfBirth.isEmpty()) {
      throw new ProjectException(
          ErrorCode.BAD_REQUEST, "Animal name, type, and date of birth cannot be empty");
    }

    try (PreparedStatement ps =
        con.prepareStatement(
            "INSERT INTO animal (animal_name, animal_type, date_of_birth)\n"
                + "VALUES (?, ?, ?);")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, animalName);
      ps.setString(parameterIndex++, animalType);
      ps.setString(parameterIndex++, dateOfBirth);
      ps.execute();
    } catch (SQLException e) {
      throw new ProjectException(ErrorCode.INTERNAL_SERVER_ERROR, "Error adding animal");
    }

    return new NounMetadata(true, PixelDataType.BOOLEAN);
  }
}
