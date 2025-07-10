package tqmc.reactors.mn;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
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

public class CreateMnRecordReactor extends AbstractTQMCReactor {

  private static final String MN_RECORD = "mn_record";

  private final LocalDateTime localTime = ConversionUtils.getUTCFromLocalNow();

  public CreateMnRecordReactor() {
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

    if (mnRecord.getCareContractorId() == null
        || mnRecord.getFacilityName() == null
        || mnRecord.getDueDateReview() == null
        || mnRecord.getDueDateDHA() == null
        || mnRecord.getSpecialtyId() == null
        || mnRecord.getAppealTypeId() == null
        || mnRecord.getQuestions() == null
        || mnRecord.getQuestions().size() == 0
        || mnRecord.getAliasRecordId() == null
        || mnRecord.getPatientLastName() == null
        || mnRecord.getPatientFirstName() == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid request: missing required fields");
    }

    if (mnRecord.getRecordId() != null) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST, "Invalid request: record_id should not be passed in");
    }

    Map<String, Object> appealInfo = TQMCHelper.getMnAppealInfo(con, mnRecord.getAppealTypeId());
    if (appealInfo.isEmpty()) {
      throw new TQMCException(ErrorCode.NOT_FOUND, "Invalid request: appeal type not found");
    }

    for (MnCaseQuestion q : mnRecord.getQuestions()) {
      if (q.getCaseQuestionId() != null) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "Invalid request: case_question_ids should not be passed in");
      }
      if (q.getQuestion() == null) {
        throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid request: missing question");
      }
    }

    Map<String, String> fieldsToUpdate = new HashMap<>();
    Set<String> validSpecialties = TQMCHelper.getValidSpecialtyIds(con);
    if (!validSpecialties.contains(mnRecord.getSpecialtyId())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid request: invalid specialty");
    }

    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT care_contractor_name FROM MN_CARE_CONTRACTOR WHERE care_contractor_id = ?")) {
      ps.setString(1, mnRecord.getCareContractorId());
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          fieldsToUpdate.put("care_contractor_name", rs.getString(1));
        }
      }
    }

    if (!fieldsToUpdate.containsKey("care_contractor_name")) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid request: care contractor not found");
    }
    int mnPadLength = 5;

    mnRecord.setRecordId(
        TQMCHelper.getDisplayId(
            TQMCConstants.MN + "-" + TQMCConstants.REC,
            TQMCHelper.getNextId(con, TQMCConstants.TABLE_MN_RECORD),
            mnPadLength));
    mnRecord.setUpdatedAt(localTime);
    mnRecord.setCreatedAt(localTime);

    MnWorkflow wf = new MnWorkflow();
    wf.setCaseId(
        TQMCHelper.getDisplayId(
            TQMCConstants.MN + "-" + TQMCConstants.CASE,
            TQMCHelper.getNextId(con, TQMCConstants.TABLE_MN_CASE),
            mnPadLength));
    wf.setGuid(UUID.randomUUID().toString());
    wf.setIsLatest(true);
    wf.setRecipientUserId(TQMCConstants.DEFAULT_SYSTEM_USER);
    wf.setSendingUserId(userId);
    wf.setStepStatus(TQMCConstants.CASE_STEP_STATUS_UNASSIGNED);
    wf.setSmssTimestamp(mnRecord.getUpdatedAt());

    try (PreparedStatement ps =
        con.prepareStatement(
            "INSERT INTO MN_RECORD (record_id, facility_name, care_contractor_id, care_contractor_name, created_at, updated_at, alias_record_id, patient_last_name, patient_first_name) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, mnRecord.getRecordId());
      ps.setString(parameterIndex++, mnRecord.getFacilityName());
      ps.setString(parameterIndex++, mnRecord.getCareContractorId());
      ps.setString(parameterIndex++, fieldsToUpdate.get("care_contractor_name"));
      ps.setObject(parameterIndex++, mnRecord.getCreatedAt());
      ps.setObject(parameterIndex++, mnRecord.getUpdatedAt());
      ps.setString(parameterIndex++, mnRecord.getAliasRecordId());
      ps.setString(parameterIndex++, mnRecord.getPatientLastName());
      ps.setString(parameterIndex++, mnRecord.getPatientFirstName());
      ps.execute();
    }

    try (PreparedStatement ps =
        con.prepareStatement(
            "INSERT INTO MN_CASE (appeal_type_id, case_id, created_at, record_id, specialty_id, updated_at, due_date_review, due_date_dha) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, mnRecord.getAppealTypeId());
      ps.setString(parameterIndex++, wf.getCaseId());
      ps.setObject(parameterIndex++, mnRecord.getCreatedAt());
      ps.setString(parameterIndex++, mnRecord.getRecordId());
      ps.setString(parameterIndex++, mnRecord.getSpecialtyId());
      ps.setObject(parameterIndex++, mnRecord.getUpdatedAt());
      ps.setObject(parameterIndex++, mnRecord.getDueDateReview());
      ps.setObject(parameterIndex++, mnRecord.getDueDateDHA());
      ps.execute();
    }

    try (PreparedStatement ps =
        con.prepareStatement(
            "INSERT INTO MN_CASE_QUESTION (case_id, case_question, case_question_id, question_number) "
                + "VALUES (?, ?, ?, ?)")) {
      int questionNumber = 1;
      for (MnCaseQuestion q : mnRecord.getQuestions()) {
        int parameterIndex = 1;
        ps.setString(parameterIndex++, wf.getCaseId());
        ps.setString(parameterIndex++, q.getQuestion());
        ps.setString(parameterIndex++, UUID.randomUUID().toString());
        ps.setInt(parameterIndex++, questionNumber++);
        ps.addBatch();
      }
      ps.executeBatch();
    }

    TQMCHelper.updateMnWorkflow(con, wf, true);

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
