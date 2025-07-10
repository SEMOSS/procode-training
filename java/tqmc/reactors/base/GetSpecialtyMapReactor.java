package tqmc.reactors.base;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.Specialty;
import tqmc.reactors.AbstractTQMCReactor;

public class GetSpecialtyMapReactor extends AbstractTQMCReactor {

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    Map<String, Map<String, String>> specialties = new HashMap<>();
    String query =
        "SELECT SPECIALTY_ID, SPECIALTY_NAME, SUBSPECIALTY_NAME FROM TQMC_SPECIALTY ORDER BY SPECIALTY_NAME, SUBSPECIALTY_NAME";

    try (PreparedStatement ps = con.prepareStatement(query)) {
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          Specialty s = new Specialty();
          s.setSpecialtyId(rs.getString("SPECIALTY_ID"));
          s.setSpecialtyName(rs.getString("SPECIALTY_NAME"));
          s.setSubspecialtyName(rs.getString("SUBSPECIALTY_NAME"));
          specialties.put(s.getSpecialtyId(), s.toMap());
        }
      }
    }

    return new NounMetadata(specialties, PixelDataType.MAP);
  }
}
