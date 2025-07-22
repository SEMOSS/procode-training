package util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import prerna.util.AssetUtility;
import prerna.util.Utility;
import domain.base.ErrorCode;
import domain.base.TQMCException;

/**
 * Loads property values from a project asset at
 *
 * <pre>[proj]/app_root/version/assets/java/project.properties</pre>
 *
 * into a static instance. The project used to infer the path is an argument at runtime.
 *
 * <p>File format conforms to typical java.util.Properties style load patterns: one property per
 * line with white space, {@code '='}, or {@code ':'} separating the key and value.
 *
 * <p>Replace the engineId applicable for your project in a format such as:
 *
 * <pre>engineId=fc6a3fab-2425-4987-be93-58ad2efeee24</pre>
 *
 * @see java.util.Properties#load(java.io.Reader)
 */
public class TQMCProperties {
  private static TQMCProperties INSTANCE = null;

  private String projectId;
  private String engineId;
  private boolean debuggingEnabled;

  private TQMCProperties() {}

  public static TQMCProperties getInstance() {
    if (INSTANCE == null) {
      throw new TQMCException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Unable to load project configuration");
    }
    return INSTANCE;
  }

  public static TQMCProperties getInstance(String projectId) {
    if (INSTANCE == null) {
      loadProp(projectId);
    }
    return INSTANCE;
  }

  private static void loadProp(String projectId) {
    TQMCProperties newInstance = new TQMCProperties();

    try (final FileInputStream fileIn =
        new FileInputStream(
            Utility.normalizePath(
                AssetUtility.getProjectAssetsFolder(projectId) + "/java/project.properties"))) {
      Properties tqmcProperties = new Properties();
      tqmcProperties.load(fileIn);

      newInstance.projectId = projectId;
      newInstance.engineId = tqmcProperties.getProperty("engineId");
      newInstance.debuggingEnabled =
          Boolean.parseBoolean(tqmcProperties.getProperty("debuggingEnabled"));

      INSTANCE = newInstance;
    } catch (IOException e) {
      INSTANCE = null;
      throw new TQMCException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Unable to load project configuration", e);
    }
  }

  public String getProjectId() {
    return projectId;
  }

  public String getEngineId() {
    return engineId;
  }

  public boolean getDebuggingEnabled() {
    return debuggingEnabled;
  }
}
