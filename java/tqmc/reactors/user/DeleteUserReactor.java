package tqmc.reactors.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.user.TQMCUserInfo;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class DeleteUserReactor extends AbstractTQMCReactor {

  private LocalDateTime currentTimestamp = ConversionUtils.getUTCFromLocalNow();

  private String BASE_QUERY =
      "SELECT tu.USER_ID, tup.PRODUCT_ID, tu.IS_ACTIVE, tu.ROLE, "
          + "tu.EMAIL, tu.FIRST_NAME, tu.LAST_NAME, tu.PHONE, tu.CREATED_AT, "
          + "tu.UPDATED_AT FROM TQMC_USER tu "
          + "LEFT OUTER JOIN TQMC_USER_PRODUCT tup "
          + "ON tup.USER_ID = tu.USER_ID "
          + "INNER JOIN TQMC_PRODUCT tp "
          + "ON tp.PRODUCT_ID = tup.PRODUCT_ID AND tp.IS_ACTIVE = 1 "
          + "WHERE tu.USER_ID IN (%s)";

  public DeleteUserReactor() {
    this.keysToGet = new String[] {TQMCConstants.USER_ID + "s"};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    // checks permission
    if (!hasRole(TQMCConstants.MANAGEMENT_LEAD) && !hasRole(TQMCConstants.ADMIN)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);

    // Get info for all users to be deleted
    Map<String, TQMCUserInfo> userMap = new HashMap<>();
    List<String> userIds = payload.getUserIds();
    // List of products and users related to checking case assignment
    // By doing it for product instead of by user, reduces the number of queries
    Map<String, Set<String>> productUserMap = new HashMap<>();

    String placeholders = String.join(",", userIds.stream().map(id -> "?").toArray(String[]::new));
    String query = String.format(BASE_QUERY, placeholders);

    try (PreparedStatement ps = con.prepareStatement(query)) {
      int parameterIndex = 1;
      for (String uId : userIds) {
        ps.setString(parameterIndex++, uId);
      }
      ps.execute();
      ResultSet rs = ps.getResultSet();
      while (rs.next()) {
        TQMCUserInfo tui = new TQMCUserInfo();
        String tempUserId = rs.getString("USER_ID");
        // Check if the other rows were added and will just add a product if it already exists
        if (!userMap.containsKey(tempUserId)) {
          tui.setUserId(tempUserId);
          // product added here if new to the map
          String up = rs.getString("PRODUCT_ID");
          if (up != null) {
            tui.getProducts().add(up);
            if (!productUserMap.containsKey(up)) {
              productUserMap.put(up, new HashSet<>());
            }
            productUserMap.get(up).add(tempUserId);
          }
          tui.setIsActive(rs.getInt("IS_ACTIVE") == 1);
          tui.setRole(rs.getString("ROLE"));
          tui.setEmail(rs.getString("EMAIL"));
          tui.setFirstName(rs.getString("FIRST_NAME"));
          tui.setLastName(rs.getString("LAST_NAME"));
          tui.setPhone(rs.getString("PHONE"));
          tui.setCreatedAt(
              ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("CREATED_AT")));
          tui.setUpdatedAt(
              ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("UPDATED_AT")));
          // if any user is not in the system, throw error
          if (tui == null || tui.getUserId() == null) {
            throw new TQMCException(ErrorCode.NOT_FOUND, "User " + tempUserId + " not found");
          }
          // is user active
          else if (!tui.getIsActive()) {
            throw new TQMCException(
                ErrorCode.BAD_REQUEST, "User " + tempUserId + " is already deleted");
          }
          // If the user exists and has no cases assigned, add to the list
          userMap.put(tempUserId, tui);
        } else {
          String up = rs.getString(2);
          userMap.get(tempUserId).getProducts().add(up);
          if (!productUserMap.containsKey(up)) {
            productUserMap.put(up, new HashSet<>());
          }
          productUserMap.get(up).add(tempUserId);
        }
      }
    }

    for (String id : userIds) {
      if (!userMap.containsKey(id)) {
        throw new TQMCException(ErrorCode.NOT_FOUND, "User " + id + " not found");
      }
    }

    // Check if the products the users are assigned to has no open cases
    Set<String> usersAssigned = new HashSet<>();
    for (String product : productUserMap.keySet()) {
      usersAssigned.addAll(getProductCaseAssignments(con, product, productUserMap.get(product)));
    }
    // Do people have cases open?
    if (!usersAssigned.isEmpty()) {
      List<String> userNames = new ArrayList<>();
      // getting the first names of people with cases opened
      for (String id : usersAssigned) {
        userNames.add(userMap.get(id).getFirstName());
      }
      throw new TQMCException(
          ErrorCode.BAD_REQUEST,
          "User(s) "
              + formatNames(userNames)
              + " cannot be deleted while they have open cases"
              + ". Reassign cases before deleting a user.");
    }

    // update User Table to have active as 0
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE TQMC_USER SET IS_ACTIVE = 0, UPDATED_AT = ? WHERE USER_ID = ?")) {
      for (TQMCUserInfo deleteUserInfo : userMap.values()) {
        ps.setObject(1, currentTimestamp);
        ps.setString(2, deleteUserInfo.getUserId());
        ps.addBatch();
      }
      ps.executeBatch();
    }

    // Returns true if successful, unsuccessful will return an error
    return new NounMetadata(true, PixelDataType.BOOLEAN);
  }

  // returns list of people with assigned cases for a product
  private Set<String> getProductCaseAssignments(
      Connection con, String product, Set<String> deleteUserIds) throws SQLException {
    Set<String> usersAssigned = new HashSet<>();
    String placeholders =
        String.join(",", deleteUserIds.stream().map(id -> "?").toArray(String[]::new));
    String query =
        "SELECT RECIPIENT_USER_ID FROM "
            + product.toUpperCase()
            + "_WORKFLOW wf "
            + "WHERE IS_LATEST = 1 AND (STEP_STATUS = 'not_started' OR STEP_STATUS = 'in_progress') "
            + "AND RECIPIENT_USER_ID IN ("
            + placeholders
            + ")";

    if (!TQMCConstants.SOC.equalsIgnoreCase(product)
        && !TQMCConstants.MN.equalsIgnoreCase(product)
        && !TQMCConstants.DP.equalsIgnoreCase(product)
        && !TQMCConstants.MCSC.equalsIgnoreCase(product)
        && !TQMCConstants.GTT.equalsIgnoreCase(product)) {
      // If legacy ORYX or unrecognized will return an empty set
      return usersAssigned;
    }

    try (PreparedStatement ps = con.prepareStatement(query)) {
      int parameterIndex = 1;
      for (String id : deleteUserIds) {
        ps.setString(parameterIndex++, id);
      }
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          // adds users will active cases to the set
          usersAssigned.add(rs.getString(1));
        }
      }
    }
    return usersAssigned;
  }

  // Nicely formats a list of strings
  // example: 1, 2, and 3
  private String formatNames(List<String> names) {
    if (names == null || names.isEmpty()) {
      return "";
    } else if (names.size() == 1) {
      return names.get(0);
    } else if (names.size() == 2) {
      return String.join(" and ", names);
    } else {
      String allButLast = String.join(", ", names.subList(0, names.size() - 1));
      String last = names.get(names.size() - 1);
      return allButLast + ", and " + last;
    }
  }

  public static class Payload {
    private List<String> userIds;

    public List<String> getUserIds() {
      return userIds;
    }

    public void setUserIds(List<String> userIDs) {
      this.userIds = userIDs;
    }
  }
}
