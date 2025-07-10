package tqmc.reactors.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import prerna.auth.AuthProvider;
import prerna.auth.PasswordRequirements;
import prerna.auth.User;
import prerna.auth.utils.AbstractSecurityUtils;
import prerna.auth.utils.SecurityQueryUtils;
import prerna.engine.impl.rdbms.RDBMSNativeEngine;
import prerna.reactor.AbstractReactor;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.PixelOperationType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.Constants;
import prerna.util.Utility;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;
import tqmc.util.TQMCProperties;

public class TqmcChangePasswordReactor extends AbstractReactor {
  private static final Logger LOGGER = LogManager.getLogger(TqmcChangePasswordReactor.class);

  protected String projectId;
  protected TQMCProperties tqmcProperties;
  private RDBMSNativeEngine securityDb;
  private RDBMSNativeEngine userDb;

  private User user;
  private boolean usingResetter = false;
  private AuthProvider resetterType = null;
  private String callingUserId;

  private String userId;
  private String oldPassword;
  private String newPassword;

  private String email;

  private String salt;
  private String hashPassword;
  private String previousSalt;
  private String previousHashPassword;

  public TqmcChangePasswordReactor() {
    keysToGet = new String[] {"userId", "oldPassword", "newPassword"};
    keyRequired = new int[] {1, 1, 1};
  }

  @Override
  public NounMetadata execute() {
    try {
      projectId = this.insight.getContextProjectId();
      if (projectId == null) {
        projectId = this.insight.getProjectId();
      }
      tqmcProperties = TQMCProperties.getInstance(projectId);

      user = insight.getUser();
      resetterType = AuthProvider.getProviderFromString(tqmcProperties.getResetterUserType());
      String resetterUserId = tqmcProperties.getResetterUserId();
      if (user != null) {
        for (AuthProvider loginType : user.getLogins()) {
          if (loginType == resetterType
              && user.getAccessToken(loginType).getId().equals(resetterUserId)) {
            usingResetter = true;
          } else if (callingUserId == null) {
            callingUserId = user.getAccessToken(loginType).getId();
          }
        }
      }

      securityDb = (RDBMSNativeEngine) Utility.getDatabase(Constants.SECURITY_DB);
      userDb = (RDBMSNativeEngine) Utility.getDatabase(tqmcProperties.getUserEngineId());
      if (userDb == null) {
        throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, "Unable to find database");
      }
      return doExecute();
    } catch (Exception e) {
      TQMCException ex = null;
      if (e instanceof TQMCException) {
        ex = (TQMCException) e;
      } else {
        ex = new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
      }
      LOGGER.error(String.format("Reactor %s threw an error", this.getClass().getSimpleName()), e);
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

  private NounMetadata doExecute() throws Exception {
    organizeKeys();

    userId = keyValue.get("userId");
    oldPassword = keyValue.get("oldPassword");
    newPassword = keyValue.get("newPassword");

    // a user can reset their own password, or the resetter user can do it for anyone
    if (!userId.equals(callingUserId) && !usingResetter
        || !PasswordRequirements.getInstance().isAllowUserChangePassword()) {
      throw new TQMCException(
          ErrorCode.FORBIDDEN, "Only an administrator is allowed to change the user password");
    }

    Connection con = null;
    try {
      con = userDb.makeConnection();

      boolean isAutoCommit = con.getAutoCommit();
      con.setAutoCommit(false);
      try {
        loadPreviousCredentials(con);

        String oldHash = AbstractSecurityUtils.hash(oldPassword, previousSalt);
        if (!previousHashPassword.equals(oldHash)) {
          throw new TQMCException(ErrorCode.FORBIDDEN, "Invalid username or password");
        }

        try {
          AbstractSecurityUtils.validPassword(userId, AuthProvider.LINOTP, newPassword);
        } catch (IllegalArgumentException e) {
          throw new TQMCException(ErrorCode.BAD_REQUEST, e.getMessage());
        }

        salt = SecurityQueryUtils.generateSalt();
        hashPassword = SecurityQueryUtils.hash(newPassword, salt);

        updateCredentials(con, salt, hashPassword);
        con.commit();
      } catch (Exception e) {
        con.rollback();
        if (!(e instanceof TQMCException)) {
          throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to update password", e);
        }
        throw e;
      } finally {
        con.setAutoCommit(isAutoCommit);
      }
    } finally {
      if (userDb.isConnectionPooling() && con != null) {
        con.close();
      }
    }

    try {
      con = securityDb.makeConnection();

      boolean isAutoCommit = con.getAutoCommit();
      con.setAutoCommit(false);
      try {
        updateCredentialsOnSemoss(con);
        con.commit();
      } catch (Exception e) {
        con.rollback();
        revertCredentialChange();
        throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to update password", e);
      } finally {
        con.setAutoCommit(isAutoCommit);
      }
    } finally {
      if (securityDb.isConnectionPooling() && con != null) {
        con.close();
      }
    }

    try {
      TQMCHelper.sendEmail(
          "TQMC Password Change : Success",
          TQMCConstants.TQMC_PASSWORD_CHANGE_TEMPLATE,
          email,
          null);
    } catch (TQMCException e) {
      LOGGER.warn("Failed to send password change success email to " + email);
    }

    return getSuccess("Password successfully reset");
  }

  private void loadPreviousCredentials(Connection con) throws SQLException, TQMCException {
    try (PreparedStatement ps =
        con.prepareStatement("SELECT SALT, PASSWORD, EMAIL FROM SMSS_USER WHERE USER_ID = ?")) {
      ps.setString(1, userId);
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        previousSalt = rs.getString(1);
        previousHashPassword = rs.getString(2);
        email = rs.getString(3);
      }
    }
    if (previousSalt == null || previousHashPassword == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Cannot update password for this user");
    }
  }

  private void updateCredentials(Connection con, String newSalt, String newHashPassword)
      throws SQLException, TQMCException {
    try (PreparedStatement ps =
        con.prepareStatement("UPDATE SMSS_USER SET SALT = ?, PASSWORD = ? WHERE USER_ID = ?")) {
      ps.setString(1, newSalt);
      ps.setString(2, newHashPassword);
      ps.setString(3, userId);
      ps.execute();
    }
  }

  private void updateCredentialsOnSemoss(Connection con) throws SQLException {
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE SMSS_USER SET SALT = ?, PASSWORD = ? WHERE ID = ? AND TYPE = 'Linotp'")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, salt);
      ps.setString(parameterIndex++, hashPassword);
      ps.setString(parameterIndex++, userId);
      ps.execute();
    }
  }

  private void revertCredentialChange() {
    Connection con = null;
    try {
      con = userDb.makeConnection();

      boolean isAutoCommit = con.getAutoCommit();
      con.setAutoCommit(false);
      try {
        updateCredentials(con, previousSalt, previousHashPassword);
        con.commit();
      } catch (Exception e) {
        con.rollback();
        LOGGER.warn("Error during revert of the user credential change for user: " + userId, e);
      } finally {
        con.setAutoCommit(isAutoCommit);
      }
    } catch (SQLException e) {
      LOGGER.warn("Failed to rollback the user credential change for user: " + userId, e);
    } finally {
      if (userDb.isConnectionPooling() && con != null) {
        try {
          con.close();
        } catch (SQLException e) {
          LOGGER.warn("Failed to close connection to tqmc user db", e);
        }
      }
    }
  }
}
