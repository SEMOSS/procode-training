package tqmc.reactors.base;

import jakarta.mail.Session;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang.text.StrSubstitutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.AssetUtility;
import prerna.util.EmailUtility;
import prerna.util.SocialPropertiesUtil;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.user.TQMCUserInfo;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;
import tqmc.util.TQMCProperties;

public class ManualAccountResetReactor extends AbstractTQMCReactor {

  private static final Logger LOGGER = LogManager.getLogger(ManualAccountResetReactor.class);

  public ManualAccountResetReactor() {
    this.keysToGet = new String[] {TQMCConstants.USER_ID};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    if (!(hasRole(TQMCConstants.MANAGEMENT_LEAD)) || hasRole(TQMCConstants.CONTRACTING_LEAD)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    TQMCUserInfo u = TQMCHelper.getTQMCUserInfo(con, this.keyValue.get(TQMCConstants.USER_ID));
    if (u == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND, "User not in system");
    }

    String[] attachments = null;
    String docsProjectId = tqmcProperties.getDocsProjectId();
    if (docsProjectId != null) {
      String projectAssetDirectory = AssetUtility.getProjectAssetsFolder(docsProjectId);
      String userGuidePath =
          Paths.get(projectAssetDirectory, "portals/" + tqmcProperties.getLoginGuideName())
              .normalize()
              .toString();
      // String userGuideVideoPath =
      //     Paths.get(projectAssetDirectory, "portals/log_in_video.mp4").normalize().toString();
      attachments = new String[] {userGuidePath};
    }

    Map<String, String> placeholderValues = new HashMap<>();
    placeholderValues.put("USERNAME", u.getEmail());
    placeholderValues.put("URL", TQMCProperties.getInstance().getTqmcUrl());
    if (attachments.length > 0) {
      placeholderValues.put(
          "ATTACHMENT_MESSAGE",
          attachments == null
              ? ""
              : "<p>More details on the login process are in the attached document"
                  + (attachments.length > 1 ? "s" : "")
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
          TQMCConstants.CCS,
          TQMCConstants.DOTCOM_BCCS,
          TQMCConstants.EMAIL_SENDER,
          "TQMC Account Creation",
          message,
          true,
          attachments);
    } catch (TQMCException e) {
      LOGGER.warn("Failed to send password reset success email to " + u.getEmail());
    }
    return new NounMetadata(true, PixelDataType.BOOLEAN);
  }
}
