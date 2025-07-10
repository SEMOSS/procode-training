package tqmc.reactors.soc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

public class UpdateSocRecordReactor extends AbstractTQMCReactor {

  /** The reactor takes a parameter record that is mandatory */
  public UpdateSocRecordReactor() {
    this.keysToGet = new String[] {"record"};
    this.keyRequired = new int[] {1};
  }

  private final LocalDateTime curTime = ConversionUtils.getUTCFromLocalNow();
  private Set<String> assignedCases;

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    /** Throws error if correct permissions are not met. */
    if (!hasProductManagementPermission(TQMCConstants.SOC)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    SocRecordPayload payload =
        TQMCHelper.getPayloadObject(store, keysToGet, SocRecordPayload.class);

    if (payload.getRecord() == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Inputs are required to complete action");
    }

    // Get record object and validate all fields
    SocRecord socRecord = payload.getRecord();

    if (socRecord.getRecordId() == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Record ID is null.");
    }
    assignedCases = TQMCHelper.getAssignedCases(con, socRecord.getRecordId(), ProductTables.SOC);

    if (socRecord.getProviders().isEmpty()) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Providers list is empty.");
    }
    if (socRecord.getNurseDueDateReview() == null || socRecord.getNurseDueDateDHA() == null) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Nurse review due dates required");
    }
    boolean hasMtfs = socRecord.getMtfList() != null && !socRecord.getMtfList().isEmpty();
    if (hasMtfs) {
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

    if (!TQMCHelper.canUpdate(con, TQMCConstants.TABLE_SOC_RECORD, socRecord)) {
      throw new TQMCException(ErrorCode.CONFLICT);
    }

    List<RecordFile> currentRecordFiles =
        TQMCHelper.getRecordFiles(con, ProductTables.SOC, socRecord.getRecordId());

    // SECTION 1: UPDATE SOC_RECORD
    updateSocRecord(con, socRecord);

    // SECTION 2: UPDATE SOC_RECORD_MTF
    updateSocRecordMtf(con, socRecord);

    // SECTION 3: DATA GETTER FOR SOC CASE, WORKFLOW, AND CASE_PROVIDER_EVALUATION
    Map<String, Map<String, String>> scqMap = new LinkedHashMap<>();
    Map<String, Map<String, String>> scpeqMap = new LinkedHashMap<>();

    getUpdationInfo(con, socRecord, scqMap, scpeqMap);

    // SECTION 4: UPDATE SOC_CASE
    socCaseUpdation(con, socRecord, scqMap);

    // SECTION 5: UPDATE SOC_WORKFLOW
    socWorkflowUpdation(con, socRecord, scqMap);

    // SECTION 6: UPDATE SOC_CASE_PROVIDER_EVALUATION
    socCaseProviderEvalUpdation(con, socRecord, scpeqMap);

    socNurseReviewUpdation(con, socRecord);

    // SECTION 7: UPDATE SOC_RECORD_FILE

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

    TQMCHelper.updateRecordFiles(
        con,
        insight,
        projectId,
        ProductTables.SOC,
        socRecord.getRecordId(),
        socRecord.getFiles(),
        currentRecordFiles);

    return TQMCHelper.getSocRecordHelper(con, socRecord.getRecordId());
  }

  private void socCaseProviderEvalUpdation(
      Connection con, SocRecord socRecord, Map<String, Map<String, String>> scpeqMap)
      throws SQLException, TQMCException {
    Set<String> cpeIds = scpeqMap.keySet();

    String socCaseProviderEvaluationSelect =
        "SELECT scpe.CASE_PROVIDER_EVALUATION_ID, scpe.DELETED_AT, scpe.PROVIDER_NAME, scpe.CASE_ID, sc.ASSIGNED_AT, sc.SPECIALTY_ID FROM "
            + TQMCConstants.TABLE_SOC_CASE_PROVIDER_EVALUATION
            + " scpe INNER JOIN SOC_CASE sc ON sc.CASE_ID = scpe.CASE_ID WHERE sc.RECORD_ID = ? AND sc.SUBMISSION_GUID IS NULL ";
    String socCaseProviderEvaluationUpdate =
        "UPDATE "
            + TQMCConstants.TABLE_SOC_CASE_PROVIDER_EVALUATION
            + " SET DELETED_AT = NULL, PROVIDER_NAME = ?, CASE_ID = ?, UPDATED_AT = ? WHERE CASE_PROVIDER_EVALUATION_ID = ?";
    String socCaseProviderEvaluationInsert =
        "INSERT INTO "
            + TQMCConstants.TABLE_SOC_CASE_PROVIDER_EVALUATION
            + " ("
            + "CASE_PROVIDER_EVALUATION_ID,"
            + "CASE_ID,"
            + "PROVIDER_NAME,"
            + "CREATED_AT,"
            + "UPDATED_AT) "
            + "VALUES (?,?,?,?,?)";
    String socCPEDeleteBatched =
        "UPDATE "
            + TQMCConstants.TABLE_SOC_CASE_PROVIDER_EVALUATION
            + " SET DELETED_AT = ? WHERE CASE_PROVIDER_EVALUATION_ID IN (?)";

    try (PreparedStatement psSelect = con.prepareStatement(socCaseProviderEvaluationSelect);
        PreparedStatement psUpdate = con.prepareStatement(socCaseProviderEvaluationUpdate);
        PreparedStatement psInsert = con.prepareStatement(socCaseProviderEvaluationInsert);
        PreparedStatement psDelete = con.prepareStatement(socCPEDeleteBatched)) {

      // Load existing records into a map
      Map<String, Map<String, String>> existingProviders = new HashMap<>();
      psSelect.setString(1, socRecord.getRecordId());
      ResultSet rs = psSelect.executeQuery();
      while (rs.next()) {
        Map<String, String> recordDetails = new HashMap<>();
        recordDetails.put("DELETED_AT", rs.getString("DELETED_AT"));
        recordDetails.put("ASSIGNED_AT", rs.getString("ASSIGNED_AT"));
        recordDetails.put("SPECIALTY_ID", rs.getString("SPECIALTY_ID"));
        recordDetails.put("PROVIDER_NAME", rs.getString("PROVIDER_NAME"));
        recordDetails.put("CASE_ID", rs.getString("CASE_ID"));
        existingProviders.put(rs.getString("CASE_PROVIDER_EVALUATION_ID"), recordDetails);
      }

      for (String cpeId : cpeIds) {
        String caseId = scpeqMap.get(cpeId).get("case_id");
        String providerName = scpeqMap.get(cpeId).get("provider_name");
        Map<String, String> existingProvider = existingProviders.get(cpeId);

        if (existingProvider == null) {
          // Check if we can insert a new provider evaluation
          boolean isAssigned =
              existingProviders.values().stream()
                  .anyMatch(
                      provider ->
                          caseId.equals(provider.get("CASE_ID"))
                              && provider.get("ASSIGNED_AT") != null);
          if (isAssigned) {
            throw new TQMCException(
                ErrorCode.BAD_REQUEST, "Cannot add providers to an already assigned case.");
          }
          // Insert new record
          int parameterIndex = 1;
          psInsert.setString(parameterIndex++, cpeId);
          psInsert.setString(parameterIndex++, caseId);
          psInsert.setString(parameterIndex++, providerName);
          psInsert.setString(parameterIndex++, curTime.toString());
          psInsert.setString(parameterIndex++, curTime.toString());
          psInsert.addBatch();
        } else {
          // Check if update is needed
          boolean isChanged =
              !existingProvider.get("PROVIDER_NAME").equals(providerName)
                  || !existingProvider.get("CASE_ID").equals(caseId);
          // Check whether new case is assigned
          boolean newCaseAssigned = this.isCaseAssigned(caseId);
          if (newCaseAssigned && isChanged) {
            throw new TQMCException(
                ErrorCode.BAD_REQUEST, "Cannot update providers on an already assigned case.");
          }
          if (isChanged) {
            psUpdate.setString(1, providerName);
            psUpdate.setString(2, caseId);
            psUpdate.setString(3, curTime.toString());
            psUpdate.setString(4, cpeId);
            psUpdate.addBatch();
          }
        }
      }

      Set<String> toDeleteCpeIds = new HashSet<>(existingProviders.keySet());
      toDeleteCpeIds.removeAll(cpeIds);

      for (String cpeId : toDeleteCpeIds) {
        Map<String, String> recordDetails = existingProviders.get(cpeId);
        if (recordDetails.get("ASSIGNED_AT") != null) {
          throw new TQMCException(
              ErrorCode.BAD_REQUEST, "Cannot delete providers on an already assigned case.");
        }
        psDelete.setString(1, curTime.toString());
        psDelete.setString(2, cpeId);
        psDelete.addBatch();
      }

      psUpdate.executeBatch();
      psInsert.executeBatch();
      psDelete.executeBatch();
    }
  }

  private void socWorkflowUpdation(
      Connection con, SocRecord socRecord, Map<String, Map<String, String>> scqMap)
      throws SQLException {
    List<String> caseIds =
        scqMap.values().stream().map(e -> e.get("case_id")).collect(Collectors.toList());
    Set<String> sids = scqMap.keySet();

    String socWorkflowSelect =
        "SELECT CASE_ID FROM "
            + TQMCConstants.TABLE_SOC_WORKFLOW
            + " WHERE IS_LATEST = 1 AND CASE_ID IN (SELECT CASE_ID FROM "
            + TQMCConstants.TABLE_SOC_CASE
            + " WHERE RECORD_ID = ? AND SUBMISSION_GUID IS NULL)";
    String socWorkflowInsert =
        "INSERT INTO "
            + TQMCConstants.TABLE_SOC_WORKFLOW
            + " (CASE_ID, CASE_TYPE, GUID, IS_LATEST, RECIPIENT_USER_ID, SMSS_TIMESTAMP, STEP_STATUS) VALUES (?, ?, ?, ?, ?, ?, ?)";
    String socWorkflowDelete =
        "UPDATE "
            + TQMCConstants.TABLE_SOC_WORKFLOW
            + " SET IS_LATEST = 0 WHERE IS_LATEST = 1 AND CASE_ID IN (?)";

    // Step 1: Fetch all relevant records once
    Set<String> existingCaseIds = new HashSet<>();
    try (PreparedStatement psSelect = con.prepareStatement(socWorkflowSelect)) {
      psSelect.setString(1, socRecord.getRecordId());
      try (ResultSet rs = psSelect.executeQuery()) {
        while (rs.next()) {
          existingCaseIds.add(rs.getString("CASE_ID"));
        }
      }
    }

    try (PreparedStatement psInsert = con.prepareStatement(socWorkflowInsert);
        PreparedStatement psDelete = con.prepareStatement(socWorkflowDelete)) {

      for (String sid : sids) {
        String caseId = scqMap.get(sid).get("case_id");
        if (!existingCaseIds.contains(caseId)) {
          // Record does not exist, insert new record
          int parameterIndex = 1;
          psInsert.setString(parameterIndex++, caseId);
          psInsert.setString(parameterIndex++, SocCaseType.PEER_REVIEW.getCaseType());
          psInsert.setString(parameterIndex++, UUID.randomUUID().toString());
          psInsert.setBoolean(parameterIndex++, true);
          psInsert.setString(parameterIndex++, "system");
          psInsert.setString(parameterIndex++, curTime.toString());
          psInsert.setString(parameterIndex++, "unassigned");
          psInsert.addBatch();
        }
      }

      Set<String> toDeleteCaseIds = new HashSet<>(existingCaseIds);
      toDeleteCaseIds.removeAll(caseIds);

      for (String caseId : toDeleteCaseIds) {
        psDelete.setString(1, caseId);
        psDelete.addBatch();
      }

      psInsert.executeBatch();
      psDelete.executeBatch();
    }
  }

  private void socCaseUpdation(
      Connection con, SocRecord socRecord, Map<String, Map<String, String>> scqMap)
      throws SQLException {

    Set<String> caseIds =
        scqMap.values().parallelStream().map(e -> e.get("case_id")).collect(Collectors.toSet());
    Set<String> sids = scqMap.keySet();
    String socCaseQuerySelect =
        "SELECT CASE_ID, SPECIALTY_ID, DELETED_AT FROM "
            + TQMCConstants.TABLE_SOC_CASE
            + " WHERE RECORD_ID = ? AND SUBMISSION_GUID IS NULL";
    String socCaseQueryUpdate =
        "UPDATE "
            + TQMCConstants.TABLE_SOC_CASE
            + " SET DELETED_AT = NULL, SPECIALTY_ID = ?, UPDATED_AT = ?, DUE_DATE_REVIEW = ?, DUE_DATE_DHA = ? WHERE RECORD_ID = ? AND CASE_ID = ? AND SUBMISSION_GUID IS NULL";
    String socCaseQueryInsert =
        "INSERT INTO "
            + TQMCConstants.TABLE_SOC_CASE
            + " (CASE_ID, CREATED_AT, RECORD_ID, SPECIALTY_ID, UPDATED_AT, DUE_DATE_REVIEW, DUE_DATE_DHA) VALUES (?, ?, ?, ?, ?, ?, ?)";
    String socCaseQueryDelete =
        "UPDATE "
            + TQMCConstants.TABLE_SOC_CASE
            + " SET DELETED_AT = ? WHERE RECORD_ID = ? AND CASE_ID IN (?) AND SUBMISSION_GUID IS NULL";

    // Step 1: Fetch all relevant records once
    Map<String, Set<Map<String, String>>> existingRecords = new HashMap<>();
    try (PreparedStatement psSelect = con.prepareStatement(socCaseQuerySelect)) {
      psSelect.setString(1, socRecord.getRecordId());
      try (ResultSet rs = psSelect.executeQuery()) {
        while (rs.next()) {
          Map<String, String> record = new HashMap<>();
          record.put("SPECIALTY_ID", rs.getString("SPECIALTY_ID"));
          record.put("DELETED_AT", rs.getString("DELETED_AT"));
          Set<Map<String, String>> curSet = new HashSet<>();
          if (existingRecords.containsKey(rs.getString("CASE_ID"))) {
            curSet = existingRecords.get(rs.getString("CASE_ID"));
          }
          curSet.add(record);
          existingRecords.put(rs.getString("CASE_ID"), curSet);
        }
      }
    }

    try (PreparedStatement psInsert = con.prepareStatement(socCaseQueryInsert);
        PreparedStatement psUpdate = con.prepareStatement(socCaseQueryUpdate);
        PreparedStatement psDelete = con.prepareStatement(socCaseQueryDelete)) {

      for (String sid : sids) {
        String caseId = scqMap.get(sid).get("case_id");
        Set<Map<String, String>> erfc = existingRecords.get(caseId);
        SocSpecialtyIdMap ssid = socRecord.getDueDateMap().get(sid);
        Map<String, String> existingRecord = null;
        if (erfc != null && !erfc.isEmpty()) {
          for (Map<String, String> r : erfc) {
            String rSpecialtyId = r.get("SPECIALTY_ID");
            if (sid.equals(rSpecialtyId) && r.get("DELETED_AT") == null) {
              existingRecord = r;
              break;
            }
          }
        }
        if (existingRecord == null) {
          // Record does not exist, insert new record
          int parameterIndex = 1;
          psInsert.setString(parameterIndex++, caseId);
          psInsert.setString(parameterIndex++, curTime.toString());
          psInsert.setString(parameterIndex++, socRecord.getRecordId());
          psInsert.setString(parameterIndex++, sid);
          psInsert.setString(parameterIndex++, curTime.toString());
          psInsert.setObject(parameterIndex++, ssid.getDueDateReview());
          psInsert.setObject(parameterIndex++, ssid.getDueDateDHA());
          psInsert.addBatch();
        } else {
          // Record exists, check if update is needed
          int parameterIndex = 1;
          psUpdate.setString(parameterIndex++, sid);
          psUpdate.setString(parameterIndex++, curTime.toString());
          psUpdate.setObject(parameterIndex++, ssid.getDueDateReview());
          psUpdate.setObject(parameterIndex++, ssid.getDueDateDHA());
          psUpdate.setString(parameterIndex++, socRecord.getRecordId());
          psUpdate.setString(parameterIndex++, caseId);
          psUpdate.addBatch();
        }
      }

      Set<String> toDeleteCaseIds = new HashSet<>(existingRecords.keySet());
      toDeleteCaseIds.removeAll(caseIds);

      psDelete.setString(1, curTime.toString());
      psDelete.setString(2, socRecord.getRecordId());
      for (String caseId : toDeleteCaseIds) {
        psDelete.setString(3, caseId);
        psDelete.addBatch();
      }

      psUpdate.executeBatch();
      psInsert.executeBatch();
      psDelete.executeBatch();
    }
  }

  private void getUpdationInfo(
      Connection con,
      SocRecord socRecord,
      Map<String, Map<String, String>> scqMap,
      Map<String, Map<String, String>> scpeqMap)
      throws SQLException {

    for (Provider item : socRecord.getProviders()) {
      String getCaseIdQuery =
          "SELECT sc.CASE_ID, SPECIALTY_ID FROM "
              + TQMCConstants.TABLE_SOC_CASE_PROVIDER_EVALUATION
              + " scpe LEFT OUTER JOIN SOC_CASE sc "
              + " ON sc.CASE_ID = scpe.CASE_ID"
              + " WHERE CASE_PROVIDER_EVALUATION_ID = ?";

      try (PreparedStatement ps = con.prepareStatement(getCaseIdQuery)) {

        String caseProviderEvaluationId = item.getCaseProviderEvaluationId();
        if (!scqMap.containsKey(item.getSpecialtyId())) {
          Map<String, String> internalScqMap = new HashMap<>();
          String caseId = null;
          ps.setString(1, item.getCaseProviderEvaluationId());
          ResultSet rs = ps.executeQuery();
          if (rs.next()) {
            if (caseProviderEvaluationId != null) {
              String specialtyId = rs.getString("SPECIALTY_ID");
              if (item.getSpecialtyId().equals(specialtyId)) {
                caseId = rs.getString("CASE_ID");
              }
            }
          }
          if (caseId == null) {
            caseId =
                TQMCHelper.getDisplayId(
                    TQMCConstants.SOC + "-" + TQMCConstants.CASE,
                    TQMCHelper.getNextId(con, TQMCConstants.TABLE_SOC_CASE),
                    TQMCConstants.PADDING_LENGTH);
          }
          internalScqMap.put("case_id", caseId);
          internalScqMap.put("sid", item.getSpecialtyId());
          scqMap.put(item.getSpecialtyId(), internalScqMap);
        }
        caseProviderEvaluationId =
            (caseProviderEvaluationId == null)
                ? UUID.randomUUID().toString()
                : caseProviderEvaluationId;
        Map<String, String> internalScpeqMap = new HashMap<>();
        internalScpeqMap.put("provider_name", item.getProviderName());

        internalScpeqMap.put("case_provider_evaluation_id", caseProviderEvaluationId);
        internalScpeqMap.put("case_id", scqMap.get(item.getSpecialtyId()).get("case_id"));
        internalScpeqMap.put("sid", item.getSpecialtyId());

        scpeqMap.put(caseProviderEvaluationId, internalScpeqMap);
      }
    }
  }

  private void updateSocRecordMtf(Connection con, SocRecord socRecord) throws SQLException {
    Set<String> newMtfIds = new HashSet<>();
    boolean hasMtfs = socRecord.getMtfList() != null && !socRecord.getMtfList().isEmpty();
    if (hasMtfs) {
      newMtfIds = socRecord.getMtfList().parallelStream().collect(Collectors.toSet());
    }

    String selectSql =
        "SELECT DMIS_ID FROM " + TQMCConstants.TABLE_SOC_RECORD_MTF + " WHERE RECORD_ID = ?";
    String insertSql =
        "INSERT INTO "
            + TQMCConstants.TABLE_SOC_RECORD_MTF
            + " (RECORD_ID, DMIS_ID, RECORD_MTF_ID) VALUES (?, ?, ?)";
    String deleteSql =
        "DELETE FROM "
            + TQMCConstants.TABLE_SOC_RECORD_MTF
            + " WHERE RECORD_ID = ? AND DMIS_ID = ?";

    // Prepare the statements
    try (PreparedStatement selectStmt = con.prepareStatement(selectSql);
        PreparedStatement deleteStmt = con.prepareStatement(deleteSql)) {
      // Retrieve existing MTF_IDs
      selectStmt.setString(1, socRecord.getRecordId());
      ResultSet rs = selectStmt.executeQuery();
      Set<String> existingMtfIds = new HashSet<>();
      while (rs.next()) {
        existingMtfIds.add(rs.getString("DMIS_ID"));
      }

      // Determine which MTF_IDs need to be deleted
      deleteStmt.setString(1, socRecord.getRecordId());
      for (String existingMtfId : existingMtfIds) {
        if (!newMtfIds.contains(existingMtfId)) {
          deleteStmt.setString(2, existingMtfId);
          deleteStmt.addBatch();
        }
      }
      // Determine which MTF_IDs need to be inserted
      if (hasMtfs) {
        PreparedStatement insertStmt = con.prepareStatement(insertSql);
        for (String mtfId : newMtfIds) {
          if (!existingMtfIds.contains(mtfId)) {
            insertStmt.setString(1, socRecord.getRecordId());
            insertStmt.setString(2, mtfId);
            insertStmt.setString(3, UUID.randomUUID().toString());
            insertStmt.addBatch();
          }
        }
        insertStmt.executeBatch();
      }
      deleteStmt.executeBatch();
    }
  }

  private void updateSocRecord(Connection con, SocRecord socRecord) throws SQLException {
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE "
                + TQMCConstants.TABLE_SOC_RECORD
                + " SET UPDATED_AT = ?, ALIAS_RECORD_ID = ?, PATIENT_LAST_NAME = ?, PATIENT_FIRST_NAME = ? WHERE RECORD_ID = ?")) {
      int parameterIndex = 1;
      ps.setString(parameterIndex++, curTime.toString());
      ps.setString(parameterIndex++, socRecord.getAliasRecordId());
      ps.setString(parameterIndex++, socRecord.getPatientLastName());
      ps.setString(parameterIndex++, socRecord.getPatientFirstName());
      ps.setString(parameterIndex++, socRecord.getRecordId());
      ps.execute();
    }
  }

  private void socNurseReviewUpdation(Connection con, SocRecord socRecord) throws SQLException {
    try (PreparedStatement ps =
        con.prepareStatement(
            "UPDATE "
                + TQMCConstants.TABLE_SOC_NURSE_REVIEW
                + " SET DUE_DATE_REVIEW = ?, DUE_DATE_DHA = ? WHERE RECORD_ID = ? AND SUBMISSION_GUID IS NULL")) {
      int parameterIndex = 1;
      ps.setObject(parameterIndex++, socRecord.getNurseDueDateReview());
      ps.setObject(parameterIndex++, socRecord.getNurseDueDateDHA());
      ps.setString(parameterIndex++, socRecord.getRecordId());
      ps.execute();
    }
  }

  private boolean isCaseAssigned(String caseId) {
    return this.assignedCases.contains(caseId);
  }
}
