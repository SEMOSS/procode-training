package tqmc.reactors.soc;

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

public class GetSocReportsReactor extends AbstractTQMCReactor {

  private static final String QUERY_TEMPLATE =
      "SELECT"
          + " COALESCE(sr.ALIAS_RECORD_ID, '') AS ALIAS_RECORD_ID,"
          + " sc.DUE_DATE_REVIEW, sc.DUE_DATE_DHA, "
          + " COALESCE(mtf.DMIS_ID, '') AS DMIS_ID, "
          + " COALESCE(mtf.MTF_NAME, '') AS MTF_NAME, "
          + " COALESCE(sc.USER_ID, '') AS PEER_REVIEWER, "
          + " sc.CREATED_AT as CREATED_AT, "
          + " sc.ASSIGNED_AT as ASSIGNED_AT, "
          + " sc.ATTESTED_AT as ATTESTED_AT, "
          + " COALESCE(ts.SPECIALTY_NAME, '') AS SPECIALTY_NAME, "
          + " COALESCE(ts.SUBSPECIALTY_NAME, '') AS SUBSPECIALTY_NAME, "
          + " COALESCE(scpe.PROVIDER_NAME, '') AS PROVIDER_NAME, "
          + " COALESCE(CASE "
          + "  WHEN sw.step_status = 'completed' THEN scpe.STANDARDS_MET "
          + "  ELSE '' "
          + " END, '') AS STANDARDS_MET, "
          + " COALESCE(CASE "
          + "  WHEN sw.step_status = 'completed' THEN scpe.DEVIATION_CLAIM "
          + "  ELSE '' "
          + " END, '') AS DEVIATION_CLAIM, "
          + "FROM "
          + " SOC_RECORD sr "
          + "JOIN SOC_CASE sc ON "
          + " sr.RECORD_ID = sc.RECORD_ID AND sc.DELETED_AT IS NULL "
          + "LEFT JOIN SOC_RECORD_MTF srm ON "
          + " srm.RECORD_ID = sr.RECORD_ID "
          + "LEFT JOIN MTF mtf ON "
          + " srm.DMIS_ID = mtf.DMIS_ID "
          + "LEFT JOIN SOC_CASE_PROVIDER_EVALUATION scpe ON "
          + " scpe.CASE_ID = sc.CASE_ID "
          + "LEFT JOIN TQMC_SPECIALTY ts ON "
          + " ts.SPECIALTY_ID = sc.SPECIALTY_ID "
          + "LEFT JOIN SOC_WORKFLOW sw ON "
          + " sc.CASE_ID = sw.CASE_ID WHERE sw.IS_LATEST = '1' "
          + "ORDER BY sc.DUE_DATE_DHA ASC, sr.ALIAS_RECORD_ID ASC";

  private static final String[] EXPORT_HEADERS =
      new String[] {
        "Record ID",
        "Due Date Review",
        "Due Date DHA",
        "DMIS ID",
        "MTF Name",
        "Peer Reviewer",
        "Created At",
        "Assigned At",
        "Attested At",
        "Specialty Name",
        "Subspecialty Name",
        "Provider Name",
        "Standards Met",
        "Deviation Claim"
      };

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    if (!(hasProductPermission(TQMCConstants.SOC) && hasRole(TQMCConstants.MANAGEMENT_LEAD))) {
      throw new TQMCException(
          ErrorCode.FORBIDDEN, "User is unauthorized to perform this operation");
    }

    String query = QUERY_TEMPLATE;
    String fileDirectory =
        AssetUtility.getRootFolderPath(this.insight, AssetUtility.INSIGHT_SPACE_KEY, true);
    String baseFilename = "SOC_Reports.csv";
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
          bw.write(",\"" + rs.getString("DMIS_ID") + "\"");
          bw.write(",\"" + rs.getString("MTF_NAME") + "\"");
          bw.write(",\"" + rs.getString("PEER_REVIEWER") + "\"");
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
          bw.write(",\"" + rs.getString("PROVIDER_NAME") + "\"");
          bw.write(",\"" + rs.getString("STANDARDS_MET") + "\"");
          bw.write(",\"" + rs.getString("DEVIATION_CLAIM") + "\"");
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
