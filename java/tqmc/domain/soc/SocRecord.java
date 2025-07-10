package tqmc.domain.soc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import tqmc.domain.base.Provider;
import tqmc.domain.base.Record;
import tqmc.domain.base.RecordFile;

public class SocRecord extends Record {

  @JsonProperty("mtf_list")
  private List<String> mtfList;

  @JsonProperty("providers")
  private List<Provider> providers;

  @JsonProperty("alias_record_id")
  private String aliasRecordId;

  @JsonProperty("patient_last_name")
  private String patientLastName;

  @JsonProperty("patient_first_name")
  private String patientFirstName;

  private List<RecordFile> files;

  @JsonProperty("due_date_map")
  private Map<String, SocSpecialtyIdMap> dueDateMap;

  @JsonProperty("nurse_due_date_review")
  private LocalDate nurseDueDateReview;

  @JsonProperty("nurse_due_date_dha")
  private LocalDate nurseDueDateDHA;

  public List<String> getMtfList() {
    return this.mtfList;
  }

  public void setMtfList(List<String> input) {
    this.mtfList = input;
  }

  public List<Provider> getProviders() {
    return this.providers;
  }

  public void setProviders(List<Provider> input) {
    this.providers = input;
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

  public Map<String, SocSpecialtyIdMap> getDueDateMap() {
    return dueDateMap;
  }

  public void setDueDateMap(Map<String, SocSpecialtyIdMap> dueDateMap) {
    this.dueDateMap = dueDateMap;
  }

  public LocalDate getNurseDueDateReview() {
    return nurseDueDateReview;
  }

  public void setNurseDueDateReview(LocalDate nurseDueDateReview) {
    this.nurseDueDateReview = nurseDueDateReview;
  }

  public LocalDate getNurseDueDateDHA() {
    return nurseDueDateDHA;
  }

  public void setNurseDueDateDHA(LocalDate nurseDueDateDHA) {
    this.nurseDueDateDHA = nurseDueDateDHA;
  }

  public static class SocSpecialtyIdMap {

    @JsonProperty("provider_count")
    private Integer providerCount;

    @JsonProperty("due_date_review")
    private LocalDate dueDateReview;

    @JsonProperty("due_date_dha")
    private LocalDate dueDateDHA;

    @JsonProperty("specialty_id")
    private String specialtyId;

    public Integer getProviderCount() {
      return providerCount;
    }

    public void setProviderCount(Integer providerCount) {
      this.providerCount = providerCount;
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

    public String getSpecialtyId() {
      return specialtyId;
    }

    public void setSpecialtyId(String specialtyId) {
      this.specialtyId = specialtyId;
    }
  }
}
