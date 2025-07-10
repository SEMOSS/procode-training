package tqmc.reactors;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import prerna.auth.AuthProvider;
import prerna.auth.User;
import prerna.engine.api.IRDBMSEngine;
import prerna.reactor.AbstractReactor;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.PixelOperationType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.AssetUtility;
import prerna.util.Utility;
import prerna.util.sql.RdbmsTypeEnum;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.user.TQMCUserInfo;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;
import tqmc.util.TQMCProperties;

public abstract class AbstractTQMCReactor extends AbstractReactor {

  private static final Logger LOGGER = LogManager.getLogger(AbstractTQMCReactor.class);

  protected User user;
  protected String userId;

  protected String projectId;
  protected TQMCProperties tqmcProperties;

  protected String engineId;
  protected RdbmsTypeEnum engineType;

  protected NounMetadata result = null;

  protected boolean tqmcUserIsActive = false;
  protected String tqmcUserRole = null;
  protected Set<String> tqmcUserProducts = new HashSet<>();

  protected TQMCUserInfo tqmcUserInfo;

  @Override
  public NounMetadata execute() {
    try {
      preExecute();

      IRDBMSEngine engine = (IRDBMSEngine) Utility.getEngine(engineId);
      if (engine == null) {
        throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, "Unable to find database");
      }
      engineType = engine.getDbType();
      Connection con = null;
      try {
        con = engine.makeConnection();

        loadUserInfo(con);

        if (isReadOnly()) {
          result = doExecute(con);
        } else {
          boolean isAutoCommit = con.getAutoCommit();
          con.setAutoCommit(false);
          try {
            result = doExecute(con);
            con.commit();
          } catch (Exception e) {
            con.rollback();
            TQMCException ex = null;
            if (e instanceof TQMCException) {
              ex = (TQMCException) e;
            } else {
              ex = new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
            }
            throw ex;
          } finally {
            con.setAutoCommit(isAutoCommit);
          }
        }
      } finally {
        if (engine.isConnectionPooling() && con != null) {
          con.close();
        }
      }
      return result;
    } catch (Exception e) {
      TQMCException ex = null;
      if (e instanceof TQMCException) {
        ex = (TQMCException) e;
      } else {
        ex = new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
      }
      LOGGER.error(String.format("Reactor %s threw an error", this.getClass().getSimpleName()), e);

      if (tqmcProperties.getIsMilInstance()) {

        // Cache the error for future reference in .mil
        String assetDirectory = null;
        try {
          assetDirectory =
              Paths.get(
                      AssetUtility.getProjectAppRootFolder(this.tqmcProperties.getProjectId()),
                      "version",
                      "assets")
                  .toString();
        } catch (Exception assetEx) {
          LOGGER.warn("Failed to get asset directory for error caching: {}", assetEx.getMessage());
        }
        if (assetDirectory != null) {
          String errorText =
              String.format(
                  "Timestamp: %s\nReactor: %s\nError: %s\nStackTrace:\n%s",
                  ConversionUtils.getUTCFromLocalNow(),
                  this.getClass().getSimpleName(),
                  ex.getMessage(),
                  ExceptionUtils.getFullStackTrace(e));
          cacheError(assetDirectory, errorText);
        }
      }

      NounMetadata result =
          new NounMetadata(ex.getAsMap(), PixelDataType.MAP, PixelOperationType.ERROR);
      if (tqmcProperties != null && tqmcProperties.getDebuggingEnabled()) {
        result.addAdditionalReturn(
            new NounMetadata(
                ExceptionUtils.getFullStackTrace(e),
                PixelDataType.CONST_STRING,
                PixelOperationType.ERROR));
      }
      return new NounMetadata(ex.getAsMap(), PixelDataType.MAP, PixelOperationType.ERROR);
    }
  }

  protected void preExecute() {
    projectId = this.insight.getContextProjectId();
    if (projectId == null) {
      projectId = this.insight.getProjectId();
    }

    tqmcProperties = TQMCProperties.getInstance(projectId);
    engineId = tqmcProperties.getEngineId();

    user = this.insight.getUser();
    AuthProvider resetterType =
        AuthProvider.getProviderFromString(tqmcProperties.getResetterUserType());
    if (user != null) {
      for (AuthProvider loginType : user.getLogins()) {
        if (loginType != resetterType) {
          userId = user.getAccessToken(loginType).getId();
        }
      }
    }

    organizeKeys();
  }

  protected void loadUserInfo(Connection con) throws SQLException {
    tqmcUserInfo = TQMCHelper.getTQMCUserInfo(con, userId);

    if (tqmcUserInfo != null) {
      tqmcUserIsActive = tqmcUserInfo.getIsActive();
      tqmcUserRole = tqmcUserInfo.getRole();
      tqmcUserProducts = tqmcUserInfo.getProducts();
    }
  }

  protected abstract NounMetadata doExecute(Connection con) throws SQLException;

  protected boolean isReadOnly() {
    return false;
  }

  protected boolean hasProductPermission(String productId) {
    return tqmcUserIsActive && tqmcUserProducts.contains(productId);
  }

  protected boolean hasRole(String roleId) {
    return tqmcUserIsActive && StringUtils.equalsIgnoreCase(roleId, tqmcUserRole);
  }

  protected boolean hasProductManagementPermission(String productId) {
    return (hasProductPermission(productId)
            && (hasRole(TQMCConstants.MANAGEMENT_LEAD) || hasRole(TQMCConstants.CONTRACTING_LEAD)))
        || hasRole(TQMCConstants.ADMIN);
  }

  private void cacheError(String assetDirectory, String errorText) {
    try {

      /**
       * TODO: Finalize whether we want this in app_root/version/assets, or just in app_root, or in
       * an errors/logs folder?
       */
      File errorFile = new File(assetDirectory, TQMCConstants.ERROR_FILE_NAME);

      // Synchronize on the file object to avoid concurrent writes
      synchronized (AbstractTQMCReactor.class) {
        // Append the error
        try (FileWriter fw = new FileWriter(errorFile, true);
            BufferedWriter bw = new BufferedWriter(fw)) {
          bw.write(errorText);
          bw.write(TQMCConstants.ERROR_SEPARATOR);
        }

        // Now, trim file if needed
        List<String> lines = Files.readAllLines(errorFile.toPath());
        if (lines.size() > TQMCConstants.MAX_ERROR_LINES) {
          // Remove lines from the top so only the last MAX_ERROR_LINES remain
          List<String> trimmed =
              lines.subList(lines.size() - TQMCConstants.MAX_ERROR_LINES, lines.size());
          Files.write(
              errorFile.toPath(),
              trimmed,
              StandardOpenOption.WRITE,
              StandardOpenOption.TRUNCATE_EXISTING);
        }
      }
    } catch (Exception ex) {
      // Logging to avoid infinite recursion if error caching fails
      LOGGER.warn("Failed to cache error to file: {}", ex.getMessage());
    }
  }
}
