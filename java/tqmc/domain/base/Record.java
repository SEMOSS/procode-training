package tqmc.domain.base;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public class Record {

  @JsonProperty("record_id")
  protected String recordId;

  @JsonProperty("updated_at")
  protected LocalDateTime updatedAt;

  public String getRecordId() {
    return recordId;
  }

  public void setRecordId(String recordId) {
    this.recordId = recordId;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
