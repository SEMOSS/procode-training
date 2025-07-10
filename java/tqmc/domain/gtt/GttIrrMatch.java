package tqmc.domain.gtt;

public class GttIrrMatch {
  private String irrMatchId;
  private String consensusCaseId;
  private String abstractorOneRecordCaseEventId;
  private String abstractorTwoRecordCaseEventId;
  private String alternateRecordCaseEventId;
  private String selectedCaseEventId;
  private Boolean isDeleted;
  private Boolean isMatch;

  public GttIrrMatch() {}

  public String getIrrMatchId() {
    return irrMatchId;
  }

  public void setIrrMatchId(String irrMatchId) {
    this.irrMatchId = irrMatchId;
  }

  public String getConsensusCaseId() {
    return consensusCaseId;
  }

  public void setConsensusCaseId(String consensusCaseId) {
    this.consensusCaseId = consensusCaseId;
  }

  public String getAbstractorOneRecordCaseEventId() {
    return abstractorOneRecordCaseEventId;
  }

  public void setAbstractorOneRecordCaseEventId(String abstractorOneRecordCaseEventId) {
    this.abstractorOneRecordCaseEventId = abstractorOneRecordCaseEventId;
  }

  public String getAbstractorTwoRecordCaseEventId() {
    return abstractorTwoRecordCaseEventId;
  }

  public void setAbstractorTwoRecordCaseEventId(String abstractorTwoRecordCaseEventId) {
    this.abstractorTwoRecordCaseEventId = abstractorTwoRecordCaseEventId;
  }

  public String getAlternateRecordCaseEventId() {
    return alternateRecordCaseEventId;
  }

  public void setAlternateRecordCaseEventId(String alternateRecordCaseEventId) {
    this.alternateRecordCaseEventId = alternateRecordCaseEventId;
  }

  public String getSelectedCaseEventId() {
    return selectedCaseEventId;
  }

  public void setSelectedCaseEventId(String selectedCaseEventId) {
    this.selectedCaseEventId = selectedCaseEventId;
  }

  public Boolean getIsMatch() {
    return isMatch;
  }

  public Boolean getIsDeleted() {
    return isDeleted;
  }

  public void setIsDeleted(Boolean isDeleted) {
    this.isDeleted = isDeleted;
  }

  public void setIsMatch(Boolean isMatch) {
    this.isMatch = isMatch;
  }
}
