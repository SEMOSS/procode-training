package tqmc.domain.base;

import java.time.LocalDateTime;
import java.util.Objects;

public class CaseDetails {
  private String product;
  private String caseId;
  private String specialtyId;
  private String caseType;
  private String recipientUserId;
  private Boolean hasFiles;
  private LocalDateTime updatedAt;

  public CaseDetails() {}

  public String getProduct() {
    return this.product;
  }

  public void setProduct(String product) {
    this.product = product;
  }

  public String getCaseId() {
    return this.caseId;
  }

  public void setCaseId(String caseId) {
    this.caseId = caseId;
  }

  public String getSpecialtyId() {
    return this.specialtyId;
  }

  public void setSpecialtyId(String specialtyId) {
    this.specialtyId = specialtyId;
  }

  public String getCaseType() {
    return this.caseType;
  }

  public void setCaseType(String caseType) {
    this.caseType = caseType;
  }

  public String getRecipientUserId() {
    return this.recipientUserId;
  }

  public void setRecipientUserId(String recipientUserId) {
    this.recipientUserId = recipientUserId;
  }

  public LocalDateTime getUpdatedAt() {
    return this.updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Boolean getHasFiles() {
    return this.hasFiles;
  }

  public void setHasFiles(Boolean hasFiles) {
    this.hasFiles = hasFiles;
  }

  @Override
  public int hashCode() {
    return Objects.hash(caseId, product);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof CaseDetails)) {
      return false;
    }
    CaseDetails other = (CaseDetails) obj;
    return Objects.equals(caseId, other.caseId) && Objects.equals(product, other.product);
  }
}
