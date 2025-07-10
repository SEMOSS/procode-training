package tqmc.reactors.gtt;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.gtt.GttCaseTime;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetGttTimerReactor extends AbstractTQMCReactor {

  public GetGttTimerReactor() {
    this.keysToGet = new String[] {TQMCConstants.CASE_ID};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    String caseId = keyValue.get(TQMCConstants.CASE_ID);

    if (!hasProductPermission(TQMCConstants.GTT)
        || (!hasRole(TQMCConstants.ADMIN)
            && !hasRole(TQMCConstants.MANAGEMENT_LEAD)
            && !TQMCHelper.hasGttCaseAccess(con, userId, caseId))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    GttCaseTime gct = TQMCHelper.getGttCaseTime(con, caseId);

    if (gct == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND, "Timer for case not found");
    }

    long timeSeconds = gct.getCumulativeTime() == null ? 0 : gct.getCumulativeTime();
    if (gct.isRunning()) {
      timeSeconds +=
          Duration.between(gct.getStartTime(), ConversionUtils.getUTCFromLocalNow()).getSeconds();
    }

    Map<String, Object> result = new HashMap<String, Object>();
    result.put("time_seconds", timeSeconds);
    result.put("timer_status", gct.isRunning() ? "running" : "paused");

    return new NounMetadata(result, PixelDataType.MAP);
  }
}
