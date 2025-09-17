package util;

import domain.base.ErrorCode;
import domain.base.ProjectException;
import domain.examples.database.AnimalData;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import prerna.auth.User;
import prerna.engine.impl.rdbms.RDBMSNativeEngine;
import prerna.query.querystruct.SelectQueryStruct;
import prerna.query.querystruct.filters.SimpleQueryFilter;
import prerna.query.querystruct.selectors.QueryColumnSelector;
import prerna.util.ConnectionUtils;
import prerna.util.QueryExecutionUtility;

public class HelperMethods {

  public static String getUserId(User user) {
    return user.getPrimaryLoginToken().getId();
  }

  public static List<Map<String, Object>> getAnimals(RDBMSNativeEngine database) {
    SelectQueryStruct qs = new SelectQueryStruct();
    qs.addSelector(new QueryColumnSelector("ANIMAL__ANIMAL_ID", "animal_id"));
    qs.addSelector(new QueryColumnSelector("ANIMAL__ANIMAL_NAME", "animal_name"));
    qs.addSelector(new QueryColumnSelector("ANIMAL__ANIMAL_TYPE", "animal_type"));
    qs.addSelector(new QueryColumnSelector("ANIMAL__DATE_OF_BIRTH", "date_of_birth"));

    return QueryExecutionUtility.flushRsToMap(database, qs);
  }

  public static List<Map<String, Object>> getAnimalById(
      RDBMSNativeEngine database, String animalId) {
    SelectQueryStruct qs = new SelectQueryStruct();
    qs.addSelector(new QueryColumnSelector("ANIMAL__ANIMAL_ID", "animal_id"));
    qs.addSelector(new QueryColumnSelector("ANIMAL__ANIMAL_NAME", "animal_name"));
    qs.addSelector(new QueryColumnSelector("ANIMAL__ANIMAL_TYPE", "animal_type"));
    qs.addSelector(new QueryColumnSelector("ANIMAL__DATE_OF_BIRTH", "date_of_birth"));
    qs.addExplicitFilter(SimpleQueryFilter.makeColToValFilter("ANIMAL__ANIMAL_ID", "==", animalId));

    return QueryExecutionUtility.flushRsToMap(database, qs);
  }

  public static void addAnimal(RDBMSNativeEngine database, AnimalData animalData) {

    Connection con = null;
    try {
      con = database.getConnection();
      try (PreparedStatement ps =
          con.prepareStatement(
              "INSERT INTO ANIMAL (ANIMAL_ID, ANIMAL_NAME, ANIMAL_TYPE, DATE_OF_BIRTH)\n"
                  + "VALUES (?, ?, ?);")) {
        int parameterIndex = 1;
        ps.setString(parameterIndex, animalData.getAnimalId());
        ps.setString(parameterIndex++, animalData.getAnimalName());
        ps.setString(parameterIndex++, animalData.getAnimalType());
        ps.setObject(parameterIndex++, animalData.getDateOfBirth());
        ps.execute();
      } catch (SQLException e) {
        throw new ProjectException(ErrorCode.INTERNAL_SERVER_ERROR);
      }
    } catch (Exception e) {
      System.out.println(e.getStackTrace().toString());
      throw new ProjectException(ErrorCode.INTERNAL_SERVER_ERROR, "Error adding animal");
    } finally {
      ConnectionUtils.closeAllConnectionsIfPooling(database, con, null, null);
    }
  }

  public static void deleteAnimal(RDBMSNativeEngine database, String animalId) {

    Connection con = null;
    try {
      con = database.getConnection();
      try (PreparedStatement ps = con.prepareStatement("DELETE FROM ANIMAL WHERE ANIMAL_ID = ?")) {
        int parameterIndex = 1;
        ps.setString(parameterIndex++, animalId);
        ps.executeUpdate();
      } catch (SQLException e) {
        throw new ProjectException(ErrorCode.INTERNAL_SERVER_ERROR);
      }
    } catch (Exception e) {
      throw new ProjectException(ErrorCode.INTERNAL_SERVER_ERROR, "Error deleting animal");
    } finally {
      ConnectionUtils.closeAllConnectionsIfPooling(database, con, null, null);
    }
  }
}
