package tqmc.reactors.base;

import java.sql.Connection;
import java.sql.SQLException;
import org.apache.commons.lang3.StringUtils;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.PixelOperationType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.ProductTables;
import tqmc.domain.base.RecordFile;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.FileUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetFileEmbedLinkReactor extends AbstractTQMCReactor {
  public GetFileEmbedLinkReactor() {
    this.keysToGet = new String[] {TQMCConstants.CASE_TYPE, TQMCConstants.RECORD_FILE_ID};
    this.keyRequired = new int[] {0, 1};
  }

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    ProductTables productLine = getProductLine();
    if (!hasProductPermission(productLine.getProductId())) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    RecordFile recordFile =
        TQMCHelper.getRecordFile(con, productLine, keyValue.get(TQMCConstants.RECORD_FILE_ID));
    if (recordFile == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND);
    }

    if (!hasProductManagementPermission(productLine.getProductId())
        && !TQMCHelper.hasRecordAccess(con, userId, recordFile.getRecordId(), productLine)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    FileUtils fileUtils = new FileUtils(projectId);
    fileUtils.downloadFileFromRecord(
        insight,
        productLine.getProductId(),
        recordFile.getRecordId(),
        recordFile.getRecordFileId(),
        recordFile.getFileName(),
        tqmcUserInfo);
    return new NounMetadata(
        recordFile.getRecordFileId(), PixelDataType.CONST_STRING, PixelOperationType.FILE_DOWNLOAD);
  }

  protected ProductTables getProductLine() {
    String caseType = StringUtils.trimToEmpty(keyValue.get(TQMCConstants.CASE_TYPE));
    ProductTables pt = ProductTables.parseProductId(caseType);
    if (pt == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid caseType");
    }
    return pt;
  }
}
