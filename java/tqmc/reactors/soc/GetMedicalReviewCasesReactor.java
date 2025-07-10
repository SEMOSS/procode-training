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
import tqmc.domain.base.GetListPayload;
import tqmc.domain.soc.MedicalReviewCaseTableRow;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetMedicalReviewCasesReactor extends AbstractTQMCReactor {

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  public GetMedicalReviewCasesReactor() {
    this.keysToGet = TQMCConstants.LIST_REACTOR_ARGUMENTS;
    this.keyRequired = new int[] {0, 0, 0};
  }

  private static final List<String> VALID_SORT_FIELDS =
      Lists.newArrayList(
          "PRODUCT",
          "PATIENT_NAME",
          "DUE_DATE_REVIEW",
          "CASE_STATUS",
          "SPECIALTY_NAME",
          "ALIAS_RECORD_ID",
          "IS_REOPENED",
          "COMPLETED_AT");

  private static final List<String> VALID_FILTER_FIELDS =
      Lists.newArrayList(
          "PRODUCT",
          "RECORD_ID",
          "SPECIALTY_NAME",
          "ASSIGNED_DATE",
          "CASE_STATUS",
          "PATIENT_NAME_QUERY",
          "ALIAS_RECORD_ID",
          "IS_REOPENED",
          "DUE_DATE_REVIEW",
          "SPECIALTY_ID");

  private static final List<String> DEFAULT_ORDER_FIELDS =
      Lists.newArrayList("ALIAS_RECORD_ID ASC", "CASE_ID ASC");

  private static final String BASE_QUERY =
      "select count(distinct case_id) over() AS total_row_count, * from\n"
          + //
          "(select * from \n"
          + //
          "(select mc.case_id,\n"
          + //
          " user_id, 'mn' as product, \n"
          + //
          "due_date_review,\n"
          + //
          "concat(mr.patient_last_name, ', ', mr.patient_first_name) as patient_name, mr.alias_record_id as alias_record_id,\n"
          + //
          "mw.step_status as case_status,\n"
          + //
          "concat(mr.patient_last_name, ', ', mr.patient_first_name, ' ', mr.patient_last_name) as patient_name_query,\n"
          + //
          "case when reopening_reason is not null then true else false end as  is_reopened,\n"
          + //
          "mc.SPECIALTY_ID,\n"
          + //
          "ts.specialty_name,\n"
          + //
          "mw.smss_timestamp as completed_at\n"
          + //
          "from mn_case as mc\n"
          + //
          "inner join mn_record as mr on mc.record_id = mr.record_id\n"
          + //
          "inner join mn_workflow mw on mw.case_id = mc.case_id and mw.is_latest\n"
          + //
          "inner join tqmc_specialty ts on ts.specialty_id = mc.specialty_id\n"
          + //
          "union \n"
          + //
          "select sc.case_id, user_id, 'soc' as product, \n"
          + //
          "due_date_review,\n"
          + //
          "concat(sr.patient_last_name, ', ', sr.patient_first_name) as patient_name,\n"
          + //
          "sr.alias_record_id as alias_record_id,\n"
          + //
          "sw.step_status as case_status,\n"
          + //
          "concat(sr.patient_last_name, ', ', sr.patient_first_name, ' ', sr.patient_last_name) as patient_name_query,\n"
          + //
          "case when reopening_reason is not null then true else false end as  is_reopened,\n"
          + //
          "sc.SPECIALTY_ID,\n"
          + //
          "ts.specialty_name,\n"
          + " sw.smss_timestamp as completed_at\n"
          + //
          " from soc_case as sc\n"
          + //
          "inner join soc_record as sr on sc.record_id = sr.record_id\n"
          + //
          "inner join soc_workflow sw on sw.case_id = sc.case_id and sw.is_latest and sw.case_type='peer_review'\n"
          + //
          "inner join tqmc_specialty ts on ts.specialty_id = sc.specialty_id\n"
          + //
          ") \n"
          + //
          "\n"
          + //
          "where user_id = ?)";

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    Map<String, Object> output = new HashMap<>();
    List<MedicalReviewCaseTableRow> cases = new ArrayList<MedicalReviewCaseTableRow>();

    GetListPayload payload =
        TQMCHelper.getListPayloadObject(
            this.getNounStore(), VALID_SORT_FIELDS, VALID_FILTER_FIELDS);

    List<String> arguments = new ArrayList<>();

    String query =
        TQMCHelper.getListQuery(
            userId, payload, BASE_QUERY, arguments, new ArrayList<>(DEFAULT_ORDER_FIELDS));

    try (PreparedStatement ps = con.prepareStatement(query)) {
      for (int i = 0; i < arguments.size(); i++) {
        ps.setString(i + 1, arguments.get(i));
      }

      int trc = 0; // Total Row Count

      ResultSet rs = ps.executeQuery();

      while (rs.next()) {

        MedicalReviewCaseTableRow row = new MedicalReviewCaseTableRow();
        row.setProduct(rs.getString("product"));
        row.setCaseId(rs.getString("case_id"));
        row.setPatientName(rs.getString("patient_name"));
        row.setAliasRecordId(rs.getString("alias_record_id"));
        row.setDueDateReview(ConversionUtils.getLocalDateFromDate(rs.getDate("due_date_review")));
        row.setIsReopened(rs.getBoolean("is_reopened"));
        row.setCaseStatus(rs.getString("case_status"));
        row.setSpecialtyId(rs.getString("specialty_id"));
        row.setCompletedAt(
            ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("completed_at")));
        cases.add(row);

        trc = rs.getInt("TOTAL_ROW_COUNT");
      }

      output.put(
          "cases",
          cases.parallelStream().map(record -> record.toMap()).collect(Collectors.toList()));
      output.put("total_row_count", trc);
    }

    return new NounMetadata(output, PixelDataType.MAP);
  }
}
