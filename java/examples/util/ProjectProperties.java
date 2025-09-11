package examples.util;

import examples.domain.base.ErrorCode;
import examples.domain.base.ProjectException;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import prerna.util.AssetUtility;
import prerna.util.Utility;

public class ProjectProperties {
  private static ProjectProperties INSTANCE = null;

  // Add var for each property
  private String databaseId;

  private ProjectProperties() {}

  public static ProjectProperties getInstance() {
    if (INSTANCE == null) {
      throw new ProjectException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Unable to load project configuration");
    }
    return INSTANCE;
  }

  public static ProjectProperties getInstance(String projectId) {
    if (INSTANCE == null) {
      loadProp(projectId);
    }
    return INSTANCE;
  }

  private static void loadProp(String projectId) {
    ProjectProperties newInstance = new ProjectProperties();

    try (final FileInputStream fileIn =
        new FileInputStream(
            Utility.normalizePath(
                AssetUtility.getProjectAssetsFolder(projectId)
                    + "/java/examples/project.properties"))) { // using properties file in examples
      // folder
      Properties projectProperties = new Properties();
      projectProperties.load(fileIn);

      // Add any properties to be read by the properties file and add the corresponding getter
      newInstance.databaseId = projectProperties.getProperty("databaseId");

      INSTANCE = newInstance;
    } catch (IOException e) {
      INSTANCE = null;
      throw new ProjectException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Unable to load project configuration", e);
    }
  }

  // Add getters for properties
  public String getDatabaseId() {
    return databaseId;
  }
}
