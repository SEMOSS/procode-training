package tqmc.domain.base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Sort {
  private String field;
  private Direction sort;
  private boolean caseInsensitive;

  public Sort() {}

  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  public Direction getSort() {
    return sort;
  }

  public void setSort(Direction sort) {
    this.sort = sort;
  }

  public boolean getCaseInsensitive() {
    return this.caseInsensitive;
  }

  public void setCaseInsensitive(boolean caseInsensitive) {
    this.caseInsensitive = caseInsensitive;
  }

  public static enum Direction {
    ASC,
    DESC
  }
}
