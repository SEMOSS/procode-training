package tqmc.domain.mn;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import tqmc.domain.base.Record;
import tqmc.domain.base.RecordFile;

public class MnRecord extends Record {

  @JsonProperty("care_contractor_id")
  private String careContractorId;

  @JsonProperty("facility_name")
  private String facilityName;

  @JsonProperty("specialty_id")
  private String specialtyId;

  @JsonProperty("appeal_type_id")
  private String appealTypeId;

  private List<MnCaseQuestion> questions;

  @JsonProperty("created_at")
  private LocalDateTime createdAt;

  private List<RecordFile> files;

  @JsonProperty("alias_record_id")
  private String aliasRecordId;

  @JsonProperty("patient_last_name")
  private String patientLastName;

  @JsonProperty("patient_first_name")
  private String patientFirstName;

  @JsonProperty("due_date_review")
  private LocalDate dueDateReview;

  @JsonProperty("due_date_dha")
  private LocalDate dueDateDHA;

  public String getCareContractorId() {
    return careContractorId;
  }

  public void setCareContractorId(String careContractorId) {
    this.careContractorId = careContractorId;
  }

  public String getFacilityName() {
    return facilityName;
  }

  public void setFacilityName(String facilityName) {
    this.facilityName = facilityName;
  }

  public String getSpecialtyId() {
    return specialtyId;
  }

  public void setSpecialtyId(String specialtyId) {
    this.specialtyId = specialtyId;
  }

  public String getAppealTypeId() {
    return appealTypeId;
  }

  public void setAppealTypeId(String appealTypeId) {
    this.appealTypeId = appealTypeId;
  }

  public List<MnCaseQuestion> getQuestions() {
    return questions;
  }

  public void setQuestions(List<MnCaseQuestion> questions) {
    this.questions = questions;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public List<RecordFile> getFiles() {
    return files;
  }

  public void setFiles(List<RecordFile> files) {
    this.files = files;
  }

  public String getAliasRecordId() {
    return aliasRecordId;
  }

  public void setAliasRecordId(String aliasRecordId) {
    this.aliasRecordId = aliasRecordId;
  }

  public String getPatientLastName() {
    return patientLastName;
  }

  public void setPatientLastName(String patientLastName) {
    this.patientLastName = patientLastName;
  }

  public String getPatientFirstName() {
    return patientFirstName;
  }

  public void setPatientFirstName(String patientFirstName) {
    this.patientFirstName = patientFirstName;
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
}
