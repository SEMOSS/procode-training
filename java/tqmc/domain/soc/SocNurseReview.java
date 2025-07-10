package tqmc.domain.soc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class SocNurseReview {

  private LocalDateTime assignedAt;

  @JsonProperty("nurse_review_id")
  private String nurseReviewId;

  @JsonProperty("nurse_reviewer_name")
  private String nurseReviewName;

  @JsonProperty("record_id")
  private String recordId;

  @JsonProperty("period_of_care_start")
  private String periodOfCareStart;

  @JsonProperty("period_of_care_end")
  private String periodOfCareEnd;

  private String injury;
  private String diagnoses;
  private String allegations;

  @JsonProperty("summary_of_facts")
  private String summaryOfFacts;

  @JsonProperty("created_at")
  private LocalDateTime createdAt;

  @JsonProperty("deleted_at")
  private LocalDateTime deletedAt;

  @JsonProperty("updated_at")
  private LocalDateTime updatedAt;

  @JsonProperty("user_id")
  private String userId;

  @JsonProperty("case_status")
  private String caseStatus;

  @JsonProperty("case_type")
  private String caseType;

  @JsonProperty("patient_name")
  private String patientName;

  @JsonProperty("time_remaining_days")
  private String timeRemainingDays;

  private Boolean complete;
  private String reopeningReason;
  private LocalDate dueDateDha;
  private LocalDate dueDateReview;

  public LocalDateTime getAssignedAt() {
    return assignedAt;
  }

  public void setAssignedAt(LocalDateTime assignedAt) {
    this.assignedAt = assignedAt;
  }

  public String getNurseReviewId() {
    return nurseReviewId;
  }

  public void setNurseReviewId(String nurseReviewId) {
    this.nurseReviewId = nurseReviewId;
  }

  public String getPeriodOfCareStart() {
    return periodOfCareStart;
  }

  public void setPeriodOfCareStart(String periodOfCareStart) {
    this.periodOfCareStart = periodOfCareStart;
  }

  public String getPeriodOfCareEnd() {
    return periodOfCareEnd;
  }

  public void setPeriodOfCareEnd(String periodOfCareEnd) {
    this.periodOfCareEnd = periodOfCareEnd;
  }

  public String getInjury() {
    return injury;
  }

  public void setInjury(String injury) {
    this.injury = injury;
  }

  public String getDiagnoses() {
    return diagnoses;
  }

  public void setDiagnoses(String diagnoses) {
    this.diagnoses = diagnoses;
  }

  public String getAllegations() {
    return allegations;
  }

  public void setAllegations(String allegations) {
    this.allegations = allegations;
  }

  public String getSummaryOfFacts() {
    return summaryOfFacts;
  }

  public void setSummaryOfFacts(String summaryOfFacts) {
    this.summaryOfFacts = summaryOfFacts;
  }

  public SocNurseReview() {}

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }

  public String getRecordId() {
    return recordId;
  }

  public void setRecordId(String recordId) {
    this.recordId = recordId;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getCaseStatus() {
    return caseStatus;
  }

  public void setCaseStatus(String caseStatus) {
    this.caseStatus = caseStatus;
  }

  public String getCaseType() {
    return caseType;
  }

  public void setCaseType(String caseType) {
    this.caseType = caseType;
  }

  public String getPatientName() {
    return patientName;
  }

  public void setPatientName(String patientName) {
    this.patientName = patientName;
  }

  public String getTimeRemainingDays() {
    return timeRemainingDays;
  }

  public void setTimeRemainingDays(String timeRemainingDays) {
    this.timeRemainingDays = timeRemainingDays;
  }

  public Boolean getComplete() {
    return this.complete;
  }

  public void setComplete(Boolean complete) {
    this.complete = complete;
  }

  public String getReopeningReason() {
    return this.reopeningReason;
  }

  public void setReopeningReason(String reopeningReason) {
    this.reopeningReason = reopeningReason;
  }

  public LocalDate getDueDateDha() {
    return dueDateDha;
  }

  public void setDueDateDha(LocalDate dueDateDha) {
    this.dueDateDha = dueDateDha;
  }

  public LocalDate getDueDateReview() {
    return dueDateReview;
  }

  public void setDueDateReview(LocalDate dueDateReview) {
    this.dueDateReview = dueDateReview;
  }
}
