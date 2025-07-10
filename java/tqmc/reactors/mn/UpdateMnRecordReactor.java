package tqmc.reactors.mn;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.ProductTables;
import tqmc.domain.base.TQMCException;
import tqmc.domain.mapper.CustomMapper;
import tqmc.domain.mn.MnCaseQuestion;
import tqmc.domain.mn.MnRecord;
import tqmc.domain.mn.MnWorkflow;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class UpdateMnRecordReactor extends AbstractTQMCReactor {

  private static final String MN_RECORD = "mn_record";

  private final LocalDateTime localTime = ConversionUtils.getUTCFromLocalNow();

  public UpdateMnRecordReactor() {
    this.keysToGet = new String[] {MN_RECORD};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    if (!hasProductManagementPermission(TQMCConstants.MN)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);

    MnRecord mnRecord = payload.getMnRecord();

    if (mnRecord.getRecordId() == null
        || mnRecord.getCareContractorId() == null
        || mnRecord.getFacilityName() == null
        || mnRecord.getDueDateReview() == null
        || mnRecord.getDueDateDHA() == null
        || mnRecord.getSpecialtyId() == null
        || mnRecord.getAppealTypeId() == null
        || mnRecord.getQuestions() == null
        || mnRecord.getQuestions().size() == 0
        || mnRecord.getUpdatedAt() == null
        || mnRecord.getAliasRecordId() == null
        || mnRecord.getPatientLastName() == null
        || mnRecord.getPatientFirstName() == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid request: missing required fields");
    }

    String caseId = TQMCHelper.getMnCaseIdForRecord(con, mnRecord.getRecordId());
    if (caseId == null) {
      throw new TQMCException(ErrorCode.NOT_FOUND);
    }

    Map<String, Object> appealInfo = TQMCHelper.getMnAppealInfo(con, mnRecord.getAppealTypeId());
    if (appealInfo.isEmpty()) {
      throw new TQMCException(ErrorCode.NOT_FOUND, "Invalid request: appeal type not found");
    }

    LocalDateTime recordUpdatedTime = null;
    LocalDateTime caseUpdatedTime = null;
    try (PreparedStatement ps =
        con.prepareStatement(
            "Select r.updated_at AS RECORD_UPDATED_AT, c.updated_at AS CASE_UPDATED_AT FROM ("
                + "select updated_at, record_id FROM mn_record where record_id = ?) as r "
                + "LEFT OUTER JOIN mn_case c "
                + "ON c.record_id= r.record_id and c.SUBMISSION_GUID IS NULL ")) {
      ps.setString(1, mnRecord.getRecordId());
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          recordUpdatedTime =
              ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("RECORD_UPDATED_AT"));
          caseUpdatedTime =
              ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp("CASE_UPDATED_AT"));
        }
      }
    }

    if (recordUpdatedTime == null || !recordUpdatedTime.isEqual(mnRecord.getUpdatedAt())) {
      throw new TQMCException(ErrorCode.CONFLICT);
    }

    Map<String, String> fieldsToUpdate = new HashMap<>();

    Set<String> validSpecialties = TQMCHelper.getValidSpecialtyIds(con);
    if (!validSpecialties.contains(mnRecord.getSpecialtyId())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid request: invalid specialty");
    }

    Map<String, Map<String, String>> ccq = new HashMap<>();

    // populate ccq map with all pre-existing case questions
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT case_question, case_question_id FROM MN_CASE_QUESTION WHERE case_id = ? AND deleted_at IS NULL ORDER BY QUESTION_NUMBER ASC")) {
      ps.setString(1, caseId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          Map<String, String> questionMap = new HashMap<>();
          questionMap.put("case_question", rs.getString("case_question"));
          questionMap.put("inputted", "no");
          ccq.put(rs.getString("case_question_id"), questionMap);
        }
      }
    }

    MnWorkflow wf = TQMCHelper.getLatestMnWorkflow(con, caseId);
    boolean isUnassigned = TQMCConstants.CASE_STEP_STATUS_UNASSIGNED.equals(wf.getStepStatus());

    if (isUnassigned) {
      if (!recordUpdatedTime.isEqual(caseUpdatedTime)) {
        throw new TQMCException(ErrorCode.CONFLICT, "Record and case time should match");
      }
    } else {
      if (!mnRecord.getAppealTypeId().equals(wf.getAppealTypeId())) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "Invalid request: cannot change appeal type after assignment");
      }
      if (!mnRecord.getSpecialtyId().equals(wf.getSpecialtyId())) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "Invalid request: cannot change specialty after assignment");
      }
    }

    List<MnCaseQuestion> oldQuestions = new ArrayList<>();
    List<MnCaseQuestion> newQuestions = new ArrayList<>();
    Set<String> questionIds = new HashSet<>();
    Set<String> questions = new HashSet<>();
    int questionNumber = 1;
    for (MnCaseQuestion q : mnRecord.getQuestions()) {
      if (q.getQuestion() == null) {
        throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid request: question(s) required");
      }

      boolean hasCaseQuestionId = q.getCaseQuestionId() != null;

      if (!isUnassigned && !hasCaseQuestionId) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "Invalid request: question id required when case is assigned");
      }

      if (questionIds.contains(q.getCaseQuestionId()) || questions.contains(q.getQuestion())) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "Invalid request: duplicate questions or question_ids");
      }

      q.setNumber(questionNumber++);

      if (hasCaseQuestionId) {
        questionIds.add(q.getCaseQuestionId());
        oldQuestions.add(q);
      } else {
        newQuestions.add(q);
      }

      questions.add(q.getQuestion());
    }

    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT care_contractor_name FROM MN_CARE_CONTRACTOR WHERE care_contractor_id = ?")) {
      ps.setString(1, mnRecord.getCareContractorId());
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          fieldsToUpdate.put("care_contractor_name", rs.getString("care_contractor_name"));
        }
      }
    }

    if (!fieldsToUpdate.containsKey("care_contractor_name")) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid request: invalid care contractor");
    }

    if (!oldQuestions.isEmpty()) {
      try (PreparedStatement ps =
          con.prepareStatement(
              "UPDATE MN_CASE_QUESTION SET case_question = ?, question_number = ? WHERE case_question_id = ?")) {
        for (MnCaseQuestion q : oldQuestions) {
          String qId = q.getCaseQuestionId();
          if (ccq.containsKey(qId)) {
            int parameterIndex = 1;
            ps.setString(parameterIndex++, q.getQuestion());
            ps.setInt(parameterIndex++, q.getNumber());
            ps.setString(parameterIndex++, q.getCaseQuestionId());
            ps.addBatch();
            ccq.get(q.getCaseQuestionId()).put("inputted", "yes");
          } else {
            throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid request: question not found");
          }
        }
        ps.executeBatch();
      }
    }

    if (!newQuestions.isEmpty()) {
      try (PreparedStatement ps =
          con.prepareStatement(
              "INSERT INTO MN_CASE_QUESTION (case_id, case_question, case_question_id, question_number) VALUES (?, ?, ?, ?)")) {
        for (MnCaseQuestion q : newQuestions) {
          int parameterIndex = 1;
          ps.setString(parameterIndex++, caseId);
          ps.setString(parameterIndex++, q.getQuestion());
          ps.setString(parameterIndex++, UUID.randomUUID().toString());
          ps.setInt(parameterIndex++, q.getNumber());
          ps.addBatch();
        }
        ps.executeBatch();
      }
    }

    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE MN_CASE_QUESTION SET deleted_at = ? WHERE case_question_id = ?")) {
      for (String cq : ccq.keySet()) {
        if (ccq.get(cq).get("inputted").equals("no")) {
          int parameterIndex = 1;
          ps.setObject(parameterIndex++, localTime);
          ps.setString(parameterIndex++, cq);
          ps.addBatch();
        }
      }
      ps.executeBatch();
    }

    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE MN_RECORD SET care_contractor_id = ?, care_contractor_name = ?, facility_name = ?, updated_at = ?, alias_record_id = ? , patient_last_name = ?, patient_first_name = ? WHERE record_id = ?")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, mnRecord.getCareContractorId());
      ps.setString(parameterIndex++, fieldsToUpdate.get("care_contractor_name"));
      ps.setString(parameterIndex++, mnRecord.getFacilityName());
      ps.setObject(parameterIndex++, localTime);
      ps.setString(parameterIndex++, mnRecord.getAliasRecordId());
      ps.setString(parameterIndex++, mnRecord.getPatientLastName());
      ps.setString(parameterIndex++, mnRecord.getPatientFirstName());
      ps.setString(parameterIndex++, mnRecord.getRecordId());
      ps.execute();
    }

    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE MN_CASE SET appeal_type_id = ?, specialty_id = ?, updated_at = ?, due_date_review = ?, due_date_dha = ? WHERE case_id = ?")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, mnRecord.getAppealTypeId());
      ps.setString(parameterIndex++, mnRecord.getSpecialtyId());
      ps.setObject(parameterIndex++, localTime);
      ps.setObject(parameterIndex++, mnRecord.getDueDateReview());
      ps.setObject(parameterIndex++, mnRecord.getDueDateDHA());
      ps.setString(parameterIndex++, caseId);
      ps.execute();
    }

    TQMCHelper.updateRecordFiles(
        con, insight, projectId, ProductTables.MN, mnRecord.getRecordId(), mnRecord.getFiles());

    return new NounMetadata(
        CustomMapper.MAPPER.convertValue(mnRecord, new TypeReference<Map<String, Object>>() {}),
        PixelDataType.MAP);
  }

  public static class Payload {

    @JsonProperty("mn_record")
    private MnRecord mnRecord;

    public MnRecord getMnRecord() {
      return this.mnRecord;
    }

    public void setMnRecord(MnRecord mnRecord) {
      this.mnRecord = mnRecord;
    }
  }
}
