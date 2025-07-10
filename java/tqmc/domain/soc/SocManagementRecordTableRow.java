package tqmc.domain.soc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @deprecated This class represents a Record Table Row, consisting of the requested information per
 *     record.
 */
public class SocManagementRecordTableRow {
  private String recordId;
  private Set<String> mtfNames;
  private SocRecordStatus recordStatus;
  private Map<String, SocManagementCaseTableRow> cases;
  private Boolean hasFiles = false;
  private String aliasRecordId;
  private String patientName;
  private String patientNameQuery;

  /**
   * Method to return data as a map object.
   *
   * @return map of all data.
   */
  public Map<String, Object> toMap() {
    Map<String, Object> outMap = new HashMap<>();
    outMap.put("record_id", recordId);
    outMap.put("mtf_names", mtfNames.toArray());
    outMap.put("record_status", recordStatus.getRecordStatus());
    outMap.put("has_files", hasFiles);
    ArrayList<Map<String, Object>> outCases = new ArrayList<>();
    for (SocManagementCaseTableRow row : cases.values()) {
      outCases.add(row.toMap());
    }
    outMap.put("cases", outCases);
    outMap.put("alias_record_id", this.aliasRecordId);
    outMap.put("patient_name", this.patientName);
    outMap.put("patient_name_query", this.patientNameQuery);
    return outMap;
  }

  public SocManagementRecordTableRow(
      String recordId,
      SocRecordStatus recordStatus,
      Map<String, SocManagementCaseTableRow> cases,
      String aliasRecordId,
      String patientName,
      String patientNameQuery) {
    this.recordId = recordId;
    this.mtfNames = new HashSet<String>();
    this.recordStatus = recordStatus;
    this.cases = cases;
    this.aliasRecordId = aliasRecordId;
    this.patientName = patientName;
    this.patientNameQuery = patientNameQuery;
  }

  public Map<String, SocManagementCaseTableRow> getCases() {
    return this.cases;
  }

  public void addMTFNames(List<String> namesToAdd) {
    for (String name : namesToAdd) {
      this.mtfNames.add(name);
    }
  }

  public void checkCaseFiles(boolean caseHasFiles) {
    if (!hasFiles) {
      hasFiles = caseHasFiles;
    }
  }
}
