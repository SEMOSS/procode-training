package tqmc.domain.base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Filter {
  private String field;
  private Comparison operator;
  private String value;

  public Filter() {}

  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  public Comparison getOperator() {
    return operator;
  }

  public void setOperator(Comparison operator) {
    this.operator = operator;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public static enum Comparison {
    GREATER_THAN(">", " > ", " > NULL"),
    LESS_THAN("<", " < ", " < NULL"),
    GREATER_THAN_EQUAL(">=", " >= ", " IS NULL"),
    LESS_THAN_EQUAL("<=", " <= ", " IS NULL"),

    EQUAL("=", "=", " IS NULL"),
    NOT_EQUAL("!=", "<>", " IS NOT NULL"),
    LIKE("like", " ilike ", " IS NULL");

    private final String symbol;
    private final String sql;
    private final String nullSql;

    private Comparison(String symbol) {
      this.symbol = symbol;
      this.sql = symbol;
      this.nullSql = symbol;
    }

    private Comparison(String symbol, String sql, String nullSql) {
      this.symbol = symbol;
      this.sql = sql;
      this.nullSql = nullSql;
    }

    @JsonValue
    public String getSymbol() {
      return symbol;
    }

    public String getSql() {
      return sql;
    }

    public String getNullSql() {
      return nullSql;
    }
  }
}
