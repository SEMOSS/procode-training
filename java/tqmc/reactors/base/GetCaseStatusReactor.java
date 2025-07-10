package tqmc.reactors.base;

import java.sql.Connection;
import java.sql.SQLException;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.ProductTables;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetCaseStatusReactor extends AbstractTQMCReactor {

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  public GetCaseStatusReactor() {
    this.keysToGet =
        new String[] {TQMCConstants.PRODUCT, TQMCConstants.CASE_ID, TQMCConstants.CASE_TYPE};
    this.keyRequired = new int[] {1, 1, 0};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    String caseId = keyValue.get(TQMCConstants.CASE_ID);
    String caseType = keyValue.get(TQMCConstants.CASE_TYPE);
    String productType = keyValue.get(TQMCConstants.PRODUCT);

    if (!hasProductPermission(productType)
        || (!hasProductManagementPermission(productType)
            && !TQMCHelper.hasCaseAccess(con, userId, caseId, productType))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    if ((productType.equals(TQMCConstants.GTT) || productType.equals(TQMCConstants.SOC))
        && (caseType == null || caseType.isEmpty())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST);
    }

    ProductTables product = getProductLine(con, productType);
    String result = TQMCHelper.getCaseStatus(con, caseId, product, caseType);

    return new NounMetadata(result, PixelDataType.CONST_STRING);
  }

  protected ProductTables getProductLine(Connection con, String product) throws SQLException {
    if (!(TQMCHelper.getActiveProductIds(con).contains(product))) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Must be a valid product");
    }
    ProductTables pt = ProductTables.parseProductId(product);
    return pt;
  }
}
