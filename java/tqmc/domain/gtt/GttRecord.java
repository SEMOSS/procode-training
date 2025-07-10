package tqmc.domain.gtt;

import java.time.LocalDate;
import java.time.LocalDateTime;
import tqmc.domain.base.Record;

public class GttRecord extends Record {

  private String dmisId;
  private String mtfName;
  private String dhn;
  private int los;
  private LocalDate dischargeDate;
  private LocalDate completedDate;
  private String gettName;
  private String gttBucket;
  private String gttName;
  private String ghctName;
  private Boolean presentOnAdmission;
  private String eventDescription;
  private String note;

  public GttRecord() {}

  public String getRecordId() {
    return super.recordId;
  }

  public void setRecordId(String recordId) {
    super.recordId = recordId;
  }

  public String getDmisId() {
    return dmisId;
  }

  public void setDmisId(String dmisId) {
    this.dmisId = dmisId;
  }

  public String getMtfName() {
    return mtfName;
  }

  public void setMtfName(String mtfName) {
    this.mtfName = mtfName;
  }

  public String getDhn() {
    return dhn;
  }

  public void setDhn(String dhn) {
    this.dhn = dhn;
  }

  public int getLos() {
    return los;
  }

  public void setLos(int los) {
    this.los = los;
  }

  public LocalDate getDischargeDate() {
    return dischargeDate;
  }

  public void setDischargeDate(LocalDate dischargeDate) {
    this.dischargeDate = dischargeDate;
  }

  public LocalDate getCompletedDate() {
    return completedDate;
  }

  public void setCompletedDate(LocalDate completedDate) {
    this.completedDate = completedDate;
  }

  public String getGettName() {
    return gettName;
  }

  public void setGettName(String gettName) {
    this.gettName = gettName;
  }

  public String getGttBucket() {
    return gttBucket;
  }

  public void setGttBucket(String gttBucket) {
    this.gttBucket = gttBucket;
  }

  public String getGttName() {
    return gttName;
  }

  public void setGttName(String gttName) {
    this.gttName = gttName;
  }

  public String getGhctName() {
    return ghctName;
  }

  public void setGhctName(String ghctName) {
    this.ghctName = ghctName;
  }

  public Boolean getPresentOnAdmission() {
    return presentOnAdmission;
  }

  public void setPresentOnAdmission(Boolean presentOnAdmission) {
    this.presentOnAdmission = presentOnAdmission;
  }

  public String getEventDescription() {
    return eventDescription;
  }

  public void setEventDescription(String eventDescription) {
    this.eventDescription = eventDescription;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public LocalDateTime getUpdatedAt() {
    return super.updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    super.updatedAt = updatedAt;
  }
}
