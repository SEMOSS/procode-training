package tqmc.domain.base;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RecordFile {

  @JsonProperty("record_file_id")
  private String recordFileId;

  @JsonIgnore private String recordId;

  @JsonIgnore private List<String> caseIds = new ArrayList<>();

  @JsonProperty("file_name")
  private String fileName;

  private String category;

  @JsonProperty("show_nurse_review")
  private Boolean showNurseReview;

  @JsonProperty("specialties")
  private List<String> specialtyIdList = new ArrayList<>();

  public RecordFile() {}

  public List<String> getCaseIds() {
    return caseIds;
  }

  public void setCaseIds(List<String> caseIds) {
    this.caseIds = caseIds;
  }

  public void addCaseId(String caseId) {
    this.caseIds.add(caseId);
  }

  public Boolean getShowNurseReview() {
    return showNurseReview;
  }

  public void setShowNurseReview(Boolean showNurseReview) {
    this.showNurseReview = showNurseReview;
  }

  public List<String> getSpecialtyIdList() {
    return specialtyIdList;
  }

  public void addSpecialtyId(String specialtyId) {
    this.specialtyIdList.add(specialtyId);
  }

  public void setSpecialtyIdList(List<String> specialtyIdList) {
    this.specialtyIdList = specialtyIdList;
  }

  public String getRecordFileId() {
    return recordFileId;
  }

  public void setRecordFileId(String recordFileId) {
    this.recordFileId = recordFileId;
  }

  public String getRecordId() {
    return recordId;
  }

  public void setRecordId(String recordId) {
    this.recordId = recordId;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("record_file_id", this.recordFileId);
    map.put("file_name", this.fileName);
    map.put("category", this.category);
    map.put("show_nurse_review", this.showNurseReview);
    map.put("specialties", this.specialtyIdList);
    //    map.put("case_ids", this.caseIds);
    //    map.put("record_id", this.recordId);
    return map;
  }

  @Override
  public int hashCode() {
    return Objects.hash(recordFileId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof RecordFile)) {
      return false;
    }
    RecordFile other = (RecordFile) obj;
    return Objects.equals(recordFileId, other.recordFileId);
  }
}
