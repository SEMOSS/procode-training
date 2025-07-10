package tqmc.reactors.user;

import com.fasterxml.jackson.core.type.TypeReference;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.mapper.CustomMapper;
import tqmc.domain.user.TQMCUserInfo;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;
import tqmc.util.UserAccountUtils;

public class CreateUserReactor extends UpdateUserReactor {

  public static final String USER = "user";

  private Payload payload;

  public CreateUserReactor() {
    this.keysToGet = new String[] {USER};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);

    if (!(hasRole(TQMCConstants.MANAGEMENT_LEAD)
        || hasRole(TQMCConstants.ADMIN)
        || hasRole(TQMCConstants.CONTRACTING_LEAD))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }
    TQMCUserInfo existingUser = TQMCHelper.getTQMCUserInfo(con, payload.getUser().getUserId());
    if (existingUser != null && !existingUser.getIsActive()) {
      boolean deletedUser = true;
      return super.doExecute(con, deletedUser);
    }
    throwExceptionIfInvalidPayload(con);

    LocalDateTime currentTimestamp = ConversionUtils.getUTCFromLocalNow();
    payload.getUser().setCreatedAt(currentTimestamp);
    payload.getUser().setUpdatedAt(currentTimestamp);

    TQMCHelper.createTQMCUser(con, payload.getUser());

    try {
      String userAccountProvider = tqmcProperties.getUserAccountProvider();
      if ("linotp".equalsIgnoreCase(userAccountProvider)) {
        UserAccountUtils.createLinotpUser(
            payload.getUser(),
            userId,
            user.getPrimaryLogin().toString(),
            tqmcProperties.getDocsProjectId(),
            tqmcProperties.getLoginGuideName());
      } else if ("saml".equalsIgnoreCase(userAccountProvider)) {
        UserAccountUtils.createSamlUser(
            payload.getUser(), userId, user.getPrimaryLogin().toString());
      } else {
        UserAccountUtils.createNativeUser(
            payload.getUser(), userId, user.getPrimaryLogin().toString());
      }
    } catch (Exception e) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST,
          "Error creating user account. Please contact Administrator for support",
          e);
    }

    return new NounMetadata(
        CustomMapper.MAPPER.convertValue(
            payload.getUser(), new TypeReference<Map<String, Object>>() {}),
        PixelDataType.MAP);
  }

  private void throwExceptionIfInvalidPayload(Connection con) throws SQLException {
    TQMCUserInfo u = payload.getUser();

    u.setUserId(StringUtils.trimToNull(u.getUserId()));
    u.setEmail(StringUtils.trimToNull(u.getEmail()));
    u.setFirstName(StringUtils.trimToNull(u.getFirstName()));
    u.setLastName(StringUtils.trimToNull(u.getLastName()));
    u.setPhone(StringUtils.trimToNull(u.getPhone()));
    u.setRole(StringUtils.trimToNull(u.getRole()));

    if (u.getUserId() == null
        || u.getEmail() == null
        || u.getFirstName() == null
        || u.getLastName() == null
        || u.getRole() == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Missing required field");
    }

    // limitations specific to contracting lead
    if (hasRole(TQMCConstants.CONTRACTING_LEAD)) {
      // Consulting Leads are not allowed to create management leads or admins
      if (TQMCConstants.MANAGEMENT_LEAD.equalsIgnoreCase(u.getRole())
          || TQMCConstants.ADMIN.equalsIgnoreCase(u.getRole())) {
        throw new TQMCException(ErrorCode.FORBIDDEN);
      }
      // check if the lead has access to the products they are assigning
      if (!TQMCConstants.VALID_ROLE_PRODUCT_MAP
          .get(TQMCConstants.CONTRACTING_LEAD)
          .containsAll(u.getProducts())) {
        throw new TQMCException(
            ErrorCode.FORBIDDEN,
            "Contracting lead needs product access before granting access to a user");
      }
    }

    if (TQMCConstants.PEER_REVIEWER.equalsIgnoreCase(u.getRole())) {
      if (u.getSpecialtyIds() == null || u.getSpecialtyIds().isEmpty()) {
        throw new TQMCException(ErrorCode.BAD_REQUEST, "Specialty is required for peer reviewers");
      }
    }

    Set<String> npiRequired = new HashSet<>(u.getProducts());
    Set<String> phoneRequired = new HashSet<>(u.getProducts());
    npiRequired.retainAll(TQMCConstants.NEED_NPI);
    phoneRequired.removeAll(TQMCConstants.NEED_NPI);

    if (!u.getUserId().equals(u.getUserId().toLowerCase())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Username must use lowercase characters");
    }

    if (!npiRequired.isEmpty() && u.getNpi() == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "NPI required for one of your products");
    }

    if (u.getNpi() != null && !u.getNpi().matches("\\d{10}")) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "NPI requires 10 numeric digits");
    }

    if (!phoneRequired.isEmpty() && u.getPhone() == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Phone number mandatory for GTT");
    }

    if (u.getPhone() != null && !u.getPhone().matches("\\d{10}")) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Phone number requires 10 numeric digits");
    }

    boolean isMilInstance = this.tqmcProperties.getIsMilInstance();
    if (isMilInstance && u.getUserId() != null && !u.getUserId().matches("\\d{10}")) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "ID must be 10 digits");
    }

    if (!TQMCConstants.USER_ROLES.contains(u.getRole())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid role");
    }

    if (!u.getProducts().isEmpty()) {
      Set<String> activeProducts = TQMCHelper.getActiveProductIds(con);
      if (u.getProducts().stream().anyMatch(p -> !activeProducts.contains(p))
          || !TQMCHelper.validateRoleProduct(u.getRole(), u.getProducts())) {
        throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid product selection");
      }
    }

    if (!u.getSpecialtyIds().isEmpty()) {
      Set<String> validSpecialties = TQMCHelper.getValidSpecialtyIds(con);
      for (String sId : u.getSpecialtyIds()) {
        if (!validSpecialties.contains(sId)) {
          throw new TQMCException(
              ErrorCode.BAD_REQUEST, "Specialty/subspecialty combination not found");
        }
      }
    }

    TQMCUserInfo existingUser = TQMCHelper.getTQMCUserInfo(con, u.getUserId());
    if (existingUser != null && existingUser.getIsActive()) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "User with that id already exists");
    }
  }

  public static class Payload {
    private TQMCUserInfo user;

    public Payload() {}

    public TQMCUserInfo getUser() {
      return user;
    }

    public void setUser(TQMCUserInfo user) {
      this.user = user;
    }
  }
}
