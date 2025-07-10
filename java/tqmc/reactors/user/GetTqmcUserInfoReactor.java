package tqmc.reactors.user;

import com.fasterxml.jackson.core.type.TypeReference;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.mapper.CustomMapper;
import tqmc.reactors.AbstractTQMCReactor;

/** Gets a single user's information and active products related to them */
public class GetTqmcUserInfoReactor extends AbstractTQMCReactor {

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  public NounMetadata doExecute(Connection con) throws SQLException {
    if (tqmcUserInfo == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND, "User not found");
    }
    return new NounMetadata(
        CustomMapper.MAPPER.convertValue(tqmcUserInfo, new TypeReference<Map<String, Object>>() {}),
        PixelDataType.MAP);
  }
}
