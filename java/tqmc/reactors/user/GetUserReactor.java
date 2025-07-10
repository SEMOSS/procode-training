package tqmc.reactors.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

/*
GetUserReactor will allow management leads to get a specific user's information. It will take in a userid and return the following:

role: Role;
user_id: string;
phone: string;
last_name: string;
first_name: string;
email: string;
products: Product[];
specialty_name: string;
subspecialty_name: string;
*/

public class GetUserReactor extends AbstractTQMCReactor {

  private static final String USER_ID = "userId";
  private static final String GET_USER_QUERY_TEMPLATE =
      "SELECT role, Users.user_id, phone, last_name, first_name, email, LISTAGG(product_id, ',') as products, specialty_id, npi, updated_at "
          + "FROM TQMC_User_Product RIGHT OUTER JOIN ("
          + "SELECT role, last_name, first_name, email, phone, TQMC_User.user_id, TQMC_User_Specialty.specialty_id, npi, updated_at "
          + "FROM TQMC_User LEFT OUTER JOIN TQMC_User_Specialty "
          + "ON TQMC_User.user_id = TQMC_User_Specialty.user_id) as Users "
          + "ON Users.user_id = TQMC_User_Product.user_id WHERE Users.user_id = ? "
          + "GROUP BY Users.role, Users.user_id, Users.phone, Users.last_name, Users.first_name, Users.email, Users.specialty_id, Users.npi, Users.updated_at";

  public GetUserReactor() {
    this.keysToGet = new String[] {USER_ID};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    // check that user has the permissions to get user info (checks if management lead)
    if (!(hasRole(TQMCConstants.MANAGEMENT_LEAD)
        || hasRole(TQMCConstants.ADMIN)
        || hasRole(TQMCConstants.CONTRACTING_LEAD))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    String queriedUser = this.keyValue.get(USER_ID);
    Map<String, Object> c = new HashMap<>();

    // try SQL query
    String query =
        GET_USER_QUERY_TEMPLATE.replace(
            "LISTAGG", TQMCHelper.getGroupConcatFunctionSyntax(engineType));
    try (PreparedStatement ps = con.prepareStatement(query)) {
      ps.setString(1, queriedUser);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();

        // add query results to map
        List<String> specialtyIds = new ArrayList<>();
        while (rs.next()) {
          c.put("role", rs.getString("role"));
          c.put("user_id", rs.getString("user_id"));
          c.put("phone", rs.getString("phone"));
          c.put("last_name", rs.getString("last_name"));
          c.put("first_name", rs.getString("first_name"));
          c.put("email", rs.getString("email"));
          String productsString = rs.getString("products");
          c.put("products", productsString == null ? new String[0] : productsString.split(","));
          String specialtyId = rs.getString("specialty_id");
          if (specialtyId != null) {
            specialtyIds.add(specialtyId);
          }
          c.put("npi", rs.getString("npi"));
          c.put(
              "updated_at",
              ConversionUtils.getLocalDateTimeStringFromTimestamp(rs.getTimestamp("updated_at")));
        }
        c.put("specialty_ids", specialtyIds);
      }

      // check that user being queried exists
      if (c.isEmpty()) {
        throw new TQMCException(ErrorCode.NOT_FOUND, "User is not found");
      }
    }

    return new NounMetadata(c, PixelDataType.MAP);
  }
}
