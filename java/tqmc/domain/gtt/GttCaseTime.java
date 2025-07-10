package tqmc.domain.gtt;

import java.time.LocalDateTime;

public class GttCaseTime {
  private String caseTimeId;
  private String caseId;
  private String caseType;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private Long cumulativeTime;

  public GttCaseTime() {}

  public String getCaseTimeId() {
    return caseTimeId;
  }

  public void setCaseTimeId(String caseTimeId) {
    this.caseTimeId = caseTimeId;
  }

  public String getCaseId() {
    return caseId;
  }

  public void setCaseId(String caseId) {
    this.caseId = caseId;
  }

  public String getCaseType() {
    return caseType;
  }

  public void setCaseType(String caseType) {
    this.caseType = caseType;
  }

  public LocalDateTime getStartTime() {
    return startTime;
  }

  public void setStartTime(LocalDateTime startTime) {
    this.startTime = startTime;
  }

  public LocalDateTime getEndTime() {
    return endTime;
  }

  public void setEndTime(LocalDateTime endTime) {
    this.endTime = endTime;
  }

  public Long getCumulativeTime() {
    return cumulativeTime;
  }

  public void setCumulativeTime(Long cumulativeTime) {
    this.cumulativeTime = cumulativeTime;
  }

  public boolean isRunning() {
    return startTime != null && endTime == null;
  }
}
