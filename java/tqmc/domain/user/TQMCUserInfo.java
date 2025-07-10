package tqmc.domain.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class TQMCUserInfo {
  @JsonProperty("user_id")
  private String userId;

  @JsonProperty("email")
  private String email;

  @JsonProperty("first_name")
  private String firstName;

  @JsonProperty("last_name")
  private String lastName;

  @JsonProperty("phone")
  private String phone;

  @JsonProperty("npi")
  private String npi;

  @JsonProperty("is_active")
  private boolean isActive;

  @JsonProperty("created_at")
  private LocalDateTime createdAt;

  @JsonProperty("updated_at")
  private LocalDateTime updatedAt;

  @JsonProperty("role")
  private String role;

  @JsonProperty("products")
  private Set<String> products = new HashSet<>();

  @JsonProperty("specialty_ids")
  private Set<String> specialtyIds = new HashSet<>();

  public TQMCUserInfo() {}

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getNpi() {
    return npi;
  }

  public void setNpi(String npi) {
    this.npi = npi;
  }

  public boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(boolean isActive) {
    this.isActive = isActive;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public Set<String> getProducts() {
    return products;
  }

  public void setProducts(Set<String> products) {
    this.products = products;
  }

  public Set<String> getSpecialtyIds() {
    return this.specialtyIds;
  }

  public void setSpecialtyIds(Set<String> specialtyIds) {
    this.specialtyIds = specialtyIds;
  }
}
