package tqmc.domain.mn;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import tqmc.domain.base.CaseQuestion;

public class MnCaseQuestion extends CaseQuestion {
  @JsonProperty("case_question_id")
  private String caseQuestionId;

  private String question;
  private int number;
  private String response;
  private String rationale;
  private String reference;
  private Boolean inputted;

  public MnCaseQuestion() {}

  public String getCaseQuestionId() {
    return caseQuestionId;
  }

  public void setCaseQuestionId(String caseQuestionId) {
    this.caseQuestionId = caseQuestionId;
  }

  public String getQuestion() {
    return question;
  }

  public void setQuestion(String question) {
    this.question = question;
  }

  public String getResponse() {
    return response;
  }

  public void setResponse(String response) {
    this.response = response;
  }

  public String getRationale() {
    return rationale;
  }

  public void setRationale(String rationale) {
    this.rationale = rationale;
  }

  public String getReference() {
    return reference;
  }

  public void setReference(String reference) {
    this.reference = reference;
  }

  public Boolean getInputted() {
    return this.inputted;
  }

  public void setInputted(Boolean inputted) {
    this.inputted = inputted;
  }

  public Map<String, String> toMap() {
    Map<String, String> m = new HashMap<>();
    m.put("case_question_id", this.caseQuestionId);
    m.put("question", this.question);
    m.put("response", this.response);
    m.put("rationale", this.rationale);
    m.put("reference", this.reference);
    return m;
  }

  public int getNumber() {
    return number;
  }

  public void setNumber(int number) {
    this.number = number;
  }
}
