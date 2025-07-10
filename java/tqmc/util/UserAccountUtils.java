package tqmc.util;

import jakarta.mail.Session;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.apache.commons.lang.text.StrSubstitutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import prerna.auth.AuthProvider;
import prerna.auth.utils.SecurityNativeUserUtils;
import prerna.auth.utils.SecurityQueryUtils;
import prerna.engine.api.IRDBMSEngine;
import prerna.engine.impl.rdbms.RDBMSNativeEngine;
import prerna.util.AssetUtility;
import prerna.util.Constants;
import prerna.util.EmailUtility;
import prerna.util.SocialPropertiesUtil;
import prerna.util.Utility;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.user.TQMCUserInfo;

public class UserAccountUtils {

  private static final Logger LOGGER = LogManager.getLogger(UserAccountUtils.class);

  public static void createNativeUser(
      TQMCUserInfo u, String createdByUserId, String createdByUserType) throws SQLException {
    // generate temporary password
    byte[] pwBytes = new byte[11];
    new Random().nextBytes(pwBytes);
    String password = Base64.getEncoder().encodeToString(pwBytes);

    String salt = SecurityQueryUtils.generateSalt();
    String hashedPassword = SecurityQueryUtils.hash(password, salt);

    if (createSemossUser(
        u,
        AuthProvider.NATIVE.toString(),
        salt,
        hashedPassword,
        createdByUserId,
        createdByUserType)) {
      sendUserCreationEmail(u, password);
    } else {
      sendPasswordResetEmail(u, null);
    }
  }

  public static void createSamlUser(
      TQMCUserInfo u, String createdByUserId, String createdByUserType) throws SQLException {
    if (createSemossUser(
        u, AuthProvider.SAML.toString(), null, null, createdByUserId, createdByUserType)) {
      sendUserCreationEmail(u, null);
    }
  }

  public static void createLinotpUser(
      TQMCUserInfo u,
      String createdByUserId,
      String createdByUserType,
      String docsProjectId,
      String loginGuideName)
      throws SQLException {
    // generate temporary password
    byte[] pwBytes = new byte[11];
    new Random().nextBytes(pwBytes);
    String password = Base64.getEncoder().encodeToString(pwBytes);

    String salt = SecurityQueryUtils.generateSalt();
    String hashedPassword = SecurityQueryUtils.hash(password, salt);

    boolean userCreated = createRemoteUser(u, salt, hashedPassword);
    createSemossUser(
        u,
        AuthProvider.LINOTP.toString(),
        salt,
        hashedPassword,
        createdByUserId,
        createdByUserType);
    String[] attachments = null;
    if (docsProjectId != null) {
      String projectAssetDirectory = AssetUtility.getProjectAssetsFolder(docsProjectId);
      String userGuidePath =
          Paths.get(projectAssetDirectory, "portals/" + loginGuideName).normalize().toString();
      // String userGuideVideoPath =
      //     Paths.get(projectAssetDirectory, "portals/log_in_video.mp4").normalize().toString();
      attachments = new String[] {userGuidePath};
    }
    if (userCreated) {
      sendUserCreationEmail(u, password, attachments);
    } else {
      sendPasswordResetEmail(u, attachments);
    }
  }

  private static synchronized boolean createRemoteUser(
      TQMCUserInfo u, String salt, String hashedPassword) {
    RDBMSNativeEngine userDb =
        (RDBMSNativeEngine) Utility.getDatabase(TQMCProperties.getInstance().getUserEngineId());
    Connection con = null;
    try {
      con = userDb.makeConnection();

      boolean isAutoCommit = con.getAutoCommit();
      con.setAutoCommit(false);
      try {
        boolean userFound = false;
        try (PreparedStatement ps =
            con.prepareStatement("SELECT 1 FROM SMSS_USER WHERE USER_ID = ?")) {
          ps.setString(1, u.getEmail());
          ResultSet rs = ps.executeQuery();
          if (rs.next()) {
            userFound = true;
          }
        }
        if (userFound) {
          return false;
        }

        try (PreparedStatement ps =
            con.prepareStatement(
                "INSERT INTO SMSS_USER (USER_ID, EMAIL, LAST_NAME, FIRST_NAME, PASSWORD, SALT) VALUES (?,?,?,?,?,?)")) {
          int parameterIndex = 1;
          ps.setString(parameterIndex++, u.getEmail());
          ps.setString(parameterIndex++, u.getEmail());
          ps.setString(parameterIndex++, u.getLastName());
          ps.setString(parameterIndex++, u.getFirstName());
          ps.setString(parameterIndex++, hashedPassword);
          ps.setString(parameterIndex++, salt);
          ps.execute();
        }
        con.commit();
        return true;
      } catch (Exception e) {
        con.rollback();
        throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to create user", e);
      } finally {
        con.setAutoCommit(isAutoCommit);
      }
    } catch (SQLException e) {
      throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, "Error during user creation", e);
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

  private static synchronized boolean createSemossUser(
      TQMCUserInfo u,
      String authProviderType,
      String salt,
      String hashedPassword,
      String createdByUserId,
      String createdByUserType)
      throws SQLException {
    IRDBMSEngine engine = (IRDBMSEngine) Utility.getDatabase(Constants.SECURITY_DB);
    Connection conSec = null;
    try {
      conSec = engine.makeConnection();
      boolean isAutoCommit = conSec.getAutoCommit();
      conSec.setAutoCommit(false);
      try {
        boolean userFound = false;

        try (PreparedStatement ps =
            conSec.prepareStatement(
                "SELECT COUNT(*), (SELECT TOP(1) 1 FROM SMSS_USER WHERE ID = ? AND TYPE = ?) FROM SMSS_USER")) {
          ps.setString(1, u.getUserId());
          ps.setString(2, authProviderType);
          if (ps.execute()) {
            ResultSet rs = ps.getResultSet();
            if (rs.next()) {
              int currentUserCount = rs.getInt(1);
              userFound = rs.getInt(2) == 1;

              String userLimitStr = Utility.getDIHelperProperty(Constants.MAX_USER_LIMIT);
              if (userLimitStr != null && !userLimitStr.trim().isEmpty()) {
                try {
                  int userLimit = Integer.parseInt(userLimitStr);
                  if (userLimit > 0 && currentUserCount + 1 > userLimit) {
                    throw new TQMCException(
                        ErrorCode.BAD_REQUEST,
                        "System has reached maximum number of users allowed");
                  }
                } catch (NumberFormatException e) {
                  // intentionally blank. nothing to check if the prop isn't a number
                }
              }
            }
          }
        }

        if (!userFound) {
          try (PreparedStatement ps =
              conSec.prepareStatement(
                  "INSERT INTO SMSS_USER "
                      + "(ID, USERNAME, NAME, EMAIL, PASSWORD, SALT, TYPE, "
                      + "PHONE, PHONEEXTENSION, COUNTRYCODE, ADMIN, PUBLISHER, EXPORTER, DATECREATED) "
                      + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            int parameterIndex = 1;
            ps.setString(parameterIndex++, u.getUserId());
            ps.setString(parameterIndex++, u.getUserId());
            ps.setString(parameterIndex++, u.getFirstName() + " " + u.getLastName());
            ps.setString(parameterIndex++, u.getEmail());
            ps.setString(parameterIndex++, hashedPassword);
            ps.setString(parameterIndex++, salt);
            ps.setString(parameterIndex++, authProviderType);
            ps.setString(parameterIndex++, u.getPhone());
            ps.setString(parameterIndex++, "");
            ps.setString(parameterIndex++, "");
            ps.setBoolean(parameterIndex++, false);
            ps.setBoolean(parameterIndex++, true);
            ps.setBoolean(parameterIndex++, false);
            ps.setObject(parameterIndex++, u.getCreatedAt());
            ps.execute();
          }

          if (hashedPassword != null) {
            try {
              SecurityNativeUserUtils.storeUserPassword(
                  u.getUserId(),
                  authProviderType,
                  hashedPassword,
                  salt,
                  Utility.getCurrentSqlTimestampUTC());
            } catch (Exception e) {
              LOGGER.warn("Failed to store password history for " + u.getUserId());
            }
          }
        } else {
          // even if they exist already we need to make sure they have upload permission
          try (PreparedStatement ps =
              conSec.prepareStatement(
                  "UPDATE SMSS_USER SET PUBLISHER = 'TRUE' WHERE ID = ? AND TYPE = ?")) {
            int parameterIndex = 1;
            ps.setString(parameterIndex++, u.getUserId());
            ps.setString(parameterIndex++, authProviderType);
            ps.execute();
          }
        }

        conSec.commit();

        return !userFound;
      } catch (SQLException e) {
        conSec.rollback();
        throw e;
      } finally {
        conSec.setAutoCommit(isAutoCommit);
      }
    } finally {
      if (engine.isConnectionPooling() && conSec != null) {
        conSec.close();
      }
    }
  }

  private static void sendUserCreationEmail(TQMCUserInfo u, String password) {
    sendUserCreationEmail(u, password, null);
  }

  private static void sendUserCreationEmail(
      TQMCUserInfo u, String password, String[] fileAttachments) {
    // if email isn't enabled, they will have to do admin password reset workflow
    if (SocialPropertiesUtil.getInstance().smtpEmailEnabled()) {
      String message;
      if (password != null) {
        Map<String, String> placeholderValues = new HashMap<>();
        placeholderValues.put("USERNAME", u.getUserId());
        placeholderValues.put("CRED", password);
        placeholderValues.put("URL", TQMCProperties.getInstance().getTqmcUrl());
        placeholderValues.put(
            "ATTACHMENT_MESSAGE",
            fileAttachments == null
                ? ""
                : "<p>More details on the login process are in the attached document"
                    + (fileAttachments.length > 1 ? "s" : "")
                    + ".</p>");
        StrSubstitutor sub = new StrSubstitutor(placeholderValues);
        message = sub.replace(TQMCConstants.PWD_ACCOUNT_CREATION_MESSAGE_TEMPLATE);
      } else {
        Map<String, String> placeholderValues = new HashMap<>();
        placeholderValues.put("URL", TQMCProperties.getInstance().getTqmcUrl());
        StrSubstitutor sub = new StrSubstitutor(placeholderValues);
        message = sub.replace(TQMCConstants.CAC_ACCOUNT_CREATION_MESSAGE_TEMPLATE);
      }
      try {
        Session emailSession = SocialPropertiesUtil.getInstance().getEmailSession();
        EmailUtility.sendEmail(
            emailSession,
            new String[] {u.getEmail()},
            null,
            null,
            TQMCConstants.EMAIL_SENDER,
            "TQMC Account Creation",
            message,
            true,
            fileAttachments);
      } catch (Exception e) {
        LOGGER.error("Failed to send account creation email for user " + u.getUserId(), e);
        throw new TQMCException(
            ErrorCode.INTERNAL_SERVER_ERROR, "Error sending email - TQMC account WAS NOT created");
      }
    }
  }

  private static void sendPasswordResetEmail(TQMCUserInfo u, String[] fileAttachments) {
    // if email isn't enabled, they will have to do admin password reset workflow
    Map<String, String> placeholderValues = new HashMap<>();
    placeholderValues.put("USERNAME", u.getEmail());
    placeholderValues.put("URL", TQMCProperties.getInstance().getTqmcUrl());
    if (fileAttachments != null && fileAttachments.length > 0) {
      placeholderValues.put(
          "ATTACHMENT_MESSAGE",
          fileAttachments == null
              ? ""
              : "<p>More details on the login process are in the attached document"
                  + (fileAttachments.length > 1 ? "s" : "")
                  + ".</p>");
    } else {
      placeholderValues.put("ATTACHMENT_MESSAGE", "");
    }
    StrSubstitutor sub = new StrSubstitutor(placeholderValues);

    String message = sub.replace(TQMCConstants.MANUAL_LINOTP_RESET_TEMPLATE);

    try {
      Session emailSession = SocialPropertiesUtil.getInstance().getEmailSession();
      EmailUtility.sendEmail(
          emailSession,
          new String[] {u.getEmail()},
          null,
          null,
          TQMCConstants.EMAIL_SENDER,
          "TQMC Account Creation",
          message,
          true,
          fileAttachments);
    } catch (TQMCException e) {
      LOGGER.warn("Failed to send password reset success email to " + u.getEmail());
      throw new TQMCException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Error sending email - TQMC account WAS NOT created");
    }
  }
}
