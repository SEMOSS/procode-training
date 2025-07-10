package tqmc.reactors.base;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.reactors.mn.GetMnAppealTypesReactor;
import tqmc.reactors.user.GetTqmcUserInfoReactor;
import tqmc.util.TQMCConstants;

public class GetTqmcLoadupInfoReactor extends AbstractTQMCReactor {

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    Map<String, Object> output = new HashMap<>();
    GetTqmcMaintenanceReactor tqmcMaintenanceReactor = new GetTqmcMaintenanceReactor();
    GetTqmcUserInfoReactor tqmcUserInfoReactor = new GetTqmcUserInfoReactor();
    GetActiveProductsReactor getActiveProductsReactor = new GetActiveProductsReactor();
    GetSpecialtyMapReactor getSpecialtyMapReactor = new GetSpecialtyMapReactor();
    GetMTFsReactor getMTFsReactor = new GetMTFsReactor();
    GetTqmcVersionReactor getTqmcVersionReactor = new GetTqmcVersionReactor();
    GetMnAppealTypesReactor getMnAppealTypesReactor = new GetMnAppealTypesReactor();
    tqmcUserInfoReactor.setInsight(this.insight);
    tqmcMaintenanceReactor.setInsight(this.insight);
    getActiveProductsReactor.setInsight(this.insight);
    getSpecialtyMapReactor.setInsight(this.insight);
    getMTFsReactor.setInsight(this.insight);
    getTqmcVersionReactor.setInsight(this.insight);
    getMnAppealTypesReactor.setInsight(this.insight);
    try {
      output.put("GetTqmcMaintenance", tqmcMaintenanceReactor.execute().getValue());
      output.put("GetTqmcUserInfo", tqmcUserInfoReactor.execute().getValue());
      output.put("GetActiveProducts", getActiveProductsReactor.execute().getValue());
      output.put("GetSpecialtyMap", getSpecialtyMapReactor.execute().getValue());
      output.put("GetMTFs", getMTFsReactor.execute().getValue());
      output.put("GetTqmcVersion", getTqmcVersionReactor.execute().getValue());
      if (hasProductPermission(TQMCConstants.MN)) {
        output.put("GetMnAppealTypes", getMnAppealTypesReactor.execute().getValue());
      }
    } catch (Exception e) {
      throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
    }

    return new NounMetadata(output, PixelDataType.MAP);
  }
}
