package examples.domain.animal;

public class AnimalData {
  private String animalId;
  private String animalType;
  private String animalName;
  private String dateOfBirth;

  public AnimalData(String animalId, String animalType, String animalName, String dateOfBirth) {
    this.animalId = animalId;
    this.animalType = animalType;
    this.animalName = animalName;
    this.dateOfBirth = dateOfBirth;
  }

  public String getAnimalName() {
    return animalName;
  }

  public void setAnimalName(String animalName) {
    this.animalName = animalName;
  }

  public String getAnimalId() {
    return animalId;
  }

  public void setAnimalId(String animalId) {
    this.animalId = animalId;
  }

  public String getDateOfBirth() {
    return dateOfBirth;
  }

  public void setDateOfBirth(String dateOfBirth) {
    this.dateOfBirth = dateOfBirth;
  }

  public String getAnimalType() {
    return animalType;
  }

  public void setAnimalType(String animalType) {
    this.animalType = animalType;
  }
}
