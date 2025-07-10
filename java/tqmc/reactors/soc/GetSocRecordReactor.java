package tqmc.reactors.soc;

import java.sql.Connection;
import java.sql.SQLException;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetSocRecordReactor extends AbstractTQMCReactor {

  public static final String SOC_RECORD_QUERY =
      "SELECT sr.RECORD_ID, sr.UPDATED_AT, sr.ALIAS_RECORD_ID, sr.PATIENT_LAST_NAME, sr.PATIENT_FIRST_NAME,"
          + " snr.DUE_DATE_REVIEW, snr.DUE_DATE_DHA"
          + " FROM SOC_RECORD sr"
          + " INNER JOIN SOC_NURSE_REVIEW snr"
          + " ON snr.RECORD_ID = sr.RECORD_ID"
          + " WHERE sr.RECORD_ID = ?";

  public static final String MTF_QUERY = "SELECT DMIS_ID FROM SOC_RECORD_MTF WHERE RECORD_ID = ?";

  public static final String PROVIDER_QUERY =
      "SELECT pe.CASE_PROVIDER_EVALUATION_ID, pe.PROVIDER_NAME,"
          + " sc.USER_ID IS NOT NULL AS CASE_ASSIGNED,"
          + " sc.SPECIALTY_ID, "
          + " FROM SOC_CASE_PROVIDER_EVALUATION pe"
          + " INNER JOIN SOC_CASE sc"
          + " ON sc.CASE_ID = pe.CASE_ID AND sc.RECORD_ID = ? AND sc.deleted_at IS NULL AND sc.SUBMISSION_GUID IS NULL "
          + " WHERE pe.DELETED_AT IS NULL"
          + " ORDER BY (sc.USER_ID IS NOT NULL) DESC, sc.CASE_ID";

  public static final String DUE_DATE_QUERY =
      "SELECT "
          + "t1.specialty_id, "
          + "t1.due_date_review, "
          + "t1.due_date_dha, "
          + "COUNT(*) AS provider_count "
          + "FROM "
          + "(SELECT * FROM soc_case sc "
          + "WHERE "
          + "sc.record_id = ? and sc.deleted_at IS NULL) AS t1 "
          + "LEFT OUTER JOIN "
          + "SOC_CASE_PROVIDER_EVALUATION scpe "
          + "ON "
          + "scpe.case_id = t1.case_id "
          + "WHERE "
          + "scpe.deleted_at IS NULL "
          + "GROUP BY "
          + "t1.case_id, "
          + "t1.due_date_review, "
          + "t1.due_date_dha, "
          + "t1.specialty_id;";

  public GetSocRecordReactor() {
    this.keysToGet = new String[] {TQMCConstants.RECORD_ID};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    String queriedRecord = this.keyValue.get(TQMCConstants.RECORD_ID);

    if (!hasProductManagementPermission(TQMCConstants.SOC)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    return TQMCHelper.getSocRecordHelper(con, queriedRecord);
  }
}
