package tqmc.reactors.soc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.ProductTables;
import tqmc.domain.base.RecordFile;
import tqmc.domain.base.TQMCException;
import tqmc.domain.soc.SocCaseType;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetSocCaseFilesReactor extends AbstractTQMCReactor {
  public GetSocCaseFilesReactor() {
    this.keysToGet = new String[] {TQMCConstants.CASE_TYPE, TQMCConstants.CASE_ID};
    this.keyRequired = new int[] {1, 1};
  }

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    String caseId = keyValue.get(TQMCConstants.CASE_ID);
    SocCaseType caseType = null;
    try {
      caseType = SocCaseType.valueOf(keyValue.get(TQMCConstants.CASE_TYPE).toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid case type passed");
    }
    if (!(hasRole(TQMCConstants.MANAGEMENT_LEAD) || hasRole(TQMCConstants.CONTRACTING_LEAD))
        && (caseType == SocCaseType.PEER_REVIEW
            && !TQMCHelper.hasCaseAccess(con, userId, caseId, ProductTables.SOC.getProductId()))
        && (caseType == SocCaseType.NURSE_REVIEW
            && !TQMCHelper.hasNurseReviewEditAccess(con, userId, caseId))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Map<String, RecordFile> recordFiles =
        TQMCHelper.getCaseFiles(con, ProductTables.SOC, caseId, caseType);
    if (recordFiles.isEmpty()) {
      return new NounMetadata(new ArrayList<>(), PixelDataType.VECTOR);
    }

    List<Map<String, Object>> outMap =
        recordFiles.values().parallelStream().map(e -> e.toMap()).collect(Collectors.toList());

    return new NounMetadata(outMap, PixelDataType.VECTOR);
  }
}
