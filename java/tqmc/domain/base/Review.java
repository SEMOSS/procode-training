package tqmc.domain.base;

import java.time.LocalDate;

public class Review {

  // DB fields
  private String recordId;
  private String aliasRecordId;
  private String caseId;
  private String stepStatus;
  private String userId;
  private String email;
  private LocalDate dueDateReview;
  private Boolean dueSoon;
  private Boolean pastDue;
  private String caseType; // Peer Review, Nurse Review, etc
  private String caseSuperType; // MN, SOC, etc TODO: come up with better name?

  // Used in Daily Digest
  private String specialty;
  private String subspecialty;
  private String reviewerFirstName;
  private String reviewerLastName;
  private LocalDate dueDateDha;
  private LocalDate completedDate;

  public Review(
      String recordId,
      String aliasRecordId,
      String caseId,
      String stepStatus,
      String userId,
      String email,
      LocalDate dueDateReview,
      LocalDate dueDateDha,
      LocalDate completedDate,
      Boolean dueSoon,
      Boolean pastDue,
      String caseType,
      String caseSuperType,
      String reviewerFirstName,
      String reviewerLastName,
      String specialty,
      String subspecialty) {
    this.recordId = recordId;
    this.aliasRecordId = aliasRecordId;
    this.caseId = caseId;
    this.stepStatus = stepStatus;
    this.userId = userId;
    this.email = email;
    this.dueDateReview = dueDateReview;
    this.dueDateDha = dueDateDha;
    this.completedDate = completedDate;
    this.dueSoon = dueSoon;
    this.pastDue = pastDue;
    this.caseType = caseType;
    this.caseSuperType = caseSuperType;
    this.reviewerFirstName = reviewerFirstName;
    this.reviewerLastName = reviewerLastName;
    this.specialty = specialty;
    this.subspecialty = subspecialty;
  }

  public LocalDate getCompletedDate() {
    return completedDate;
  }

  public void setCompletedDate(LocalDate completedDate) {
    this.completedDate = completedDate;
  }

  public String getCaseId() {
    return caseId;
  }

  public void setCaseId(String caseId) {
    this.caseId = caseId;
  }

  public Boolean getPastDue() {
    return pastDue;
  }

  public Boolean getDueSoon() {
    return dueSoon;
  }

  public void setDueSoon(Boolean dueSoon) {
    this.dueSoon = dueSoon;
  }

  public Boolean isPastDue() {
    return pastDue;
  }

  public void setPastDue(Boolean pastDue) {
    this.pastDue = pastDue;
  }

  public String getSpecialty() {
    return specialty;
  }

  public void setSpecialty(String specialty) {
    this.specialty = specialty;
  }

  public String getSubspecialty() {
    return subspecialty;
  }

  public void setSubspecialty(String subspecialty) {
    this.subspecialty = subspecialty;
  }

  public String getReviewerFirstName() {
    return reviewerFirstName;
  }

  public void setReviewerFirstName(String reviewerFirstName) {
    this.reviewerFirstName = reviewerFirstName;
  }

  public String getReviewerLastName() {
    return reviewerLastName;
  }

  public void setReviewerLastName(String reviewerLastName) {
    this.reviewerLastName = reviewerLastName;
  }

  public LocalDate getDueDateDha() {
    return dueDateDha;
  }

  public void setDueDateDha(LocalDate dueDateDha) {
    this.dueDateDha = dueDateDha;
  }

  public void setAliasRecordId(String aliasRecordId) {
    this.aliasRecordId = aliasRecordId;
  }

  public void setStepStatus(String stepStatus) {
    this.stepStatus = stepStatus;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public void setDueDateReview(LocalDate dueDateReview) {
    this.dueDateReview = dueDateReview;
  }

  public void setCaseType(String caseType) {
    this.caseType = caseType;
  }

  public void setCaseSuperType(String caseSuperType) {
    this.caseSuperType = caseSuperType;
  }

  public Boolean isDueSoon() {
    return dueSoon;
  }

  public String getAliasRecordId() {
    return aliasRecordId;
  }

  public String getStepStatus() {
    return stepStatus;
  }

  public String getUserId() {
    return userId;
  }

  public String getEmail() {
    return email;
  }

  public LocalDate getDueDateReview() {
    return dueDateReview;
  }

  public String getCaseType() {
    return caseType;
  }

  public String getCaseSuperType() {
    return caseSuperType;
  }

  public void setRecordId(String recordId) {
    this.recordId = recordId;
  }

  public String getRecordId() {
    return recordId;
  }

  @Override
  public String toString() {
    return "Review{"
        + "aliasRecordId='"
        + aliasRecordId
        + '\''
        + ", stepStatus='"
        + stepStatus
        + '\''
        + ", userId='"
        + userId
        + '\''
        + ", dueDateReview='"
        + dueDateReview
        + '\''
        + ", caseType='"
        + caseType
        + '\''
        + ", caseSuperType ='"
        + caseSuperType
        + '\''
        + '}';
  }
}
