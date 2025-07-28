package domain.examples;

public class AnimalData {
  private int animal_id;
  private String animal_type;
  private String animal_name;

  public AnimalData(int animal_id, String animal_type, String animal_name) {
    this.animal_id = animal_id;
    this.animal_type = animal_type;
    this.animal_name = animal_name;
  }

  public String getName() {
    return animal_name;
  }

  public void setName(String name) {
    this.animal_name = name;
  }

  public int getId() {
    return animal_id;
  }

  public void setId(int id) {
    this.animal_id = id;
  }

  public String getType() {
    return animal_type;
  }

  public void setAlias(String type) {
    this.animal_type = type;
  }
}
