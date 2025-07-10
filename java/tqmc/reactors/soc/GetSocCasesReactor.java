package tqmc.reactors.soc;

import com.google.common.collect.Lists;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.CaseStatus;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.GetListPayload;
import tqmc.domain.base.TQMCException;
import tqmc.domain.soc.SocCaseType;
import tqmc.domain.soc.SocManagementCaseTableRow;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

/**
 * GetSocCasesReactor
 *
 * <p>This class takes sort, filter, pagination inputs and returns an array of cases based on those
 * parameters. User must be an SOC Management Lead.
 */
public class GetSocCasesReactor extends AbstractTQMCReactor {

  private static final String BASE_QUERY =
      "WITH\n"
          + "    StepStatusSummary AS (\n"
          + "SELECT\n"
          + "	sr.RECORD_ID,\n"
          + "	COUNT(\n"
          + "                CASE\n"
          + "                    WHEN scw.STEP_STATUS = 'completed' THEN 1\n"
          + "                END\n"
          + "            ) AS COMPLETED_COUNT,\n"
          + "	COUNT(\n"
          + "                CASE\n"
          + "                    WHEN scw.STEP_STATUS = 'unassigned' THEN 1\n"
          + "                END\n"
          + "            ) AS UNASSIGNED_COUNT,\n"
          + "	COUNT(DISTINCT s.CASE_ID) AS TOTAL_CASE_COUNT\n"
          + "FROM\n"
          + "	SOC_RECORD sr\n"
          + "INNER JOIN (\n"
          + "	SELECT\n"
          + "		sc.RECORD_ID,\n"
          + "		sc.CASE_ID\n"
          + "	FROM\n"
          + "		SOC_CASE sc\n"
          + "UNION\n"
          + "	SELECT\n"
          + "		snr.RECORD_ID,\n"
          + "		snr.NURSE_REVIEW_ID AS CASE_ID\n"
          + "	FROM\n"
          + "		SOC_NURSE_REVIEW snr\n"
          + "            ) s ON\n"
          + "	sr.RECORD_ID = s.RECORD_ID\n"
          + "INNER JOIN SOC_WORKFLOW scw ON\n"
          + "	s.CASE_ID = scw.CASE_ID\n"
          + "	AND scw.IS_LATEST = 1\n"
          + "GROUP BY\n"
          + "	sr.RECORD_ID\n"
          + "    )\n"
          + "SELECT\n"
          + "	COUNT(*) OVER () AS TOTAL_ROW_COUNT,\n"
          + "	*\n"
          + "FROM\n"
          + "	(\n"
          + "	SELECT\n"
          + "		sr.ALIAS_RECORD_ID,\n"
          + "		sr.PATIENT_FIRST_NAME,\n"
          + "		sr.PATIENT_LAST_NAME,\n"
          + "		CONCAT (sr.PATIENT_LAST_NAME,\n"
          + "		', ',\n"
          + "		sr.PATIENT_FIRST_NAME) AS PATIENT_NAME_STR,\n"
          + "		SOC_CASES.*,\n"
          + "		LOWER(CONCAT (sr.PATIENT_LAST_NAME,\n"
          + "		', ',\n"
          + "		sr.PATIENT_FIRST_NAME)) AS PATIENT_NAME,\n"
          + "		CASE\n"
          + "			WHEN ss.COMPLETED_COUNT = ss.TOTAL_CASE_COUNT THEN 'completed'\n"
          + "			WHEN ss.UNASSIGNED_COUNT > 0 THEN 'awaiting_assignment'\n"
          + "			ELSE 'assigned'\n"
          + "		END AS RECORD_STATUS_STR,\n"
          + "		CASE\n"
          + "			WHEN ss.COMPLETED_COUNT = ss.TOTAL_CASE_COUNT THEN 2\n"
          + "			WHEN ss.UNASSIGNED_COUNT > 0 THEN 0\n"
          + "			ELSE 1\n"
          + "		END AS RECORD_STATUS,\n"
          + "		CONCAT (\n"
          + "        sr.PATIENT_LAST_NAME,\n"
          + "		', ',\n"
          + "		sr.PATIENT_FIRST_NAME,\n"
          + "		' ',\n"
          + "		sr.PATIENT_LAST_NAME\n"
          + "    ) AS PATIENT_NAME_QUERY,\n"
          + "		CASE\n"
          + "			WHEN tu.USER_ID IS NULL\n"
          + "    		THEN NULL\n"
          + "			ELSE CONCAT (tu.LAST_NAME,\n"
          + "			', ',\n"
          + "			tu.FIRST_NAME)\n"
          + "		END AS USER_NAME_QUERY\n"
          + "	FROM\n"
          + "		(\n"
          + "		SELECT\n"
          + "			sc.RECORD_ID,\n"
          + "			sc.CASE_ID AS CASE_ID,\n"
          + "			NULL AS NURSE_REVIEW_ID,\n"
          + "			scw.CASE_TYPE,\n"
          + "			scw.STEP_STATUS AS CASE_STATUS,\n"
          + "			sc.SPECIALTY_ID,\n"
          + "			spec.SPECIALTY_NAME,\n"
          + "			CASE\n"
          + "				WHEN COUNT(DISTINCT srf.RECORD_FILE_ID) > 0 THEN TRUE\n"
          + "				ELSE FALSE\n"
          + "			END AS HAS_FILES,\n"
          + "			CASE\n"
          + "				WHEN scw.STEP_STATUS = 'completed'\n"
          + "        	THEN scw.SMSS_TIMESTAMP\n"
          + "			END AS COMPLETED_AT,\n"
          + "			sc.USER_ID,\n"
          + "			sc.ASSIGNED_AT AS ASSIGNED_DATE,\n"
          + "			sc.DUE_DATE_REVIEW,\n"
          + "			sc.DUE_DATE_DHA\n"
          + "		FROM\n"
          + "			SOC_CASE sc\n"
          + "		LEFT JOIN TQMC_SPECIALTY spec ON\n"
          + "			sc.SPECIALTY_ID = spec.SPECIALTY_ID\n"
          + "		INNER JOIN SOC_WORKFLOW scw ON\n"
          + "			sc.CASE_ID = scw.CASE_ID\n"
          + "			AND scw.IS_LATEST = 1\n"
          + "			AND scw.CASE_TYPE = 'peer_review'\n"
          + "		LEFT JOIN SOC_CASE_FILE scf ON\n"
          + "			sc.CASE_ID = scf.CASE_ID\n"
          + "		LEFT JOIN SOC_RECORD_FILE srf ON\n"
          + "			scf.RECORD_FILE_ID = srf.RECORD_FILE_ID\n"
          + "			AND srf.DELETED_AT IS NULL\n"
          + "		GROUP BY\n"
          + "			sc.RECORD_ID,\n"
          + "			sc.SPECIALTY_ID,\n"
          + "			sc.CASE_ID\n"
          + "	UNION ALL\n"
          + "		SELECT\n"
          + "			snr.RECORD_ID,\n"
          + "			NULL AS CASE_ID,\n"
          + "			snr.NURSE_REVIEW_ID AS NURSE_REVIEW_ID,\n"
          + "			scw.CASE_TYPE,\n"
          + "			scw.STEP_STATUS AS CASE_STATUS,\n"
          + "			NULL AS SPECIALTY_ID,\n"
          + "			NULL AS SPECIALTY_NAME,\n"
          + "			CASE\n"
          + "				WHEN COUNT(DISTINCT srf.RECORD_FILE_ID) > 0 THEN TRUE\n"
          + "				ELSE FALSE\n"
          + "			END AS HAS_FILES,\n"
          + "			CASE\n"
          + "				WHEN scw.STEP_STATUS = 'completed'\n"
          + "    	THEN scw.SMSS_TIMESTAMP\n"
          + "			END AS COMPLETED_AT,\n"
          + "			snr.USER_ID,\n"
          + "			snr.ASSIGNED_AT AS ASSIGNED_DATE,\n"
          + "			snr.DUE_DATE_REVIEW,\n"
          + "			snr.DUE_DATE_DHA\n"
          + "		FROM\n"
          + "			SOC_NURSE_REVIEW snr\n"
          + "		INNER JOIN SOC_WORKFLOW scw ON\n"
          + "			snr.NURSE_REVIEW_ID = scw.CASE_ID\n"
          + "			AND scw.IS_LATEST = 1\n"
          + "			AND scw.CASE_TYPE = 'nurse_review'\n"
          + "		LEFT JOIN SOC_RECORD sr ON\n"
          + "			snr.RECORD_ID = sr.RECORD_ID\n"
          + "		LEFT JOIN SOC_RECORD_FILE srf ON\n"
          + "			sr.RECORD_ID = srf.RECORD_ID\n"
          + "			AND srf.DELETED_AT IS NULL\n"
          + "			AND srf.SHOW_NURSE_REVIEW\n"
          + "		GROUP BY\n"
          + "			snr.RECORD_ID,\n"
          + "			snr.NURSE_REVIEW_ID\n"
          + "    ) SOC_CASES\n"
          + "	INNER JOIN SOC_RECORD sr ON\n"
          + "		SOC_CASES.RECORD_ID = sr.RECORD_ID\n"
          + "	LEFT JOIN TQMC_USER tu ON\n"
          + "		SOC_CASES.USER_ID = tu.USER_ID\n"
          + "	LEFT JOIN StepStatusSummary ss ON\n"
          + "		sr.RECORD_ID = ss.RECORD_ID\n"
          + ")\n";

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  private static final List<String> VALID_SORT_FIELDS =
      Lists.newArrayList(
          "RECORD_STATUS",
          "ALIAS_RECORD_ID",
          "PATIENT_NAME",
          "SPECIALTY_NAME",
          "CASE_TYPE",
          "DUE_DATE_DHA",
          "DUE_DATE_REVIEW",
          "ASSIGNED_DATE",
          "COMPLETED_AT",
          "HAS_FILES",
          "CASE_STATUS",
          "USER_NAME_QUERY");

  private static final List<String> VALID_FILTER_FIELDS =
      Lists.newArrayList(
          "RECORD_ID",
          "CASE_ID",
          "SPECIALTY_NAME",
          "RECORD_STATUS_STR",
          "CASE_TYPE",
          "ASSIGNED_DATE",
          "CASE_STATUS",
          "PATIENT_NAME_QUERY",
          "ALIAS_RECORD_ID",
          "USER_ID",
          "DUE_DATE_DHA",
          "DUE_DATE_REVIEW",
          "COMPLETED_AT");

  private static final List<String> DEFAULT_ORDER_FIELDS =
      Lists.newArrayList("ALIAS_RECORD_ID ASC", "CASE_ID ASC", "CASE_TYPE ASC");

  /** Constructor, takes in arguments. None of the arguments are mandatory. */
  public GetSocCasesReactor() {
    this.keysToGet = TQMCConstants.LIST_REACTOR_ARGUMENTS;
    this.keyRequired = new int[] {0, 0, 0};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    /** Throws error if correct permissions are not met. */
    if (!hasProductManagementPermission(TQMCConstants.SOC)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    /** Gets input arguments using helper function and validates inputs. */
    GetListPayload payload =
        TQMCHelper.getListPayloadObject(
            this.getNounStore(), VALID_SORT_FIELDS, VALID_FILTER_FIELDS);

    /** Forms the final query using all of the information above. */
    List<String> arguments = new ArrayList<>();
    String query =
        TQMCHelper.getListQuery(
            userId, payload, BASE_QUERY, arguments, new ArrayList<>(DEFAULT_ORDER_FIELDS));

    /** Setting up data structures. Using LinkedHashMap to preserve insertion order */
    Map<String, Object> output = new HashMap<>();
    List<SocManagementCaseTableRow> records = new ArrayList<>();

    /**
     * Try connection to the database, set all arguments, and execute query. If successful, then
     * iterating over the result set, assign values per column and insert information into data
     * structures as necessary.
     */
    try (PreparedStatement ps = con.prepareStatement(query)) {

      for (int i = 1; i < arguments.size(); i++) {
        ps.setString(i, arguments.get(i));
      }

      int trc = 0; // Total Row Count

      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          String aliasRecordId = rs.getString("ALIAS_RECORD_ID");
          String patientName = rs.getString("PATIENT_NAME_STR");
          String recordId = rs.getString("RECORD_ID");
          String caseId = rs.getString("CASE_ID");
          String nurseReviewId = rs.getString("NURSE_REVIEW_ID");
          String caseType = rs.getString("CASE_TYPE");
          String caseStatus = rs.getString("CASE_STATUS");
          String specialtyId = rs.getString("SPECIALTY_ID");
          String specialtyName = rs.getString("SPECIALTY_NAME");
          Boolean caseHasFiles = rs.getBoolean("HAS_FILES");
          String completedAtDate =
              ConversionUtils.getLocalDateStringFromDate(rs.getDate("COMPLETED_AT"));
          String userId = rs.getString("USER_ID");
          String assignedDate =
              ConversionUtils.getLocalDateStringFromDate(rs.getDate("ASSIGNED_DATE"));
          String dueDateReview =
              ConversionUtils.getLocalDateStringFromDate(rs.getDate("DUE_DATE_REVIEW"));
          String dueDateDHA =
              ConversionUtils.getLocalDateStringFromDate(rs.getDate("DUE_DATE_DHA"));
          String recordStatus = rs.getString("RECORD_STATUS_STR");
          int totalRowCount = rs.getInt("TOTAL_ROW_COUNT");

          records.add(
              new SocManagementCaseTableRow(
                  aliasRecordId,
                  patientName,
                  recordId,
                  caseId,
                  nurseReviewId,
                  SocCaseType.valueOf(caseType.toUpperCase()),
                  CaseStatus.valueOf(caseStatus.toUpperCase()),
                  specialtyId,
                  specialtyName,
                  caseHasFiles,
                  completedAtDate,
                  userId,
                  assignedDate,
                  dueDateReview,
                  dueDateDHA,
                  recordStatus));

          trc = totalRowCount;
        }

        /** Reorganize and prepare data for output. */
        List<Map<String, Object>> rows =
            records.parallelStream().map(record -> record.toMap()).collect(Collectors.toList());

        output.put("cases", rows);
        output.put("total_row_count", trc);
      }
    }

    return new NounMetadata(output, PixelDataType.MAP);
  }
}
