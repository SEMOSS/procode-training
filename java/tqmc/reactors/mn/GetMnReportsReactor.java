package tqmc.reactors.mn;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import prerna.om.InsightFile;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.PixelOperationType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.AssetUtility;
import prerna.util.Utility;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;

public class GetMnReportsReactor extends AbstractTQMCReactor {

  private static final String QUERY_TEMPLATE =
      "SELECT"
          + " COALESCE(mnr.ALIAS_RECORD_ID, '') AS ALIAS_RECORD_ID,"
          + " mnc.DUE_DATE_REVIEW, mnc.DUE_DATE_DHA, "
          + " COALESCE(mnc.USER_ID, '') AS PEER_REVIEWER, "
          + " COALESCE(CASE "
          + "  WHEN mnw.step_status = 'completed' THEN mnc.RECOMMENDATION_RESPONSE "
          + "  ELSE '' "
          + " END, '') AS RECOMMENDATION_RESPONSE, "
          + " mnc.CREATED_AT as CREATED_AT, "
          + " mnc.ASSIGNED_AT as ASSIGNED_AT, "
          + " mnc.ATTESTED_AT as ATTESTED_AT, "
          + " COALESCE(ts.SPECIALTY_NAME, '') AS SPECIALTY_NAME, "
          + " COALESCE(ts.SUBSPECIALTY_NAME, '') AS SUBSPECIALTY_NAME, "
          + " COALESCE(mncc.CARE_CONTRACTOR_NAME, '') AS CARE_CONTRACTOR_NAME, "
          + " COALESCE(mat.APPEAL_TYPE_NAME, '') AS APPEAL_TYPE_NAME, "
          + "FROM "
          + " MN_RECORD mnr "
          + "JOIN MN_CASE mnc ON "
          + " mnr.RECORD_ID = mnc.RECORD_ID AND mnc.SUBMISSION_GUID IS NULL AND mnc.DELETED_AT IS NULL "
          + "LEFT JOIN MN_APPEAL_TYPE mat ON "
          + " mnc.APPEAL_TYPE_ID = mat.APPEAL_TYPE_ID "
          + "LEFT JOIN MN_CARE_CONTRACTOR mncc ON "
          + " mncc.CARE_CONTRACTOR_ID = mnr.CARE_CONTRACTOR_ID "
          + "LEFT JOIN TQMC_SPECIALTY ts ON "
          + " ts.SPECIALTY_ID = mnc.SPECIALTY_ID "
          + "LEFT JOIN MN_WORKFLOW mnw ON "
          + " mnw.CASE_ID = mnc.CASE_ID WHERE mnw.IS_LATEST = '1' "
          + "ORDER BY mnc.DUE_DATE_DHA ASC, mnr.ALIAS_RECORD_ID ASC";

  private static final String[] EXPORT_HEADERS =
      new String[] {
        "Record ID",
        "Due Date Review",
        "Due Date DHA",
        "Peer Reviewer",
        "Reconsideration Response",
        "Created At",
        "Assigned At",
        "Attested At",
        "Specialty Name",
        "Subspecialty Name",
        "Care Contractor Name",
        "Appeal Type Name"
      };

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    if (!(hasProductPermission(TQMCConstants.MN) && hasRole(TQMCConstants.MANAGEMENT_LEAD))) {
      throw new TQMCException(
          ErrorCode.FORBIDDEN, "User is unauthorized to perform this operation");
    }

    String query = QUERY_TEMPLATE;
    String fileDirectory =
        AssetUtility.getRootFolderPath(this.insight, AssetUtility.INSIGHT_SPACE_KEY, true);
    String baseFilename = "MN_Reports.csv";
    String filePathString = Utility.getUniqueFilePath(fileDirectory, baseFilename);
    File f = new File(filePathString);
    try (BufferedWriter bw = new BufferedWriter(new FileWriter(f));
        PreparedStatement ps = con.prepareStatement(query)) {
      bw.write(EXPORT_HEADERS[0]);
      for (int i = 1; i < EXPORT_HEADERS.length; i++) {
        bw.write(",");
        bw.write(EXPORT_HEADERS[i]);
      }
      // write results of query to CSV
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        bw.write("\n");
        while (rs.next()) {
          bw.write("\"" + rs.getString("ALIAS_RECORD_ID") + "\"");
          Date dueDateReview = rs.getDate("DUE_DATE_REVIEW");
          bw.write(
              ",\""
                  + (dueDateReview != null
                      ? ConversionUtils.getLocalDateStringFromDate(dueDateReview)
                      : "")
                  + "\"");
          Date dueDateDHA = rs.getDate("DUE_DATE_DHA");
          bw.write(
              ",\""
                  + (dueDateDHA != null
                      ? ConversionUtils.getLocalDateStringFromDate(dueDateDHA)
                      : "")
                  + "\"");
          bw.write(",\"" + rs.getString("PEER_REVIEWER") + "\"");
          bw.write(",\"" + rs.getString("RECOMMENDATION_RESPONSE") + "\"");
          Date createdAt = rs.getDate("CREATED_AT");
          bw.write(
              ",\""
                  + (createdAt != null ? ConversionUtils.getLocalDateStringFromDate(createdAt) : "")
                  + "\"");
          Date assignedAt = rs.getDate("ASSIGNED_AT");
          bw.write(
              ",\""
                  + (assignedAt != null
                      ? ConversionUtils.getLocalDateStringFromDate(assignedAt)
                      : "")
                  + "\"");
          Date attestedAt = rs.getDate("ATTESTED_AT");
          bw.write(
              ",\""
                  + (attestedAt != null
                      ? ConversionUtils.getLocalDateStringFromDate(attestedAt)
                      : "")
                  + "\"");
          bw.write(",\"" + rs.getString("SPECIALTY_NAME") + "\"");
          bw.write(",\"" + rs.getString("SUBSPECIALTY_NAME") + "\"");
          bw.write(",\"" + rs.getString("CARE_CONTRACTOR_NAME") + "\"");
          bw.write(",\"" + rs.getString("APPEAL_TYPE_NAME") + "\"");
          bw.write("\n");
        }
      }
    } catch (IOException e) {
      throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
    }
    String downloadKey = UUID.randomUUID().toString();
    InsightFile insightFile = new InsightFile();
    insightFile.setFileKey(downloadKey);
    insightFile.setFilePath(filePathString);
    insightFile.setDeleteOnInsightClose(true);
    this.insight.addExportFile(downloadKey, insightFile);
    NounMetadata retNoun =
        new NounMetadata(downloadKey, PixelDataType.CONST_STRING, PixelOperationType.FILE_DOWNLOAD);
    return retNoun;
  }
}
