package tqmc.domain.soc;

import java.util.HashMap;
import java.util.Map;
import tqmc.domain.base.CaseStatus;

/** This class represents a Case Table Row, consisting of all requested information per case. */
public class SocManagementCaseTableRow {

  private String aliasRecordId;
  private String patientName;
  private String recordId;
  private String caseId;
  private String nurseReviewId;
  private SocCaseType caseType;
  private CaseStatus caseStatus;
  private String specialtyId;
  private String specialtyName;
  private Boolean caseHasFiles;
  private String completedAtDate;
  private String userId;
  private String assignedDate;
  private String dueDateReview;
  private String dueDateDHA;
  private String recordStatus;

  /**
   * Method to return data as a map object.
   *
   * @return map of all data.
   */
  public Map<String, Object> toMap() {
    Map<String, Object> outMap = new HashMap<>();
    outMap.put("alias_record_id", aliasRecordId);
    outMap.put("patient_name", patientName);
    outMap.put("record_id", recordId);
    outMap.put("case_id", caseId);
    outMap.put("nurse_review_id", nurseReviewId);
    outMap.put("case_type", caseType != null ? caseType.getCaseType() : null);
    outMap.put("case_status", caseStatus != null ? caseStatus.getCaseStatus() : null);
    outMap.put("specialty_id", specialtyId);
    outMap.put("specialty_name", specialtyName);
    outMap.put("has_files", caseHasFiles);
    outMap.put("completed_at", completedAtDate);
    outMap.put("user_id", userId);
    outMap.put("assigned_date", assignedDate);
    outMap.put("due_date_review", dueDateReview);
    outMap.put("due_date_dha", dueDateDHA);
    outMap.put("record_status", recordStatus);
    return outMap;
  }

  public SocManagementCaseTableRow(
      String aliasRecordId,
      String patientName,
      String recordId,
      String caseId,
      String nurseReviewId,
      SocCaseType caseType,
      CaseStatus caseStatus,
      String specialtyId,
      String specialtyName,
      Boolean caseHasFiles,
      String completedAtDate,
      String userId,
      String assignedDate,
      String dueDateReview,
      String dueDateDHA,
      String recordStatus) {
    this.aliasRecordId = aliasRecordId;
    this.patientName = patientName;
    this.recordId = recordId;
    this.caseId = caseId;
    this.nurseReviewId = nurseReviewId;
    this.caseType = caseType;
    this.caseStatus = caseStatus;
    this.specialtyId = specialtyId;
    this.specialtyName = specialtyName;
    this.caseHasFiles = caseHasFiles;
    this.completedAtDate = completedAtDate;
    this.userId = userId;
    this.assignedDate = assignedDate;
    this.dueDateReview = dueDateReview;
    this.dueDateDHA = dueDateDHA;
    this.recordStatus = recordStatus;
  }
}
