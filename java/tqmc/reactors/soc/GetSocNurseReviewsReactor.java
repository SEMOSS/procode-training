package tqmc.reactors.soc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.GetListPayload;
import tqmc.domain.base.TQMCException;
import tqmc.domain.soc.SocNurseReviewTableRow;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetSocNurseReviewsReactor extends AbstractTQMCReactor {

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  /** Constructor, takes in arguments. None of the arguments are mandatory. */
  public GetSocNurseReviewsReactor() {
    this.keysToGet = TQMCConstants.LIST_REACTOR_ARGUMENTS;
    this.keyRequired = new int[] {0, 0, 0};
  }

  private static final List<String> VALID_SORT_FIELDS =
      Arrays.asList(
          "NURSE_REVIEW_ID",
          "ALIAS_RECORD_ID",
          "PATIENT_NAME",
          "DUE_DATE_REVIEW",
          "IS_REOPENED",
          "CASE_STATUS",
          "COMPLETED_AT");

  private static final List<String> VALID_FILTER_FIELDS =
      Arrays.asList(
          "NURSE_REVIEW_ID",
          "ALIAS_RECORD_ID",
          "PATIENT_NAME_QUERY",
          "DMIS_ID",
          "DUE_DATE_REVIEW",
          "IS_REOPENED",
          "CASE_STATUS",
          "COMPLETED_AT");

  private static final List<String> DEFAULT_ORDER_FIELDS =
      Arrays.asList("ALIAS_RECORD_ID ASC", "NURSE_REVIEW_ID ASC", "CASE_STATUS ASC");

  private static final String BASE_QUERY =
      "WITH dmis_lists AS (\r\n"
          + "SELECT\r\n"
          + "	snr.nurse_review_id,\r\n"
          + "	STRING_AGG(srf.DMIS_ID::text, ',' ORDER BY srf.DMIS_ID) AS dmis_id_list\r\n"
          + "FROM\r\n"
          + "	soc_nurse_review snr\r\n"
          + "LEFT JOIN SOC_RECORD_MTF srf ON\r\n"
          + "	srf.RECORD_ID = snr.RECORD_ID\r\n"
          + "GROUP BY\r\n"
          + "	snr.nurse_review_id\r\n"
          + ")\r\n"
          + "SELECT\r\n"
          + "	COUNT(DISTINCT nurse_review_id) OVER() AS TOTAL_ROW_COUNT,\r\n"
          + "	*,\r\n"
          + "	dmis_id_list\r\n"
          + "FROM\r\n"
          + "	(\r\n"
          + "	SELECT\r\n"
          + "		snr.nurse_review_id,\r\n"
          + "		sr.ALIAS_RECORD_ID,\r\n"
          + "		CONCAT(sr.PATIENT_LAST_NAME, ', ', sr.PATIENT_FIRST_NAME) AS PATIENT_NAME,\r\n"
          + "		snr.DUE_DATE_REVIEW,\r\n"
          + "		CASE\r\n"
          + "			WHEN snr.REOPENING_REASON IS NOT NULL THEN TRUE\r\n"
          + "			ELSE FALSE\r\n"
          + "		END AS IS_REOPENED,\r\n"
          + "		sw.STEP_STATUS AS CASE_STATUS,\r\n"
          + "		srf.DMIS_ID AS DMIS_ID,\r\n"
          + "		CONCAT(sr.PATIENT_LAST_NAME, ', ', sr.PATIENT_FIRST_NAME, ' ', sr.PATIENT_LAST_NAME) AS PATIENT_NAME_QUERY,\r\n"
          + "		CASE\r\n"
          + "			WHEN sw.STEP_STATUS = 'completed' THEN sw.SMSS_TIMESTAMP\r\n"
          + "		END AS completed_at,\r\n"
          + "		dmis_lists.dmis_id_list\r\n"
          + "	FROM\r\n"
          + "		soc_nurse_review snr\r\n"
          + "	INNER JOIN SOC_RECORD sr ON\r\n"
          + "		sr.RECORD_ID = snr.RECORD_ID\r\n"
          + "	INNER JOIN SOC_WORKFLOW sw ON\r\n"
          + "		sw.CASE_ID = snr.NURSE_REVIEW_ID\r\n"
          + "		AND sw.CASE_TYPE = 'nurse_review'\r\n"
          + "		AND sw.IS_LATEST\r\n"
          + "	LEFT JOIN SOC_RECORD_MTF srf ON\r\n"
          + "		srf.RECORD_ID = snr.RECORD_ID\r\n"
          + "	LEFT JOIN dmis_lists ON\r\n"
          + "		dmis_lists.nurse_review_id = snr.nurse_review_id\r\n"
          + "	WHERE\r\n"
          + "		snr.user_id = ?\r\n"
          + "    )";

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    // Permission check
    if (!((hasProductPermission(TQMCConstants.SOC) && hasRole(TQMCConstants.NURSE_REVIEWER))
        || hasProductManagementPermission(TQMCConstants.SOC))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    GetListPayload payload =
        TQMCHelper.getListPayloadObject(
            this.getNounStore(), VALID_SORT_FIELDS, VALID_FILTER_FIELDS);

    List<String> arguments = new ArrayList<>();
    String query =
        TQMCHelper.getListQuery(
            userId, payload, BASE_QUERY, arguments, new ArrayList<>(DEFAULT_ORDER_FIELDS));

    Map<String, Object> output = new HashMap<>();
    Set<String> nrIds = new HashSet<>();
    List<SocNurseReviewTableRow> nurseReviews = new ArrayList<>();

    try (PreparedStatement ps = con.prepareStatement(query)) {
      for (int i = 0; i < arguments.size(); i++) {
        ps.setString(i + 1, arguments.get(i));
      }

      int trc = 0; // Total Row Count

      ResultSet rs = ps.executeQuery();

      while (rs.next()) {
        String nrId = rs.getString("nurse_review_id");
        if (!nrIds.contains(nrId)) {
          nrIds.add(nrId);
          SocNurseReviewTableRow row = new SocNurseReviewTableRow();
          row.setNurseReviewId(nrId);
          row.setAliasRecordId(rs.getString("alias_record_id"));
          row.setPatientName(rs.getString("patient_name"));
          row.setDmisIdList(Arrays.asList(rs.getString("dmis_id_list").split("\\s*,\\s*")));
          row.setDueDateReview(rs.getString("due_date_review"));
          row.setIsReopened(rs.getBoolean("is_reopened"));
          row.setCaseStatus(rs.getString("case_status"));
          row.setCompletedAt(rs.getString("completed_at"));
          nurseReviews.add(row);
        }
        trc = rs.getInt("TOTAL_ROW_COUNT");
      }

      output.put(
          "nurse_reviews",
          nurseReviews.parallelStream().map(record -> record.toMap()).collect(Collectors.toList()));
      output.put("total_row_count", trc);
    }

    return new NounMetadata(output, PixelDataType.MAP);
  }
}
