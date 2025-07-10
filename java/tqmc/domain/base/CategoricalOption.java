package tqmc.domain.base;

public class CategoricalOption extends Option {
  private String category;

  public CategoricalOption() {}

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }
}
