package tqmc.domain.qu.base;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tqmc.domain.base.Record;
import tqmc.domain.base.RecordFile;

public class QuRecord extends Record {

  @JsonProperty("case_length_days")
  private String caseLengthDays;

  @JsonProperty("alias_record_id")
  private String aliasRecordId;

  @JsonProperty("claim_number")
  private String claimNumber;

  @JsonProperty("received_at")
  private String receivedAt;

  @JsonProperty("requested_at")
  private String requestedAt;

  @JsonProperty("patient_first_name")
  private String patientFirstName;

  @JsonProperty("patient_last_name")
  private String patientLastName;

  @JsonProperty("missing_requested_at")
  private String missingRequestedAt;

  @JsonProperty("missing_received_at")
  private String missingReceivedAt;

  @JsonProperty("files")
  private List<RecordFile> files;

  public String getCaseLengthDays() {
    return caseLengthDays;
  }

  public void setCaseLengthDays(String caseLengthDays) {
    this.caseLengthDays = caseLengthDays;
  }

  public String getReceivedAt() {
    return receivedAt;
  }

  public void setReceivedAt(String receivedAt) {
    this.receivedAt = receivedAt;
  }

  public String getRequestedAt() {
    return requestedAt;
  }

  public void setRequestedAt(String requestedAt) {
    this.requestedAt = requestedAt;
  }

  public String getPatientFirstName() {
    return patientFirstName;
  }

  public void setPatientFirstName(String patientFirstName) {
    this.patientFirstName = patientFirstName;
  }

  public String getPatientLastName() {
    return patientLastName;
  }

  public void setPatientLastName(String patientLastName) {
    this.patientLastName = patientLastName;
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

  public String getMissingRequestedAt() {
    return missingRequestedAt;
  }

  public void setMissingRequestedAt(String missingRequestedAt) {
    this.missingRequestedAt = missingRequestedAt;
  }

  public String getMissingReceivedAt() {
    return missingReceivedAt;
  }

  public void setMissingReceivedAt(String missingReceivedAt) {
    this.missingReceivedAt = missingReceivedAt;
  }

  public String getClaimNumber() {
    return claimNumber;
  }

  public void setClaimNumber(String claimNumber) {
    this.claimNumber = claimNumber;
  }
}
