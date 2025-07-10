package tqmc.reactors.mn;

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
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.ProductTables;
import tqmc.domain.base.RecordFile;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetMnRecordReactor extends AbstractTQMCReactor {

  private static final String mnRecordQuery =
      "SELECT mc.RECORD_ID, mc.CASE_ID, mc.APPEAL_TYPE_ID, "
          + "mw.STEP_STATUS, "
          + "mc.SPECIALTY_ID, "
          + "mr.CARE_CONTRACTOR_ID, mr.FACILITY_NAME, mc.DUE_DATE_REVIEW, mc.DUE_DATE_DHA, "
          + "mr.UPDATED_AT, "
          + "mr.ALIAS_RECORD_ID, "
          + "mr.PATIENT_LAST_NAME, "
          + "mr.PATIENT_FIRST_NAME, "
          + "FROM MN_CASE mc "
          + "INNER JOIN MN_WORKFLOW mw "
          + "ON mw.CASE_ID = mc.CASE_ID AND mw.IS_LATEST = 1 "
          + "INNER JOIN MN_RECORD mr "
          + "ON mr.RECORD_ID = mc.RECORD_ID AND mc.SUBMISSION_GUID IS NULL "
          + "WHERE mc.RECORD_ID = ? AND mc.DELETED_AT IS NULL ";

  private static final String mnQuestionQuery =
      "SELECT mcq.CASE_QUESTION_ID, mcq.CASE_QUESTION "
          + "FROM MN_CASE mc "
          + "INNER JOIN MN_CASE_QUESTION mcq ON mcq.CASE_ID = mc.CASE_ID AND mc.SUBMISSION_GUID IS NULL "
          + "WHERE mc.RECORD_ID = ? AND mc.DELETED_AT IS NULL AND mcq.DELETED_AT IS NULL "
          + "ORDER BY mcq.QUESTION_NUMBER ASC";

  public GetMnRecordReactor() {
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

    if (!(hasProductPermission(TQMCConstants.MN)
        && ((hasRole(TQMCConstants.MANAGEMENT_LEAD) || hasRole(TQMCConstants.CONTRACTING_LEAD))
            || TQMCHelper.hasRecordAccess(con, userId, queriedRecord, ProductTables.MN)))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Map<String, Object> output = new HashMap<>();

    try (PreparedStatement recordStatement = con.prepareStatement(mnRecordQuery);
        PreparedStatement questionStatement = con.prepareStatement(mnQuestionQuery)) {
      recordStatement.setString(1, queriedRecord);
      questionStatement.setString(1, queriedRecord);
      if (recordStatement.execute()) {
        ResultSet recordResults = recordStatement.getResultSet();

        // add query results to map
        while (recordResults.next()) {
          output.put("record_id", recordResults.getString("RECORD_ID"));
          output.put("appeal_type_id", recordResults.getString("APPEAL_TYPE_ID"));
          output.put("case_status", recordResults.getString("STEP_STATUS"));
          output.put("specialty_id", recordResults.getString("SPECIALTY_ID"));
          output.put("care_contractor_id", recordResults.getString("CARE_CONTRACTOR_ID"));
          output.put("facility_name", recordResults.getString("FACILITY_NAME"));
          output.put(
              "due_date_review",
              ConversionUtils.getLocalDateStringFromDate(recordResults.getDate("DUE_DATE_REVIEW")));
          output.put(
              "due_date_dha",
              ConversionUtils.getLocalDateStringFromDate(recordResults.getDate("DUE_DATE_DHA")));
          output.put(
              "updated_at",
              ConversionUtils.getLocalDateTimeStringFromTimestamp(
                  recordResults.getTimestamp("UPDATED_AT")));
          output.put("alias_record_id", recordResults.getString("ALIAS_RECORD_ID"));
          output.put("patient_last_name", recordResults.getString("PATIENT_LAST_NAME"));
          output.put("patient_first_name", recordResults.getString("PATIENT_FIRST_NAME"));

          List<HashMap<String, String>> questions = new ArrayList<HashMap<String, String>>();
          if (questionStatement.execute()) {
            ResultSet questionResults = questionStatement.getResultSet();

            // add questions to list
            while (questionResults.next()) {
              HashMap<String, String> entry = new HashMap<String, String>();

              entry.put("case_question_id", questionResults.getString("CASE_QUESTION_ID"));
              entry.put("question", questionResults.getString("CASE_QUESTION"));

              questions.add(entry);
            }
          }
          output.put("questions", questions);
        }
      }
    }

    // check that case being queried exists and the user has permission
    if (output.isEmpty()) {
      throw new TQMCException(ErrorCode.NOT_FOUND, "Case is not found");
    }

    List<RecordFile> recordFiles = TQMCHelper.getRecordFiles(con, ProductTables.MN, queriedRecord);
    output.put(
        "files", recordFiles.parallelStream().map(e -> e.toMap()).collect(Collectors.toList()));

    return new NounMetadata(output, PixelDataType.MAP);
  }
}
