package tqmc.domain.soc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SocNurseReviewTableRow {

  @JsonProperty("nurse_review_id")
  private String nurseReviewId;

  @JsonProperty("alias_record_id")
  private String aliasRecordId;

  @JsonProperty("patient_name")
  private String patientName;

  @JsonProperty("dmis_id_list")
  private List<String> dmisIdList;

  @JsonProperty("due_date_review")
  private String dueDateReview;

  @JsonProperty("is_reopened")
  private Boolean isReopened;

  @JsonProperty("case_status")
  private String caseStatus;

  @JsonProperty("completed_at")
  private String completedAt;

  public String getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(String completedAt) {
    this.completedAt = completedAt;
  }

  public String getNurseReviewId() {
    return nurseReviewId;
  }

  public void setNurseReviewId(String nurseReviewId) {
    this.nurseReviewId = nurseReviewId;
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

  public List<String> getDmisIdList() {
    return dmisIdList;
  }

  public void setDmisIdList(List<String> dmisIdList) {
    this.dmisIdList = dmisIdList;
  }

  public String getDueDateReview() {
    return dueDateReview;
  }

  public void setDueDateReview(String dueDateReview) {
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

  public Map<String, Object> toMap() {
    Map<String, Object> outMap = new HashMap<>();
    outMap.put("nurse_review_id", nurseReviewId);
    outMap.put("alias_record_id", aliasRecordId);
    outMap.put("patient_name", patientName);
    outMap.put("dmis_id_list", dmisIdList);
    outMap.put("due_date_review", dueDateReview);
    outMap.put("is_reopened", isReopened);
    outMap.put("case_status", caseStatus);
    outMap.put("completed_at", completedAt);
    return outMap;
  }
}
