package tqmc.reactors.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import prerna.auth.AccessToken;
import prerna.auth.AuthProvider;
import prerna.auth.PasswordRequirements;
import prerna.auth.User;
import prerna.auth.utils.AbstractSecurityUtils;
import prerna.auth.utils.SecurityQueryUtils;
import prerna.date.SemossDate;
import prerna.engine.api.IRawSelectWrapper;
import prerna.engine.impl.rdbms.RDBMSNativeEngine;
import prerna.query.querystruct.SelectQueryStruct;
import prerna.query.querystruct.filters.SimpleQueryFilter;
import prerna.query.querystruct.selectors.QueryColumnSelector;
import prerna.rdf.engine.wrappers.WrapperManager;
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

public class TqmcResetPasswordReactor extends AbstractReactor {
  private static final Logger LOGGER = LogManager.getLogger(TqmcResetPasswordReactor.class);

  private String projectId;
  private TQMCProperties tqmcProperties;
  private RDBMSNativeEngine securityDb;
  private RDBMSNativeEngine userDb;

  private User user;
  private boolean usingResetter = false;
  private AuthProvider resetterType = null;

  private String token;
  private String password;
  private String salt;
  private String hashPassword;
  private String previousSalt;
  private String previousHashPassword;

  private String email;
  private String type; // all-caps provider enum name
  private AuthProvider provider;

  private String userId;

  public TqmcResetPasswordReactor() {
    keysToGet = new String[] {"token", "password"};
    keyRequired = new int[] {1, 1};
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
        AccessToken candidate = user.getAccessToken(resetterType);
        if (candidate != null && candidate.getId().equals(resetterUserId)) {
          usingResetter = true;
        }
      }

      if (!usingResetter) {
        throw new TQMCException(
            ErrorCode.FORBIDDEN, "Only administrator password changes are currently enabled");
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

    token = keyValue.get("token");
    password = keyValue.get("password");

    if (!PasswordRequirements.getInstance().isAllowUserChangePassword()) {
      throw new TQMCException(
          ErrorCode.FORBIDDEN, "Only an administrator is allowed to change the user password");
    }

    loadDataForToken(token);

    if (provider != AuthProvider.LINOTP) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST, "Cannot update password for this user's login type");
    }

    loadDataForUser();

    try {
      AbstractSecurityUtils.validPassword(userId, provider, password);
    } catch (IllegalArgumentException e) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, e.getMessage());
    }

    salt = SecurityQueryUtils.generateSalt();
    hashPassword = SecurityQueryUtils.hash(password, salt);

    Connection con = null;
    try {
      con = userDb.makeConnection();

      boolean isAutoCommit = con.getAutoCommit();
      con.setAutoCommit(false);
      try {
        loadPreviousCredentials(con);
        updateCredentials(con, salt, hashPassword);
        con.commit();
      } catch (Exception e) {
        con.rollback();
        throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to update password", e);
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
          "TQMC Reset Password : Success",
          TQMCConstants.TQMC_PASSWORD_CHANGE_TEMPLATE,
          email,
          null);
    } catch (TQMCException e) {
      LOGGER.warn("Failed to send password reset success email to " + email);
    }

    return getSuccess("Password successfully reset");
  }

  private void loadPreviousCredentials(Connection con) throws SQLException, TQMCException {
    try (PreparedStatement ps =
        con.prepareStatement("SELECT SALT, PASSWORD FROM SMSS_USER WHERE USER_ID = ?")) {
      ps.setString(1, userId);
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        previousSalt = rs.getString(1);
        previousHashPassword = rs.getString(2);
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
            "UPDATE SMSS_USER SET SALT = ?, PASSWORD = ? WHERE ID = ? AND TYPE = ?")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, salt);
      ps.setString(parameterIndex++, hashPassword);
      ps.setString(parameterIndex++, userId);
      ps.setString(parameterIndex++, provider.toString());
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

  private void loadDataForToken(String token) throws Exception {
    SemossDate dateTokenAdded = null;

    SelectQueryStruct qs = new SelectQueryStruct();
    qs.addSelector(new QueryColumnSelector("PASSWORD_RESET__DATE_ADDED"));
    qs.addSelector(new QueryColumnSelector("PASSWORD_RESET__EMAIL"));
    qs.addSelector(new QueryColumnSelector("PASSWORD_RESET__TYPE"));
    qs.addExplicitFilter(
        SimpleQueryFilter.makeColToValFilter("PASSWORD_RESET__TOKEN", "==", token));
    IRawSelectWrapper wrapper = null;
    try {
      wrapper = WrapperManager.getInstance().getRawWrapper(securityDb, qs);
      if (wrapper.hasNext()) {
        Object[] row = wrapper.next().getValues();
        dateTokenAdded = (SemossDate) row[0];
        email = (String) row[1];
        type = (String) row[2];
      }
    } finally {
      if (wrapper != null) {
        wrapper.close();
      }
    }

    if (dateTokenAdded == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid attempt trying to update password");
    }
    // if i added the token more than 15 minutes ago
    // then the link has expired
    ZonedDateTime curTimeUtc = ZonedDateTime.now(ZoneId.of("UTC"));
    if (dateTokenAdded.getZonedDateTime().isBefore(curTimeUtc.minusMinutes(15))) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST,
          "This link to reset the password has expired, please request a new link");
    }
    provider = AuthProvider.getProviderFromString(type);
  }

  private void loadDataForUser() throws Exception {
    SelectQueryStruct qs = new SelectQueryStruct();
    qs.addSelector(new QueryColumnSelector("SMSS_USER__ID"));
    qs.addExplicitFilter(SimpleQueryFilter.makeColToValFilter("SMSS_USER__EMAIL", "==", email));
    qs.addExplicitFilter(
        SimpleQueryFilter.makeColToValFilter("SMSS_USER__TYPE", "==", provider.toString()));
    IRawSelectWrapper wrapper = null;
    try {
      wrapper = WrapperManager.getInstance().getRawWrapper(securityDb, qs);
      if (wrapper.hasNext()) {
        userId = (String) wrapper.next().getValues()[0];
      }
    } finally {
      if (wrapper != null) {
        wrapper.close();
      }
    }
  }
}
