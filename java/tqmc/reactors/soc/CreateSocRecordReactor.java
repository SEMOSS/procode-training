package tqmc.reactors.soc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.ProductTables;
import tqmc.domain.base.Provider;
import tqmc.domain.base.RecordFile;
import tqmc.domain.base.TQMCException;
import tqmc.domain.soc.SocCaseType;
import tqmc.domain.soc.SocRecord;
import tqmc.domain.soc.SocRecord.SocSpecialtyIdMap;
import tqmc.domain.soc.SocRecordPayload;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

/**
 * This reactor creates SOC records and their associated cases given an input dictionary containing
 * the information about the records to instantiate
 */
public class CreateSocRecordReactor extends AbstractTQMCReactor {

  private final LocalDateTime curTime = ConversionUtils.getUTCFromLocalNow();

  /** The reactor takes a parameter record that is mandatory */
  public CreateSocRecordReactor() {
    this.keysToGet = new String[] {"record"};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    /** Throws error if correct permissions are not met. */
    if (!hasProductManagementPermission(TQMCConstants.SOC)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    SocRecordPayload payload =
        TQMCHelper.getPayloadObject(store, keysToGet, SocRecordPayload.class);

    if (payload.getRecord() == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Record is required to complete action");
    }

    SocRecord socRecord = payload.getRecord();

    if (socRecord.getProviders().isEmpty()) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Providers list is empty.");
    }

    if (socRecord.getNurseDueDateReview() == null || socRecord.getNurseDueDateDHA() == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Nurse review due dates required");
    }

    if (socRecord.getMtfList() != null && !socRecord.getMtfList().isEmpty()) {
      if (socRecord.getMtfList().parallelStream().anyMatch(e -> e == null)) {
        throw new TQMCException(
            ErrorCode.BAD_REQUEST, "One or more MTF list entries have null ID.");
      }
    }

    if (socRecord.getFiles() != null && !socRecord.getFiles().isEmpty()) {
      if (socRecord
          .getFiles()
          .parallelStream()
          .anyMatch(
              e ->
                  e.getShowNurseReview() == null
                      || (e.getShowNurseReview() == false && e.getSpecialtyIdList().isEmpty()))) {
        throw new TQMCException(ErrorCode.BAD_REQUEST, "One or more files have invalid parameters");
      }
    }

    Set<String> givenProviderSpecialties =
        socRecord
            .getProviders()
            .parallelStream()
            .map(e -> e.getSpecialtyId())
            .collect(Collectors.toSet());
    Set<String> givenFilesSpecialties =
        socRecord
            .getFiles()
            .parallelStream()
            .flatMap(f -> f.getSpecialtyIdList().parallelStream())
            .collect(Collectors.toSet());
    Set<String> givenDueDateMapSpecialties =
        socRecord
            .getDueDateMap()
            .values()
            .parallelStream()
            .map(sidMap -> sidMap.getSpecialtyId())
            .collect(Collectors.toSet());

    if (!socRecord.getFiles().parallelStream().anyMatch(f -> f.getShowNurseReview())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Each review must have at least one file");
    }

    if (!(givenProviderSpecialties.equals(givenFilesSpecialties)
        && givenProviderSpecialties.equals(givenDueDateMapSpecialties))) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Each review must have at least one file");
    }

    Set<String> providerSpecialties = new HashSet<>();
    Set<String> validSpecialties = TQMCHelper.getValidSpecialtyIds(con);
    for (Provider p : socRecord.getProviders()) {
      if (p.getProviderName() == null
          || p.getSpecialtyId() == null
          || !validSpecialties.contains(p.getSpecialtyId())) {
        throw new TQMCException(ErrorCode.BAD_REQUEST, "One or more providers have invalid data.");
      }
      providerSpecialties.add(p.getSpecialtyId());
    }

    Set<String> dueDateSpecialties = new HashSet<>();
    if (socRecord.getDueDateMap() != null && socRecord.getDueDateMap().size() > 0) {
      for (String specialty : socRecord.getDueDateMap().keySet()) {
        SocSpecialtyIdMap ssim = socRecord.getDueDateMap().get(specialty);
        if (ssim == null
            || ssim.getSpecialtyId() == null
            || ssim.getDueDateReview() == null
            || ssim.getDueDateDHA() == null) {
          throw new TQMCException(
              ErrorCode.BAD_REQUEST, "Review due date, DHA due date, or specialty id missing");
        }
        if (!validSpecialties.contains(ssim.getSpecialtyId())) {
          throw new TQMCException(
              ErrorCode.BAD_REQUEST, "Specialty/subspecialty combination not found");
        }
        dueDateSpecialties.add(ssim.getSpecialtyId());
      }
    }

    if (!dueDateSpecialties.equals(providerSpecialties)) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST, "Provider and due date specialties do not match");
    }

    String recordId =
        TQMCHelper.getDisplayId(
            TQMCConstants.SOC + "-" + TQMCConstants.REC,
            TQMCHelper.getNextId(con, TQMCConstants.TABLE_SOC_RECORD),
            TQMCConstants.PADDING_LENGTH);
    String aliasRecordId = socRecord.getAliasRecordId();
    String patientLastName = socRecord.getPatientLastName();
    String patientFirstName = socRecord.getPatientFirstName();

    List<String> mtfs = socRecord.getMtfList();

    Map<String, Map<String, String>> scqMap = new HashMap<>();
    Map<String, Map<String, String>> scpeqMap = new HashMap<>();

    for (Provider item : socRecord.getProviders()) {
      if (!scqMap.containsKey(item.getSpecialtyId())) {
        Map<String, String> internalScqMap = new HashMap<>();
        String caseId =
            TQMCHelper.getDisplayId(
                TQMCConstants.SOC + "-" + TQMCConstants.CASE,
                TQMCHelper.getNextId(con, TQMCConstants.TABLE_SOC_CASE),
                TQMCConstants.PADDING_LENGTH);
        internalScqMap.put("case_id", caseId);
        internalScqMap.put("specialty_id", item.getSpecialtyId());
        scqMap.put(item.getSpecialtyId(), internalScqMap);
      }
      Map<String, String> internalScpeqMap = new HashMap<>();
      String caseProviderEvaluationId = UUID.randomUUID().toString();
      internalScpeqMap.put("provider_name", item.getProviderName());
      internalScpeqMap.put("case_provider_evaluation_id", caseProviderEvaluationId);
      internalScpeqMap.put("case_id", scqMap.get(item.getSpecialtyId()).get("case_id"));

      scpeqMap.put(caseProviderEvaluationId, internalScpeqMap);
    }

    List<RecordFile> addCaseIds = socRecord.getFiles();
    addCaseIds
        .parallelStream()
        .forEach(
            file -> {
              file.getSpecialtyIdList()
                  .parallelStream()
                  .forEach(
                      sid -> {
                        String caseId = scqMap.get(sid).get("case_id");
                        file.addCaseId(caseId);
                      });
            });

    socRecord.setFiles(addCaseIds);

    String socRecordQuery =
        "INSERT INTO SOC_RECORD (RECORD_ID, CREATED_AT, UPDATED_AT, ALIAS_RECORD_ID, PATIENT_LAST_NAME, PATIENT_FIRST_NAME) VALUES (?,?,?,?,?,?);";
    String socMtfQuery =
        "INSERT INTO SOC_RECORD_MTF (RECORD_MTF_ID,RECORD_ID,DMIS_ID) VALUES (?,?,?)";
    String socCaseQuery =
        "INSERT INTO SOC_CASE (CASE_ID, CREATED_AT, UPDATED_AT, RECORD_ID, SPECIALTY_ID, DUE_DATE_REVIEW, DUE_DATE_DHA) VALUES (?,?,?,?,?,?,?)";
    String socNurseQuery =
        "INSERT INTO SOC_NURSE_REVIEW (NURSE_REVIEW_ID, RECORD_ID, CREATED_AT, UPDATED_AT, DUE_DATE_REVIEW, DUE_DATE_DHA) VALUES (?,?,?,?,?,?)";
    String socWorkflowQuery =
        "INSERT INTO SOC_WORKFLOW (CASE_ID, CASE_TYPE, GUID, IS_LATEST, RECIPIENT_USER_ID, SMSS_TIMESTAMP, STEP_STATUS) VALUES (?,?,?,?,?,?,?)";
    String socCaseProviderEvaluationQuery =
        "INSERT INTO SOC_CASE_PROVIDER_EVALUATION (CASE_PROVIDER_EVALUATION_ID, CASE_ID, PROVIDER_NAME, CREATED_AT, UPDATED_AT) VALUES (?,?,?,?,?)";
    // String socCaseSystemEvalQuery =
    //     "INSERT INTO SOC_CASE_SYSTEM_EVALUATION (CASE_SYSTEM_EVALUATION_ID, CASE_ID, CREATED_AT,
    // UPDATED_AT) VALUES (?,?,?,?)";

    try (PreparedStatement ps = con.prepareStatement(socRecordQuery)) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, recordId);
      ps.setObject(parameterIndex++, curTime);
      ps.setObject(parameterIndex++, curTime);
      ps.setString(parameterIndex++, aliasRecordId);
      ps.setString(parameterIndex++, patientLastName);
      ps.setString(parameterIndex++, patientFirstName);
      ps.execute();
    }
    if (mtfs != null && !mtfs.isEmpty()) {
      try (PreparedStatement ps = con.prepareStatement(socMtfQuery)) {
        for (String item : mtfs) {
          int parameterIndex = 1;
          String recordMtfId = UUID.randomUUID().toString();
          ps.setString(parameterIndex++, recordMtfId);
          ps.setString(parameterIndex++, recordId);
          ps.setString(parameterIndex++, item);
          ps.addBatch();
        }
        ps.executeBatch();
      }
    }

    try (PreparedStatement ps = con.prepareStatement(socCaseQuery)) {
      for (Map<String, String> item : scqMap.values()) {
        int parameterIndex = 1;
        ps.setString(parameterIndex++, item.get("case_id"));
        ps.setObject(parameterIndex++, curTime);
        ps.setObject(parameterIndex++, curTime);
        ps.setString(parameterIndex++, recordId);
        String specialtyId = item.get("specialty_id");
        ps.setString(parameterIndex++, specialtyId);
        SocSpecialtyIdMap ssid = socRecord.getDueDateMap().get(specialtyId);
        ps.setObject(parameterIndex++, ssid.getDueDateReview());
        ps.setObject(parameterIndex++, ssid.getDueDateDHA());
        ps.addBatch();
      }
      ps.executeBatch();
    }

    String nurseReviewId =
        TQMCHelper.getDisplayId(
            TQMCConstants.SOC + "-" + TQMCConstants.NR,
            TQMCHelper.getNextId(con, TQMCConstants.TABLE_SOC_NURSE_REVIEW),
            TQMCConstants.PADDING_LENGTH);

    try (PreparedStatement ps = con.prepareStatement(socNurseQuery)) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, nurseReviewId);
      ps.setString(parameterIndex++, recordId);
      ps.setObject(parameterIndex++, curTime);
      ps.setObject(parameterIndex++, curTime);
      ps.setObject(parameterIndex++, socRecord.getNurseDueDateReview());
      ps.setObject(parameterIndex++, socRecord.getNurseDueDateDHA());
      ps.execute();
    }

    try (PreparedStatement ps = con.prepareStatement(socWorkflowQuery)) {
      for (Map<String, String> item : scqMap.values()) {
        int parameterIndex = 1;
        ps.setString(parameterIndex++, item.get("case_id"));
        ps.setString(parameterIndex++, SocCaseType.PEER_REVIEW.getCaseType());
        String guid = UUID.randomUUID().toString();
        ps.setString(parameterIndex++, guid);
        ps.setBoolean(parameterIndex++, true);
        ps.setString(parameterIndex++, TQMCConstants.DEFAULT_SYSTEM_USER);
        ps.setObject(parameterIndex++, curTime);
        ps.setString(parameterIndex++, TQMCConstants.CASE_STEP_STATUS_UNASSIGNED);
        ps.addBatch();
      }
      int parameterIndex = 1;
      ps.setString(parameterIndex++, nurseReviewId);
      ps.setString(parameterIndex++, SocCaseType.NURSE_REVIEW.getCaseType());
      String guid = UUID.randomUUID().toString();
      ps.setString(parameterIndex++, guid);
      ps.setBoolean(parameterIndex++, true);
      ps.setString(parameterIndex++, TQMCConstants.DEFAULT_SYSTEM_USER);
      ps.setObject(parameterIndex++, curTime);
      ps.setString(parameterIndex++, TQMCConstants.CASE_STEP_STATUS_UNASSIGNED);
      ps.addBatch();

      ps.executeBatch();
    }

    try (PreparedStatement ps = con.prepareStatement(socCaseProviderEvaluationQuery);
    // PreparedStatement ps2 = con.prepareStatement(socCaseSystemEvalQuery)
    ) {
      for (Map<String, String> item : scpeqMap.values()) {
        int parameterIndex = 1;
        ps.setString(parameterIndex++, item.get("case_provider_evaluation_id"));
        ps.setString(parameterIndex++, item.get("case_id"));
        ps.setString(parameterIndex++, item.get("provider_name"));
        ps.setObject(parameterIndex++, curTime);
        ps.setObject(parameterIndex++, curTime);
        ps.addBatch();

        // parameterIndex = 1;
        // ps2.setString(parameterIndex++, UUID.randomUUID().toString());
        // ps2.setString(parameterIndex++, item.get("case_id"));
        // ps2.setObject(parameterIndex++, curTime);
        // ps2.setObject(parameterIndex++, curTime);
        // ps2.addBatch();
      }
      ps.executeBatch();
      // ps2.executeBatch();
    }

    TQMCHelper.updateRecordFiles(
        con, insight, projectId, ProductTables.SOC, recordId, socRecord.getFiles());

    return TQMCHelper.getSocRecordHelper(con, recordId);
  }
}
