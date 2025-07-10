package tqmc.domain.base;

public class MTFData {
  private String mtf_name;
  private String dmis_id;
  private String alias_mtf_name;

  public MTFData(String name, String id, String alias) {
    this.mtf_name = name;
    this.dmis_id = id;
    this.alias_mtf_name = alias;
  }

  public String getName() {
    return mtf_name;
  }

  public void setName(String name) {
    this.mtf_name = name;
  }

  public String getId() {
    return dmis_id;
  }

  public void setId(String id) {
    this.dmis_id = id;
  }

  public String getAlias() {
    return alias_mtf_name;
  }

  public void setAlias(String alias) {
    this.alias_mtf_name = alias;
  }
}
