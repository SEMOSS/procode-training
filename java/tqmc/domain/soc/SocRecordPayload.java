package tqmc.domain.soc;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SocRecordPayload {
  @JsonProperty("record")
  private SocRecord record;

  public SocRecordPayload() {}

  public SocRecord getRecord() {
    return this.record;
  }

  public void setRecord(SocRecord input) {
    this.record = input;
  }
}
