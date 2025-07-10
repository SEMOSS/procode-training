package tqmc.domain.qu.base;

import com.fasterxml.jackson.annotation.JsonProperty;

public class QuRecordPayload {

  @JsonProperty("record")
  private QuRecord record;

  public QuRecord getRecord() {
    return record;
  }

  public void setRecord(QuRecord record) {
    this.record = record;
  }
}
