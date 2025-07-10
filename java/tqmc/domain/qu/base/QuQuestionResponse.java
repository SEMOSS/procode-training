package tqmc.domain.qu.base;

import com.fasterxml.jackson.annotation.JsonProperty;
import tqmc.domain.base.CaseQuestion;

public class QuQuestionResponse extends CaseQuestion {

  @JsonProperty("question_response_id")
  private String questionResponseId;

  @JsonProperty("question_template_id")
  private String questionTemplateId;

  @JsonProperty("response_template_id")
  private String responseTemplateId;

  public String getQuestionResponseId() {
    return questionResponseId;
  }

  public void setQuestionResponseId(String questionResponseId) {
    this.questionResponseId = questionResponseId;
  }

  public String getQuestionTemplateId() {
    return questionTemplateId;
  }

  public void setQuestionTemplateId(String questionTemplateId) {
    this.questionTemplateId = questionTemplateId;
  }

  public String getResponseTemplateId() {
    return responseTemplateId;
  }

  public void setResponseTemplateId(String responseTemplateId) {
    this.responseTemplateId = responseTemplateId;
  }
}
