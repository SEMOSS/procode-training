package tqmc.domain.base;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Specialty {

  @JsonProperty("specialty_name")
  String specialtyName;

  @JsonProperty("subspecialty_name")
  String subspecialtyName;

  @JsonProperty("specialty_id")
  String specialtyId;

  public String getSpecialtyName() {
    return this.specialtyName;
  }

  public void setSpecialtyName(String specialtyName) {
    this.specialtyName = specialtyName;
  }

  public String getSubspecialtyName() {
    return this.subspecialtyName;
  }

  public void setSubspecialtyName(String subspecialtyName) {
    this.subspecialtyName = subspecialtyName;
  }

  public String getSpecialtyId() {
    return this.specialtyId;
  }

  public void setSpecialtyId(String specialtyId) {
    this.specialtyId = specialtyId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(specialtyId, specialtyName, subspecialtyName);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Specialty)) {
      return false;
    }
    Specialty other = (Specialty) obj;
    return Objects.equals(specialtyId, other.specialtyId)
        && Objects.equals(specialtyName, other.specialtyName)
        && Objects.equals(subspecialtyName, other.subspecialtyName);
  }

  public Map<String, String> toMap() {
    Map<String, String> outMap = new HashMap<>();
    outMap.put("specialty_id", this.specialtyId);
    outMap.put("specialty_name", this.specialtyName);
    outMap.put("subspecialty_name", this.subspecialtyName);
    return outMap;
  }
}
