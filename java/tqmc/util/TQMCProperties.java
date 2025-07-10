package tqmc.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import prerna.util.AssetUtility;
import prerna.util.Utility;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;

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
  private String userEngineId;
  private String storageId;
  private String docsProjectId;
  private String userAccountProvider;
  private String tqmcUrl;
  private String resetterUserId;
  private String resetterUserType;
  private String defaultAbs1UserId;
  private String defaultAbs2UserId;
  private String defaultPhysUserId;
  private boolean isMilInstance;
  private boolean debuggingEnabled;
  private String loginGuideName;

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
      newInstance.storageId = tqmcProperties.getProperty("storageId");
      newInstance.userEngineId = tqmcProperties.getProperty("userEngineId");
      newInstance.docsProjectId = tqmcProperties.getProperty("docsProjectId");
      newInstance.userAccountProvider = tqmcProperties.getProperty("userAccountProvider");
      newInstance.tqmcUrl = tqmcProperties.getProperty("tqmcUrl");
      newInstance.resetterUserId = tqmcProperties.getProperty("resetterUserId");
      newInstance.resetterUserType = tqmcProperties.getProperty("resetterUserType");
      newInstance.defaultAbs1UserId = tqmcProperties.getProperty("defaultAbstractorOne");
      newInstance.defaultAbs2UserId = tqmcProperties.getProperty("defaultAbstractorTwo");
      newInstance.defaultPhysUserId = tqmcProperties.getProperty("defaultPhysUserId");
      newInstance.isMilInstance = Boolean.parseBoolean(tqmcProperties.getProperty("isMilInstance"));
      newInstance.debuggingEnabled =
          Boolean.parseBoolean(tqmcProperties.getProperty("debuggingEnabled"));
      newInstance.loginGuideName = tqmcProperties.getProperty("loginGuideName");

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

  public String getStorageId() {
    return storageId;
  }

  public String getUserEngineId() {
    return userEngineId;
  }

  public String getDocsProjectId() {
    return docsProjectId;
  }

  public String getUserAccountProvider() {
    return userAccountProvider;
  }

  public String getTqmcUrl() {
    return tqmcUrl;
  }

  public String getResetterUserId() {
    return resetterUserId;
  }

  public String getResetterUserType() {
    return resetterUserType;
  }

  public String getDefaultAbs1UserId() {
    return defaultAbs1UserId;
  }

  public String getDefaultAbs2UserId() {
    return defaultAbs2UserId;
  }

  public String getDefaultPhysUserId() {
    return defaultPhysUserId;
  }

  public boolean getIsMilInstance() {
    return isMilInstance;
  }

  public boolean getDebuggingEnabled() {
    return debuggingEnabled;
  }

  public String getLoginGuideName() {
    return loginGuideName;
  }
}
