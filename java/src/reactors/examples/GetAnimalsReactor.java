package reactors.examples;

import java.sql.Connection;
import java.sql.SQLException;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import reactors.AbstractProjectReactor;
import util.HelperMethods;

public class GetAnimalsReactor extends AbstractProjectReactor {

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    // List<MTFData> output = new ArrayList<>();
    // try (PreparedStatement mtfQuery =
    //     con.prepareStatement(
    //         "SELECT DMIS_ID, MTF_NAME, COALESCE(alias_mtf_name, mtf_name) AS ALIAS_MTF_NAME FROM
    // MTF ORDER BY LOWER(COALESCE(alias_mtf_name, mtf_name)) ASC"); ) {
    //   if (mtfQuery.execute()) {
    //     ResultSet rs = mtfQuery.getResultSet();
    //     while (rs.next()) {
    //       String id = rs.getString("DMIS_ID");
    //       String name = rs.getString("MTF_NAME");
    //       String alias = rs.getString("ALIAS_MTF_NAME");
    //       MTFData row = new MTFData(name, id, alias);
    //       output.add(row);
    //     }
    //   }
    // }
    int output = HelperMethods.addIntegerExampleHelper(con, 5, 7);

    return new NounMetadata(output, PixelDataType.CONST_INT);
  }
}
