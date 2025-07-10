package tqmc.reactors.qu.base;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.qu.base.QuRecord;
import tqmc.domain.qu.base.QuRecordPayload;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class UpdateQuRecordReactor extends AbstractTQMCReactor {

  private LocalDateTime currentTimestamp = ConversionUtils.getUTCFromLocalNow();
  private QuRecordPayload payload;
  private QuRecord quRecord;

  public UpdateQuRecordReactor() {
    this.keysToGet = new String[] {"record"};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    loadAndValidate(con);

    updateQuRecord(con);

    TQMCHelper.updateRecordFiles(
        con,
        insight,
        projectId,
        TQMCHelper.getQuProductTable(con, quRecord.getRecordId()),
        quRecord.getRecordId(),
        quRecord.getFiles());

    return new NounMetadata(TQMCHelper.getQuRecord(con, quRecord.getRecordId()), PixelDataType.MAP);
  }

  private void updateQuRecord(Connection con) throws SQLException {
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE "
                + TQMCConstants.TABLE_QU_RECORD
                + " SET CASE_LENGTH_DAYS = ?, ALIAS_RECORD_ID = ?, CLAIM_NUMBER = ?, REQUESTED_AT = ?, PATIENT_FIRST_NAME = ?, PATIENT_LAST_NAME = ?, RECEIVED_AT = ?, MISSING_RECEIVED_AT = ?, MISSING_REQUESTED_AT = ?, UPDATED_AT = ? WHERE RECORD_ID = ?")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, quRecord.getCaseLengthDays());
      ps.setString(parameterIndex++, quRecord.getAliasRecordId());
      ps.setString(parameterIndex++, quRecord.getClaimNumber());
      ps.setString(parameterIndex++, quRecord.getRequestedAt());
      ps.setString(parameterIndex++, quRecord.getPatientFirstName());
      ps.setString(parameterIndex++, quRecord.getPatientLastName());
      ps.setString(parameterIndex++, quRecord.getReceivedAt());
      ps.setString(parameterIndex++, quRecord.getMissingReceivedAt());
      ps.setString(parameterIndex++, quRecord.getMissingRequestedAt());
      ps.setString(parameterIndex++, currentTimestamp.toString());
      ps.setString(parameterIndex++, quRecord.getRecordId());
      ps.execute();
    }
  }

  private void loadAndValidate(Connection con) throws SQLException {
    if (!(hasProductManagementPermission(TQMCConstants.DP)
        || hasProductManagementPermission(TQMCConstants.MCSC))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    payload = TQMCHelper.getPayloadObject(store, keysToGet, QuRecordPayload.class);

    if (payload.getRecord() == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Inputs are required to complete action");
    }

    quRecord = payload.getRecord();

    if (quRecord.getRecordId() == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Record ID is null.");
    }

    int caseLengthDays;
    try {
      caseLengthDays = Integer.parseInt(quRecord.getCaseLengthDays());
      if (caseLengthDays < 0) {
        throw new TQMCException(ErrorCode.BAD_REQUEST, "Case length days is invalid");
      }
    } catch (NumberFormatException e) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, e);
    }

    if (quRecord.getRequestedAt() == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Requested at date is null");
    }

    if (!TQMCHelper.canUpdate(con, TQMCConstants.TABLE_QU_RECORD, quRecord)) {
      throw new TQMCException(ErrorCode.CONFLICT);
    }
  }
}
