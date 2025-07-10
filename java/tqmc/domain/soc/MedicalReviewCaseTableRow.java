package tqmc.domain.soc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import tqmc.util.ConversionUtils;

public class MedicalReviewCaseTableRow {

  @JsonProperty("product")
  private String product;

  @JsonProperty("case_id")
  private String caseId;

  @JsonProperty("alias_record_id")
  private String aliasRecordId;

  @JsonProperty("patient_name")
  private String patientName;

  @JsonProperty("specialty_id")
  private String specialtyId;

  @JsonProperty("due_date_review")
  private LocalDate dueDateReview;

  @JsonProperty("is_reopened")
  private boolean isReopened;

  @JsonProperty("case_status")
  private String caseStatus;

  @JsonProperty("completed_at")
  private LocalDateTime completedAt;

  public String getProduct() {
    return product;
  }

  public void setProduct(String product) {
    this.product = product;
  }

  public String geCaseId() {
    return caseId;
  }

  public void setCaseId(String caseId) {
    this.caseId = caseId;
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

  public String getSpecialtyId() {
    return specialtyId;
  }

  public void setSpecialtyId(String specialtyId) {
    this.specialtyId = specialtyId;
  }

  public LocalDate getDueDateReview() {
    return dueDateReview;
  }

  public void setDueDateReview(LocalDate dueDateReview) {
    this.dueDateReview = dueDateReview;
  }

  public Boolean getIsReopened() {
    return isReopened;
  }

  public void setIsReopened(Boolean isReopened) {
    this.isReopened = isReopened;
  }

  public String getCaseStatus() {
    return caseStatus;
  }

  public void setCaseStatus(String caseStatus) {
    this.caseStatus = caseStatus;
  }

  public LocalDateTime getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(LocalDateTime completedAt) {
    this.completedAt = completedAt;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> outMap = new HashMap<>();
    outMap.put("product", product);
    outMap.put("case_id", caseId);
    outMap.put("alias_record_id", aliasRecordId);
    outMap.put("patient_name", patientName);
    outMap.put("specialty_id", specialtyId);
    outMap.put("due_date_review", ConversionUtils.getLocalDateString(dueDateReview));
    outMap.put("is_reopened", isReopened);
    outMap.put("case_status", caseStatus);
    outMap.put("completed_at", ConversionUtils.getLocalDateTimeString(completedAt));

    return outMap;
  }
}
