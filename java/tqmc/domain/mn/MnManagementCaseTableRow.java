package tqmc.domain.mn;

import java.util.HashMap;
import java.util.Map;
import tqmc.domain.base.CaseStatus;

public class MnManagementCaseTableRow {

  private String caseId;
  private String caseStatus;
  private String recordId;
  private String appealTypeId;
  private String specialtyId;
  private String careContractorName;
  private Boolean hasFiles;
  private String dueDateReview;
  private String dueDateDHA;
  private String userId;
  private String assignedDate;
  private String aliasRecordId;
  private String patientName;
  private String patientNameQuery;
  private String completedAtDate;

  public MnManagementCaseTableRow(
      String caseId,
      CaseStatus caseStatus,
      String recordId,
      String appealTypeId,
      String specialtyId,
      String careContractorName,
      Boolean hasFiles,
      String dueDateReview,
      String dueDateDHA,
      String userId,
      String assignedDate,
      String aliasRecordId,
      String patientName,
      String patientNameQuery,
      String completedAtDate) {
    this.caseId = caseId;
    this.caseStatus = caseStatus.getCaseStatus();
    this.recordId = recordId;
    this.appealTypeId = appealTypeId;
    this.specialtyId = specialtyId;
    this.careContractorName = careContractorName;
    this.hasFiles = hasFiles;
    this.dueDateReview = dueDateReview;
    this.dueDateDHA = dueDateDHA;
    this.userId = userId;
    this.assignedDate = assignedDate;
    this.aliasRecordId = aliasRecordId;
    this.patientName = patientName;
    this.patientNameQuery = patientNameQuery;
    this.completedAtDate = completedAtDate;
  }

  public String getCaseId() {
    return caseId;
  }

  public void setCaseId(String caseId) {
    this.caseId = caseId;
  }

  public String getCaseStatus() {
    return caseStatus;
  }

  public void setCaseStatus(CaseStatus caseStatus) {
    this.caseStatus = caseStatus.getCaseStatus();
  }

  public String getRecordId() {
    return recordId;
  }

  public void setRecordId(String recordId) {
    this.recordId = recordId;
  }

  public String getAppealTypeId() {
    return appealTypeId;
  }

  public void setAppealTypeId(String appealTypeId) {
    this.appealTypeId = appealTypeId;
  }

  public String getSpecialtyId() {
    return specialtyId;
  }

  public void setSpecialtyId(String specialtyId) {
    this.specialtyId = specialtyId;
  }

  public String getCareContractorName() {
    return careContractorName;
  }

  public void setCareContractorName(String careContractorName) {
    this.careContractorName = careContractorName;
  }

  public Boolean getHasFiles() {
    return hasFiles;
  }

  public void setHasFiles(Boolean hasFiles) {
    this.hasFiles = hasFiles;
  }

  public String getDueDateReview() {
    return dueDateReview;
  }

  public void setDueDateReview(String dueDateReview) {
    this.dueDateReview = dueDateReview;
  }

  public String getDueDateDHA() {
    return dueDateDHA;
  }

  public void setDueDateDHA(String dueDateDHA) {
    this.dueDateDHA = dueDateDHA;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getAssignedDate() {
    return assignedDate;
  }

  public void setAssignedDate(String assignedDate) {
    this.assignedDate = assignedDate;
  }

  public String getAliasRecordId() {
    return aliasRecordId;
  }

  public void setAliasRecordId(String aliasRecordId) {
    this.aliasRecordId = aliasRecordId;
  }

  public String getPatientName() {
    return patientName;
  }

  public void setPatientName(String patientName) {
    this.patientName = patientName;
  }

  public String getPatientNameQuery() {
    return patientNameQuery;
  }

  public void setPatientNameQuery(String patientNameQuery) {
    this.patientNameQuery = patientNameQuery;
  }

  public String getCompletedAtDate() {
    return completedAtDate;
  }

  public void setCompletedAtDate(String completedAtDate) {
    this.completedAtDate = completedAtDate;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> outMap = new HashMap<>();

    outMap.put("case_id", this.caseId);
    outMap.put("case_status", this.caseStatus);
    outMap.put("record_id", this.recordId);
    outMap.put("appeal_type_id", this.appealTypeId);
    outMap.put("specialty_id", this.specialtyId);
    outMap.put("care_contractor_name", this.careContractorName);
    outMap.put("has_files", this.hasFiles);
    outMap.put("due_date_review", this.dueDateReview);
    outMap.put("due_date_dha", this.dueDateDHA);
    outMap.put("user_id", this.userId);
    outMap.put("assigned_date", this.assignedDate);
    outMap.put("alias_record_id", this.aliasRecordId);
    outMap.put("patient_name", this.patientName);
    outMap.put("patient_name_query", this.patientNameQuery);
    outMap.put("completed_at", completedAtDate);

    return outMap;
  }
}
