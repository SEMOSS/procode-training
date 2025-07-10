package tqmc.domain.mn;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tqmc.domain.base.Case;
import tqmc.util.ConversionUtils;

public class MnCase extends Case {

  public MnCase() {}

  public MnCase(String caseId) {
    super(caseId);
  }

  private String appealTypeId;
  private LocalDateTime assignedAt;

  @JsonProperty("attestation_signature")
  private String attestationSignature;

  private LocalDateTime attestedAt;
  private LocalDateTime createdAt;

  private LocalDateTime deletedAt;

  private LocalDate dueDateReview;

  private LocalDate dueDateDHA;

  private String recordId;
  private String reopeningReason;
  private String specialtyId;

  @JsonProperty("updated_at")
  private LocalDateTime updatedAt;

  private String userId;
  private Boolean complete;

  private List<MnCaseQuestion> questions;
  private List<Map<String, String>> questionsMap;

  @JsonProperty("recommendation_response")
  private String recommendationResponse;

  @JsonProperty("recommendation_explanation")
  private String recommendationExplanation;

  private String submissionGuid;

  private String attestationSpecialty;
  private String attestationSubspecialty;

  private String caseStatus;
  private Boolean isSecondLevel;
  private String activeUserId;

  public String getAppealTypeId() {
    return appealTypeId;
  }

  public void setAppealTypeId(String appealTypeId) {
    this.appealTypeId = appealTypeId;
  }

  public LocalDateTime getAssignedAt() {
    return assignedAt;
  }

  public void setAssignedAt(LocalDateTime assignedAt) {
    this.assignedAt = assignedAt;
  }

  public String getAttestationSignature() {
    return attestationSignature;
  }

  public void setAttestationSignature(String attestationSignature) {
    this.attestationSignature = attestationSignature;
  }

  public LocalDateTime getAttestedAt() {
    return attestedAt;
  }

  public void setAttestedAt(LocalDateTime attestedAt) {
    this.attestedAt = attestedAt;
  }

  public String getCaseId() {
    return super.caseId;
  }

  public void setCaseId(String caseId) {
    super.caseId = caseId;
  }

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

  public LocalDate getDueDateReview() {
    return dueDateReview;
  }

  public void setDueDateReview(LocalDate dueDateReview) {
    this.dueDateReview = dueDateReview;
  }

  public LocalDate getDueDateDHA() {
    return dueDateDHA;
  }

  public void setDueDateDHA(LocalDate dueDateDHA) {
    this.dueDateDHA = dueDateDHA;
  }

  public String getRecordId() {
    return recordId;
  }

  public void setRecordId(String recordId) {
    this.recordId = recordId;
  }

  public String getReopeningReason() {
    return reopeningReason;
  }

  public void setReopeningReason(String reopeningReason) {
    this.reopeningReason = reopeningReason;
  }

  public String getSpecialtyId() {
    return specialtyId;
  }

  public void setSpecialtyId(String specialtyId) {
    this.specialtyId = specialtyId;
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

  public Boolean getComplete() {
    return complete;
  }

  public void setComplete(Boolean complete) {
    this.complete = complete;
  }

  public List<MnCaseQuestion> getQuestions() {
    return questions;
  }

  public void setQuestions(List<MnCaseQuestion> questions) {
    this.questions = questions;
  }

  public List<Map<String, String>> getQuestionsMap() {
    return questionsMap;
  }

  public void setQuestionsMap(List<Map<String, String>> questionsMap) {
    this.questionsMap = questionsMap;
  }

  public String getRecommendationResponse() {
    return recommendationResponse;
  }

  public void setRecommendationResponse(String recommendationResponse) {
    this.recommendationResponse = recommendationResponse;
  }

  public String getRecommendationExplanation() {
    return recommendationExplanation;
  }

  public void setRecommendationExplanation(String recommendationExplanation) {
    this.recommendationExplanation = recommendationExplanation;
  }

  public String getSubmissionGuid() {
    return this.submissionGuid;
  }

  public void setSubmissionGuid(String submissionGuid) {
    this.submissionGuid = submissionGuid;
  }

  public String getAttestationSpecialty() {
    return this.attestationSpecialty;
  }

  public void setAttestationSpecialty(String attestationSpecialty) {
    this.attestationSpecialty = attestationSpecialty;
  }

  public String getAttestationSubspecialty() {
    return this.attestationSubspecialty;
  }

  public void setAttestationSubspecialty(String attestationSubspecialty) {
    this.attestationSubspecialty = attestationSubspecialty;
  }

  public String getCaseStatus() {
    return this.caseStatus;
  }

  public void setCaseStatus(String caseStatus) {
    this.caseStatus = caseStatus;
  }

  public Boolean getIsSecondLevel() {
    return this.isSecondLevel;
  }

  public void setIsSecondLevel(Boolean isSecondLevel) {
    this.isSecondLevel = isSecondLevel;
  }

  public String getActiveUserId() {
    return this.activeUserId;
  }

  public void setActiveUserId(String activeUserId) {
    this.activeUserId = activeUserId;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> m = new HashMap<>();
    m.put("case_id", this.caseId);
    m.put("case_status", this.caseStatus);
    m.put("specialty_id", this.specialtyId);
    m.put("due_date_review", ConversionUtils.getLocalDateString(this.dueDateReview));
    m.put("due_date_dha", ConversionUtils.getLocalDateString(this.dueDateDHA));
    m.put("updated_at", ConversionUtils.getLocalDateTimeString(this.updatedAt));
    m.put("attestation_signature", this.attestationSignature);
    m.put("attested_at", ConversionUtils.getLocalDateTimeString(this.attestedAt));
    m.put("reopening_reason", this.reopeningReason);
    m.put("is_second_level", this.isSecondLevel);
    m.put("recommendation_response", this.recommendationResponse);
    m.put("recommendation_explanation", this.recommendationExplanation);
    m.put("user_id", this.userId);
    m.put("active_user_id", this.activeUserId);
    m.put("questions", this.getQuestionsMap());
    return m;
  }
}
