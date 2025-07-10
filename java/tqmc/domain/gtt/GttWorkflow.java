package tqmc.domain.gtt;

import java.util.ArrayList;
import java.util.List;
import tqmc.domain.base.Workflow;

public class GttWorkflow extends Workflow {
  // CASE_ID, CASE_TYPE, GUID, IS_LATEST, RECIPIENT_STAGE,
  // RECIPIENT_USER_ID, SENDING_STAGE, SENDING_USER_ID,
  // SMSS_TIMESTAMP, STEP_STATUS
  private String caseType;
  private String recipientStage;
  private String sendingStage;
  private List<String> visibleTo = new ArrayList<>();

  public GttWorkflow() {}

  public String getCaseType() {
    return caseType;
  }

  public void setCaseType(String caseType) {
    this.caseType = caseType;
  }

  public String getRecipientStage() {
    return recipientStage;
  }

  public void setRecipientStage(String recipientStage) {
    this.recipientStage = recipientStage;
  }

  public String getSendingStage() {
    return sendingStage;
  }

  public void setSendingStage(String sendingStage) {
    this.sendingStage = sendingStage;
  }

  public List<String> getVisibleTo() {
    return visibleTo;
  }

  public void setVisibleTo(List<String> visibleTo) {
    this.visibleTo = visibleTo;
  }
}
