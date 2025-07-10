package tqmc.domain.qu.base;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import tqmc.domain.base.CaseStatus;
import tqmc.util.ConversionUtils;

public class QuCaseTableRow {
  private String caseId;
  private String caseStatus;
  private String recordId;
  private String aliasRecordId;
  private String claimNumber;
  private String patientName;
  private String patientNameQuery;
  private String careContractorName;
  private String caseType;
  private Boolean hasFiles;
  private Integer timeRemainingDays;
  private LocalDate receivedAt;
  private String userName;
  private String userId;
  private String userNameQuery;
  private LocalDate assignedDate;
  private LocalDateTime updatedAt;

  public QuCaseTableRow(
      String caseId,
      CaseStatus caseStatus,
      String recordId,
      String aliasRecordId,
      String claimNumber,
      String patientName,
      String patientNameQuery,
      String careContractorName,
      String caseType,
      Boolean hasFiles,
      Integer timeRemainingDays,
      LocalDate receivedAt,
      String userName,
      String userId,
      String userNameQuery,
      LocalDate assignedDate) {
    this.caseId = caseId;
    this.caseStatus = caseStatus.getCaseStatus();
    this.recordId = recordId;
    this.aliasRecordId = aliasRecordId;
    this.claimNumber = claimNumber;
    this.patientName = patientName;
    this.patientNameQuery = patientNameQuery;
    this.careContractorName = careContractorName;
    this.caseType = caseType;
    this.hasFiles = hasFiles;
    this.timeRemainingDays = timeRemainingDays;
    this.receivedAt = receivedAt;
    this.userName = userName;
    this.userId = userId;
    this.userNameQuery = userNameQuery;
    this.assignedDate = assignedDate;
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

  public String getAliasRecordId() {
    return aliasRecordId;
  }

  public void setAliasRecordId(String aliasRecordId) {
    this.aliasRecordId = aliasRecordId;
  }

  public String getClaimNumber() {
    return claimNumber;
  }

  public void setClaimNumber(String claimNumber) {
    this.claimNumber = claimNumber;
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

  public String getCareContractorName() {
    return careContractorName;
  }

  public void setCareContractorName(String careContractorName) {
    this.careContractorName = careContractorName;
  }

  public String getCaseType() {
    return caseType;
  }

  public void setCaseType(String caseType) {
    this.caseType = caseType;
  }

  public Boolean getHasFiles() {
    return hasFiles;
  }

  public void setHasFiles(Boolean hasFiles) {
    this.hasFiles = hasFiles;
  }

  public Integer getTimeRemainingDays() {
    return timeRemainingDays;
  }

  public void setTimeRemainingDays(Integer timeRemainingDays) {
    this.timeRemainingDays = timeRemainingDays;
  }

  public LocalDate getReceivedAt() {
    return receivedAt;
  }

  public void setReceivedAt(LocalDate receivedAt) {
    this.receivedAt = receivedAt;
  }

  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getUserNameQuery() {
    return userNameQuery;
  }

  public void setUserNameQuery(String userNameQuery) {
    this.userNameQuery = userNameQuery;
  }

  public LocalDate getAssignedDate() {
    return assignedDate;
  }

  public void setAssignedDate(LocalDate assignedDate) {
    this.assignedDate = assignedDate;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> outMap = new HashMap<>();

    outMap.put("case_id", this.caseId);
    outMap.put("case_status", this.caseStatus);
    outMap.put("record_id", this.recordId);
    outMap.put("alias_record_id", this.aliasRecordId);
    outMap.put("claim_number", this.claimNumber);
    outMap.put("patient_name", this.patientName);
    outMap.put("patient_name_query", this.patientNameQuery);
    outMap.put("care_contractor_name", this.careContractorName);
    outMap.put("case_type", this.caseType);
    outMap.put("has_files", this.hasFiles);
    outMap.put("time_remaining_days", this.timeRemainingDays);
    outMap.put("assigned_to_user_name", this.userName);
    outMap.put("assigned_to_user_id", this.userId);
    outMap.put("assigned_to_user_query", this.userNameQuery);
    outMap.put("assigned_date", ConversionUtils.getLocalDateString(this.assignedDate));
    outMap.put("complete_files_received_at", ConversionUtils.getLocalDateString(this.receivedAt));
    return outMap;
  }
}
