package tqmc.reactors.user;

import com.google.common.collect.Lists;
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
import tqmc.domain.base.Filter.Comparison;
import tqmc.domain.base.GetListPayload;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetUsersReactor extends AbstractTQMCReactor {

  private static final String PRODUCTS_MATCH_QUERY =
      "SELECT TOP(1) 1 FROM TQMC_USER_PRODUCT tup INNER JOIN TQMC_PRODUCT tp ON tp.PRODUCT_ID = tup.PRODUCT_ID AND tp.IS_ACTIVE = 1";
  private static final String MEASURES_MATCH_QUERY =
      "SELECT TOP(1) 1 FROM ORYX_USER_MEASURE_SET oums INNER JOIN ORYX_MEASURE_SET oms ON  oms.MEASURE_SET_ID = oums.MEASURE_SET_ID AND oms.IS_ACTIVE = 1";
  private static final String GET_USERS_INNER_QUERY_TEMPLATE =
      "SELECT USER_ID, EMAIL, FIRST_NAME, LAST_NAME, NAME_QUERY, ROLE, PRODUCTS, MEASURES, SPECIALTY_NAME, SUBSPECIALTY_NAME, SPECIALTY_ID, CREATED_AT, COUNT(*) OVER() AS TOTAL_ROW_COUNT "
          + "FROM "
          + "( "
          + "    SELECT tu.USER_ID, tu.EMAIL, tu.FIRST_NAME, tu.LAST_NAME, CONCAT(tu.LAST_NAME, ', ', tu.FIRST_NAME, ' ', tu.LAST_NAME) as NAME_QUERY, tu.\"ROLE\", "
          + "           (SELECT LISTAGG(tup.PRODUCT_ID) "
          + "            FROM TQMC_USER_PRODUCT tup "
          + "            INNER JOIN TQMC_PRODUCT tp ON tp.PRODUCT_ID = tup.PRODUCT_ID AND tp.IS_ACTIVE = 1 "
          + "            WHERE tup.USER_ID = tu.USER_ID) AS PRODUCTS, "
          + "           (%PRODUCTS_MATCH%) AS PRODUCTS_MATCH, "
          + "           (SELECT LISTAGG(oums.MEASURE_SET_ID) WITHIN GROUP (ORDER BY oums.MEASURE_SET_ID) "
          + "            FROM ORYX_USER_MEASURE_SET oums "
          + "            INNER JOIN ORYX_MEASURE_SET oms ON oms.MEASURE_SET_ID = oums.MEASURE_SET_ID AND oms.IS_ACTIVE = 1 "
          + "            WHERE oums.USER_ID = tu.USER_ID) AS MEASURES, "
          + "           (%MEASURES_MATCH%) AS MEASURES_MATCH, "
          + "           COALESCE(ts.SPECIALTY_NAME, '') AS SPECIALTY_NAME, "
          + "           COALESCE(ts.SUBSPECIALTY_NAME, '') AS SUBSPECIALTY_NAME, "
          + "           tus.SPECIALTY_ID AS SPECIALTY_ID, "
          + "           tu.CREATED_AT, "
          + "    FROM TQMC_USER tu "
          + "    LEFT OUTER JOIN TQMC_USER_SPECIALTY tus ON tus.USER_ID = tu.USER_ID "
          + "    LEFT OUTER JOIN TQMC_SPECIALTY ts ON ts.SPECIALTY_ID = tus.SPECIALTY_ID "
          + "    WHERE tu.IS_ACTIVE = 1 "
          + ") ";

  private static final List<String> VALID_SORT_FIELDS =
      Lists.newArrayList(
          "EMAIL",
          "LAST_NAME",
          "FIRST_NAME",
          "ROLE",
          "CREATED_AT",
          "SPECIALTY_NAME",
          "SUBSPECIALTY_NAME");

  private static final List<String> VALID_FILTER_FIELDS =
      Lists.newArrayList(
          "EMAIL",
          "NAME_QUERY",
          "ROLE",
          "PRODUCT",
          "MEASURE_SET",
          "CREATED_AT",
          "SPECIALTY_NAME",
          "SUBSPECIALTY_NAME",
          "USER_ID");

  private static final List<String> DEFAULT_ORDER_FIELDS = Lists.newArrayList("USER_ID ASC");

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    if (!(hasRole(TQMCConstants.MANAGEMENT_LEAD)
        || hasRole(TQMCConstants.ADMIN)
        || hasRole(TQMCConstants.CONTRACTING_LEAD))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    GetListPayload payload =
        TQMCHelper.getListPayloadObject(
            this.getNounStore(), VALID_SORT_FIELDS, VALID_FILTER_FIELDS);

    List<String> arguments = new ArrayList<>();

    // filters on the joined many-to-one sub-tables need special handling
    String productsMatchSubquery = "1";
    if (payload.getCollatedFilters() != null
        && payload.getCollatedFilters().containsKey("PRODUCT")) {
      GetListPayload productsPayload = new GetListPayload();
      Map<String, Map<Comparison, List<String>>> productsFilter = new HashMap<>();
      productsFilter.put("tup.PRODUCT_ID", payload.getCollatedFilters().remove("PRODUCT"));
      productsPayload.setCollatedFilters(productsFilter);

      productsMatchSubquery =
          TQMCHelper.getListQuery(null, productsPayload, PRODUCTS_MATCH_QUERY, arguments)
              + " AND tup.USER_ID = tu.USER_ID";
    }

    String measuresMatchSubquery = "1";
    if (payload.getCollatedFilters() != null
        && payload.getCollatedFilters().containsKey("MEASURE_SET")) {
      GetListPayload measuresPayload = new GetListPayload();
      Map<String, Map<Comparison, List<String>>> measuresFilter = new HashMap<>();
      measuresFilter.put("oums.MEASURE_SET_ID", payload.getCollatedFilters().remove("MEASURE_SET"));
      measuresPayload.setCollatedFilters(measuresFilter);

      measuresMatchSubquery =
          TQMCHelper.getListQuery(null, measuresPayload, MEASURES_MATCH_QUERY, arguments)
              + " AND oums.USER_ID = tu.USER_ID";
    }

    Map<Comparison, List<String>> match = new HashMap<>();
    match.put(Comparison.EQUAL, Lists.newArrayList("1"));
    if (payload.getCollatedFilters() == null) {
      payload.setCollatedFilters(new HashMap<>());
    }
    payload.getCollatedFilters().put("PRODUCTS_MATCH", match);
    payload.getCollatedFilters().put("MEASURES_MATCH", match);

    String query =
        GET_USERS_INNER_QUERY_TEMPLATE
            .replace("LISTAGG", TQMCHelper.getGroupConcatFunctionSyntax(engineType))
            .replace("%PRODUCTS_MATCH%", productsMatchSubquery)
            .replace("%MEASURES_MATCH%", measuresMatchSubquery);
    query = TQMCHelper.getListQuery(null, payload, query, arguments, DEFAULT_ORDER_FIELDS);

    Map<String, Object> results = new HashMap<>();
    int totalCount = 0;
    List<Map<String, Object>> users = new ArrayList<>();
    try (PreparedStatement ps = con.prepareStatement(query)) {
      int parameterIndex = 1;
      for (String s : arguments) {
        ps.setString(parameterIndex++, s);
      }
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          Map<String, Object> u = new HashMap<>();
          u.put("user_id", rs.getString("USER_ID"));
          u.put("email", rs.getString("EMAIL"));
          u.put("first_name", rs.getString("FIRST_NAME"));
          u.put("last_name", rs.getString("LAST_NAME"));
          u.put("name_query", rs.getString("NAME_QUERY"));
          u.put("role", rs.getString("ROLE"));

          String productsString = rs.getString("PRODUCTS");
          u.put("products", productsString == null ? new String[0] : productsString.split(","));

          String measuresString = rs.getString("MEASURES");
          u.put("measure_sets", measuresString == null ? new String[0] : measuresString.split(","));
          String specialtyId = rs.getString("SPECIALTY_ID");
          u.put("specialty_id", specialtyId);
          u.put(
              "created_at",
              ConversionUtils.getLocalDateTimeStringFromTimestamp(rs.getTimestamp("CREATED_AT")));
          if (totalCount == 0) {
            totalCount = rs.getInt("TOTAL_ROW_COUNT");
          }
          users.add(u);
        }
      }
    }

    results.put("total_row_count", totalCount);
    results.put("users", users);
    return new NounMetadata(results, PixelDataType.MAP);
  }
}
