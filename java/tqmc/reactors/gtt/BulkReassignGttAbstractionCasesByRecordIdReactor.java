package tqmc.reactors.gtt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class BulkReassignGttAbstractionCasesByRecordIdReactor extends AbstractTQMCReactor {

  private static final Logger LOGGER =
      LogManager.getLogger(BulkReassignGttAbstractionCasesByRecordIdReactor.class);

  public BulkReassignGttAbstractionCasesByRecordIdReactor() {
    this.keysToGet =
        new String[] {
          "recordIds", TQMCConstants.NEW_ASSIGNEE_USER_ID, TQMCConstants.NEW_ASSIGNEE_USER_ID_2
        };
    this.keyRequired = new int[] {1, 1, 1};
  }

  private static final String getCasesQuery =
      "SELECT r.record_id, "
          + "CASE WHEN gcc.abs_1_case_id = gw.case_id "
          + "THEN gw.case_id ELSE NULL END AS abs_1_case_id, "
          + "CASE WHEN gcc.abs_2_case_id = gw.case_id "
          + "THEN gw.case_id ELSE NULL END AS abs_2_case_id, "
          + "gw.recipient_user_id, "
          + "gw.step_status, "
          + "FROM TQMC_RECORD r "
          + "INNER JOIN GTT_CONSENSUS_CASE gcc ON r.record_id = gcc.record_id "
          + "INNER JOIN GTT_WORKFLOW gw ON (gcc.abs_1_case_id = gw.case_id OR gcc.abs_2_case_id = gw.case_id) "
          + "WHERE r.record_id IN (%s) "
          + "AND gw.is_latest = 1 "
          + "AND gw.case_type = 'abstraction' ";

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);

    if (!hasProductManagementPermission(TQMCConstants.GTT)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    String placeholders = String.join(",", Collections.nCopies(payload.getRecordIds().size(), "?"));
    String queryWithPlaceholders = String.format(getCasesQuery, placeholders);

    Map<String, Map<String, String>> recordsMap = new HashMap<>();

    try (PreparedStatement ps = con.prepareStatement(queryWithPlaceholders)) {
      int parameterIndex = 1;
      for (String recordId : payload.getRecordIds()) {
        ps.setString(parameterIndex++, recordId);
      }
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        String abs1CaseId = rs.getString("ABS_1_CASE_ID");
        String abs2CaseId = rs.getString("ABS_2_CASE_ID");
        String recordId = rs.getString("RECORD_ID");
        String recipientUserId = rs.getString("RECIPIENT_USER_ID");
        String stepStatus = rs.getString("STEP_STATUS");
        if (!recordsMap.containsKey(recordId)) {
          recordsMap.put(recordId, new HashMap<String, String>());
        }
        Map<String, String> rMap = recordsMap.get(recordId);
        if ((abs1CaseId != null && abs2CaseId != null)
            || (abs1CaseId == null && abs2CaseId == null)) {
          throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, "Error in SQL query");
        }
        if (abs1CaseId != null) {
          rMap.put("abs1CaseId", abs1CaseId);
          rMap.put("abs1UserId", recipientUserId);
          rMap.put("abs1StepStatus", stepStatus);
        }
        if (abs2CaseId != null) {
          rMap.put("abs2CaseId", abs2CaseId);
          rMap.put("abs2UserId", recipientUserId);
          rMap.put("abs2StepStatus", stepStatus);
        }
      }
    }

    for (String recordId : recordsMap.keySet()) {
      Map<String, String> rMap = recordsMap.get(recordId);
      if (rMap.get("abs2UserId").equals(payload.getNewAssigneeUserId())) {
        LOGGER.warn(
            "Abstractor "
                + payload.getNewAssigneeUserId()
                + " is already assigned on case "
                + rMap.get("abs2CaseId")
                + ". Attempting to swap");
        String tempUser = payload.getNewAssigneeUserId();
        payload.setNewAssigneeUserId(payload.getNewAssigneeUserId2());
        payload.setNewAssigneeUserId2(tempUser);
      }
      if (rMap.get("abs1UserId").equals(payload.getNewAssigneeUserId2())) {
        LOGGER.warn(
            "Abstractor "
                + payload.getNewAssigneeUserId2()
                + " is already assigned on case "
                + rMap.get("abs1CaseId")
                + ". Attempting to swap");
        String tempUser = payload.getNewAssigneeUserId();
        payload.setNewAssigneeUserId(payload.getNewAssigneeUserId2());
        payload.setNewAssigneeUserId2(tempUser);
      }
      boolean abs1Assigned = false;
      boolean abs2Assigned = false;
      if (rMap.get("abs1StepStatus").equals("completed")) {
        if (rMap.get("abs1UserId").equals(payload.getNewAssigneeUserId())) {
          LOGGER.warn(
              "Abstractor "
                  + payload.getNewAssigneeUserId()
                  + " already has a completed case on the record.");
          abs1Assigned = true;
        } else {
          throw new TQMCException(
              ErrorCode.BAD_REQUEST,
              "Record " + recordId + " already has completed cases by " + rMap.get("abs1UserId"));
        }
      }
      if (rMap.get("abs2StepStatus").equals("completed")) {
        if (rMap.get("abs2UserId").equals(payload.getNewAssigneeUserId2())) {
          LOGGER.warn(
              "Abstractor "
                  + payload.getNewAssigneeUserId2()
                  + " already has a completed case on the record.");
          abs2Assigned = true;
        } else {
          throw new TQMCException(
              ErrorCode.BAD_REQUEST,
              "Record " + recordId + " already has completed cases by " + rMap.get("abs2UserId"));
        }
      }
      if (rMap.get("abs1UserId").equals(payload.getNewAssigneeUserId()) || abs1Assigned) {
        LOGGER.warn(
            "Skipping reassign - abstractor "
                + payload.getNewAssigneeUserId()
                + " already on case "
                + rMap.get("abs1CaseId"));
      } else {
        try {
          TQMCHelper.checkReassignGttPermissions(
              con,
              rMap.get("abs1CaseId"),
              TQMCConstants.GTT_CASE_TYPE_ABSTRACTOR,
              payload.getNewAssigneeUserId());
        } catch (TQMCException e) {
          throw new TQMCException(
              ErrorCode.BAD_REQUEST, e.toString().split(":")[1] + " on record " + recordId);
        }

        TQMCHelper.onReassignUpdateGttWorkflowAndEditCases(
            con,
            rMap.get("abs1CaseId"),
            TQMCConstants.GTT_CASE_TYPE_ABSTRACTOR,
            userId,
            payload.getNewAssigneeUserId());
        rMap.put("abs1UserId", payload.getNewAssigneeUserId());
      }

      if (rMap.get("abs2UserId").equals(payload.getNewAssigneeUserId2()) || abs2Assigned) {
        LOGGER.warn(
            "Skipping reassign - abstractor "
                + payload.getNewAssigneeUserId2()
                + " already on case "
                + rMap.get("abs2CaseId"));
      } else {
        try {
          TQMCHelper.checkReassignGttPermissions(
              con,
              rMap.get("abs2CaseId"),
              TQMCConstants.GTT_CASE_TYPE_ABSTRACTOR,
              payload.getNewAssigneeUserId2());
        } catch (TQMCException e) {
          throw new TQMCException(
              ErrorCode.BAD_REQUEST, e.toString().split(":")[1] + " on record " + recordId);
        }

        TQMCHelper.onReassignUpdateGttWorkflowAndEditCases(
            con,
            rMap.get("abs2CaseId"),
            TQMCConstants.GTT_CASE_TYPE_ABSTRACTOR,
            userId,
            payload.getNewAssigneeUserId2());
        rMap.put("abs2UserId", payload.getNewAssigneeUserId2());
      }
    }
    return new NounMetadata(recordsMap, PixelDataType.MAP);
  }

  public static class Payload {
    private Set<String> recordIds;
    private String newAssigneeUserId;
    private String newAssigneeUserId2;

    public Set<String> getRecordIds() {
      return recordIds;
    }

    public void setRecordIds(Set<String> recordIds) {
      this.recordIds = recordIds;
    }

    public String getNewAssigneeUserId() {
      return newAssigneeUserId;
    }

    public void setNewAssigneeUserId(String newAssigneeUserId) {
      this.newAssigneeUserId = newAssigneeUserId;
    }

    public String getNewAssigneeUserId2() {
      return newAssigneeUserId2;
    }

    public void setNewAssigneeUserId2(String newAssigneeUserId2) {
      this.newAssigneeUserId2 = newAssigneeUserId2;
    }
  }
}
