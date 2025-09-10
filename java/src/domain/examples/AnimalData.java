package domain.examples;

public class AnimalData {
  private int animal_id;
  private String animal_type;
  private String animal_name;
  private String date_of_birth;

  public AnimalData(int animal_id, String animal_type, String animal_name, String date_of_birth) {
    this.animal_id = animal_id;
    this.animal_type = animal_type;
    this.animal_name = animal_name;
    this.date_of_birth = date_of_birth;
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

  public String getDateOfBirth() {
    return date_of_birth;
  }

  public void setDateOfBirth(String dateOfBirth) {
    this.date_of_birth = dateOfBirth;
  }

  public String getType() {
    return animal_type;
  }

  public void setType(String type) {
    this.animal_type = type;
  }
}
