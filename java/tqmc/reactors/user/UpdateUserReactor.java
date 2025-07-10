package tqmc.reactors.user;

import com.fasterxml.jackson.core.type.TypeReference;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.CaseDetails;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.mapper.CustomMapper;
import tqmc.domain.user.TQMCUserInfo;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class UpdateUserReactor extends AbstractTQMCReactor {

  private static final String USER = "user";

  private final LocalDateTime localTime = ConversionUtils.getUTCFromLocalNow();

  public UpdateUserReactor() {
    this.keysToGet = new String[] {USER};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    return doExecute(con, false);
  }

  protected NounMetadata doExecute(Connection con, boolean deletedUser) throws SQLException {

    if (!(hasRole(TQMCConstants.MANAGEMENT_LEAD)
        || hasRole(TQMCConstants.ADMIN)
        || hasRole(TQMCConstants.CONTRACTING_LEAD))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);

    TQMCUserInfo newU = payload.getUser();

    if (newU == null
        || newU.getUserId() == null
        || newU.getFirstName() == null
        || newU.getLastName() == null
        || newU.getRole() == null
        || newU.getProducts() == null
        || newU.getEmail() == null
        || (!deletedUser && newU.getUpdatedAt() == null)) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Missing required fields during update");
    }

    if (!TQMCConstants.USER_ROLES.contains(newU.getRole())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid role");
    }

    // limitations specific to contracting lead
    if (hasRole(TQMCConstants.CONTRACTING_LEAD)) {
      // Consulting Leads are not allowed to create management leads or admins
      if (TQMCConstants.MANAGEMENT_LEAD.equalsIgnoreCase(newU.getRole())
          || TQMCConstants.ADMIN.equalsIgnoreCase(newU.getRole())) {
        throw new TQMCException(ErrorCode.FORBIDDEN);
      }
      // check if the lead has access to the products they are assigning
      if (!TQMCConstants.VALID_ROLE_PRODUCT_MAP
          .get(TQMCConstants.CONTRACTING_LEAD)
          .containsAll(newU.getProducts())) {
        throw new TQMCException(
            ErrorCode.FORBIDDEN, "Contracting lead cannot give access to one or more products");
      }
    }

    Set<String> npiRequired = new HashSet<>(newU.getProducts());
    Set<String> phoneRequired = new HashSet<>(newU.getProducts());
    npiRequired.retainAll(TQMCConstants.NEED_NPI);
    phoneRequired.removeAll(TQMCConstants.NEED_NPI);

    if (!newU.getUserId().equals(newU.getUserId().toLowerCase())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Username must use lowercase characters");
    }

    if (!phoneRequired.isEmpty() && newU.getPhone() == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Phone number mandatory for GTT");
    }

    if (newU.getPhone() != null && !newU.getPhone().matches("\\d{10}")) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Phone number requires 10 numeric digits");
    }

    if (TQMCConstants.PEER_REVIEWER.equalsIgnoreCase(newU.getRole())) {
      if (newU.getSpecialtyIds().isEmpty()) {
        throw new TQMCException(ErrorCode.BAD_REQUEST, "Specialty is required for peer reviewers");
      }
    }

    if (!npiRequired.isEmpty() && newU.getNpi() == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "NPI required for one of your products");
    }

    if (newU.getNpi() != null && !newU.getNpi().matches("\\d{10}")) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "NPI requires 10 numeric digits");
    }

    // checks that the products are applicable to the Role
    if (!TQMCHelper.validateRoleProduct(newU.getRole(), newU.getProducts())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid product selection");
    }

    TQMCUserInfo u = TQMCHelper.getTQMCUserInfo(con, payload.getUser().getUserId());
    if (u == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND);
    }

    if (!deletedUser && !u.getUpdatedAt().isEqual(newU.getUpdatedAt())) {
      throw new TQMCException(ErrorCode.CONFLICT);
    }

    boolean isMilInstance = this.tqmcProperties.getIsMilInstance();
    if (isMilInstance && !newU.getUserId().matches("\\d{10}")) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "ID must be 10 digits");
    }

    Set<String> validProducts = new HashSet<>();
    try (PreparedStatement ps =
        con.prepareStatement("SELECT product_id FROM TQMC_PRODUCT WHERE IS_ACTIVE=1")) {
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          validProducts.add(rs.getString(1));
        }
      }
    }

    for (String product : newU.getProducts()) {
      if (!validProducts.contains(product)) {
        throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid product selection");
      }
    }

    Set<String> productsToBeAdded = new HashSet<>(newU.getProducts());
    Set<String> productsToBeRemoved = new HashSet<>(u.getProducts());

    productsToBeAdded.removeAll(u.getProducts());
    productsToBeRemoved.removeAll(newU.getProducts());

    Set<String> validSpecialties = TQMCHelper.getValidSpecialtyIds(con);
    for (String sId : newU.getSpecialtyIds()) {
      if (!validSpecialties.contains(sId)) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "Specialty/subspecialty combination not found");
      }
    }

    Set<String> specialtiesToBeAdded = new HashSet<>(newU.getSpecialtyIds());
    Set<String> specialtiesToBeRemoved = new HashSet<>(u.getSpecialtyIds());

    specialtiesToBeAdded.removeAll(u.getSpecialtyIds());
    specialtiesToBeRemoved.removeAll(newU.getSpecialtyIds());

    Set<CaseDetails> userCases = TQMCHelper.getProductCaseAssignments(con, u);

    for (CaseDetails d : userCases) {
      if (!newU.getProducts().contains(d.getProduct())) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "User has active " + d.getProduct().toUpperCase() + " cases");
      }

      if (d.getHasFiles() && (newU.getNpi() == null)) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "Can not remove NPI on user with active case");
      }

      if (!u.getRole().equalsIgnoreCase(newU.getRole())) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "User has open " + d.getProduct().toUpperCase() + " cases");
      }
    }

    newU.setIsActive(u.getIsActive());
    newU.setCreatedAt(u.getCreatedAt());
    newU.setUpdatedAt(localTime);

    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE TQMC_USER SET first_name = ?, last_name = ?, role = ?, email = ?, phone = ?, is_active = ?, npi = ?, created_at = ?, updated_at = ? WHERE user_id = ?")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, newU.getFirstName());
      ps.setString(parameterIndex++, newU.getLastName());
      ps.setString(parameterIndex++, newU.getRole());
      ps.setString(parameterIndex++, newU.getEmail());
      ps.setString(parameterIndex++, newU.getPhone());
      ps.setInt(parameterIndex++, 1);
      ps.setString(parameterIndex++, newU.getNpi());
      ps.setObject(parameterIndex++, newU.getCreatedAt());
      ps.setObject(parameterIndex++, newU.getUpdatedAt());
      ps.setString(parameterIndex++, newU.getUserId());
      ps.execute();
    }

    // insert product-user combinations into table if it does not already exist and need to be there
    if (!productsToBeAdded.isEmpty()) {
      try (PreparedStatement ps =
          con.prepareStatement(
              "INSERT INTO TQMC_USER_PRODUCT (user_product_id, user_id, product_id) "
                  + " VALUES (?, ?, ?)")) {
        for (String product : productsToBeAdded) {
          int parameterIndex = 1;
          ps.setString(parameterIndex++, UUID.randomUUID().toString());
          ps.setString(parameterIndex++, newU.getUserId());
          ps.setString(parameterIndex++, product);
          ps.addBatch();
        }
        ps.executeBatch();
      }
    }

    // remove product-user combinations that exist in the table but are no longer applicable
    if (!productsToBeRemoved.isEmpty()) {
      try (PreparedStatement ps =
          con.prepareStatement(
              "DELETE FROM TQMC_USER_PRODUCT WHERE user_id = ? AND product_id = ?")) {
        for (String product : productsToBeRemoved) {
          int parameterIndex = 1;
          ps.setString(parameterIndex++, newU.getUserId());
          ps.setString(parameterIndex++, product);
          ps.addBatch();
        }
        ps.executeBatch();
      }
    }

    if (!specialtiesToBeAdded.isEmpty()) {
      try (PreparedStatement ps =
          con.prepareStatement(
              "INSERT INTO TQMC_USER_SPECIALTY (user_specialty_id, user_id, specialty_id) "
                  + " VALUES (?, ?, ?)")) {
        for (String specialtyId : specialtiesToBeAdded) {
          int parameterIndex = 1;
          ps.setString(parameterIndex++, UUID.randomUUID().toString());
          ps.setString(parameterIndex++, newU.getUserId());
          ps.setString(parameterIndex++, specialtyId);
          ps.addBatch();
        }
        ps.executeBatch();
      }
    }

    if (!specialtiesToBeRemoved.isEmpty()) {
      try (PreparedStatement ps =
          con.prepareStatement(
              "DELETE FROM TQMC_USER_SPECIALTY WHERE user_id = ? AND specialty_id = ?")) {
        for (String specialtyId : specialtiesToBeRemoved) {
          int parameterIndex = 1;
          ps.setString(parameterIndex++, newU.getUserId());
          ps.setString(parameterIndex++, specialtyId);
          ps.addBatch();
        }
        ps.executeBatch();
      }
    }

    return new NounMetadata(
        CustomMapper.MAPPER.convertValue(newU, new TypeReference<Map<String, Object>>() {}),
        PixelDataType.MAP);
  }

  public static class Payload {
    private TQMCUserInfo user;

    public TQMCUserInfo getUser() {
      return user;
    }

    public void setUser(TQMCUserInfo user) {
      this.user = user;
    }
  }
}
