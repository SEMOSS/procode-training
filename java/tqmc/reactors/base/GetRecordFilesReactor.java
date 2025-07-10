package tqmc.reactors.base;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.ProductTables;
import tqmc.domain.base.RecordFile;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetRecordFilesReactor extends AbstractTQMCReactor {
  public GetRecordFilesReactor() {
    this.keysToGet = new String[] {TQMCConstants.PRODUCT, TQMCConstants.RECORD_ID};
    this.keyRequired = new int[] {0, 1};
  }

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    ProductTables productLine = getProductLine();
    String recordId = keyValue.get(TQMCConstants.RECORD_ID);
    if (!(hasRole(TQMCConstants.MANAGEMENT_LEAD) || hasRole(TQMCConstants.CONTRACTING_LEAD))
        && !TQMCHelper.hasRecordAccess(con, userId, recordId, productLine)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    List<RecordFile> recordFiles = TQMCHelper.getRecordFiles(con, productLine, recordId);

    List<Map<String, Object>> outMap =
        recordFiles.parallelStream().map(e -> e.toMap()).collect(Collectors.toList());

    return new NounMetadata(outMap, PixelDataType.VECTOR);
  }

  protected ProductTables getProductLine() {
    String caseType = StringUtils.trimToEmpty(keyValue.get(TQMCConstants.PRODUCT));
    ProductTables pt = ProductTables.parseProductId(caseType);
    if (pt == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid caseType");
    }
    return pt;
  }
}
