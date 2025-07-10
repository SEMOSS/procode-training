package tqmc.reactors.gtt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import prerna.om.InsightFile;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.PixelOperationType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.AssetUtility;
import prerna.util.Utility;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.MTFData;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetMtfReportReactor extends AbstractTQMCReactor {

  public GetMtfReportReactor() {
    this.keysToGet = new String[] {"year"};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  private static final String CASE_QUERY =
      "SELECT r.dmis_id, "
          + "EXTRACT(MONTH FROM r.discharge_date) AS discharge_month, "
          + "COALESCE(mtf.alias_mtf_name, mtf.mtf_name) AS MTF_NAME, "
          + "COUNT(CASE WHEN (gw1.step_status != 'completed' OR gw2.step_status != 'completed') THEN 1 END) AS ABSTRACTION_STAGE_COUNT, "
          + "COUNT(CASE WHEN (gw1.step_status = 'completed' AND gw2.step_status = 'completed') AND (gw3.step_status != 'completed') THEN 1 END) AS CONSENSUS_STAGE_COUNT, "
          + "COUNT(CASE WHEN gw3.step_status = 'completed' AND (gw4.step_status != 'completed') THEN 1 END) AS PHYSICIAN_STAGE_COUNT, "
          + "COUNT(CASE WHEN gw4.step_status = 'completed' THEN 1 END) AS COMPLETED_STAGE_COUNT "
          + "FROM tqmc_record r "
          + "INNER JOIN mtf ON r.dmis_id = mtf.dmis_id "
          + "INNER JOIN gtt_consensus_case gcc ON gcc.record_id = r.record_id "
          + "INNER JOIN gtt_physician_case gpc ON gpc.record_id = r.record_id "
          + "INNER JOIN gtt_workflow gw1 ON gcc.abs_1_case_id = gw1.case_id AND gw1.is_latest = 1 "
          + "INNER JOIN gtt_workflow gw2 ON gcc.abs_2_case_id = gw2.case_id AND gw2.is_latest = 1 "
          + "INNER JOIN gtt_workflow gw3 ON gcc.case_id = gw3.case_id AND gw3.is_latest = 1 "
          + "INNER JOIN gtt_workflow gw4 ON gpc.case_id = gw4.case_id AND gw4.is_latest = 1 "
          + "WHERE EXTRACT(YEAR FROM r.discharge_date) = ? "
          + "GROUP BY r.dmis_id, discharge_month "
          + "ORDER BY discharge_month, CAST(r.dmis_id AS INTEGER)";

  private static final String[] monthStrings = {
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December"
  };

  private final LocalDateTime localTime = ConversionUtils.getUTCFromLocalNow();

  private String insightFileDirectory;

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);

    if (!hasProductManagementPermission(TQMCConstants.GTT)) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    insightFileDirectory =
        AssetUtility.getRootFolderPath(this.insight, AssetUtility.INSIGHT_SPACE_KEY, true);

    Map<String, LinkedHashMap<String, Map<String, Object>>> monthlyDmis = new LinkedHashMap<>();

    for (String m : monthStrings) {
      monthlyDmis.put(m, new LinkedHashMap<String, Map<String, Object>>());
    }

    try (PreparedStatement ps = con.prepareStatement(CASE_QUERY)) {
      ps.setString(1, payload.getYear());
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        // pull data into map
        String dmisId = rs.getString("DMIS_ID");
        String month = monthStrings[rs.getInt("DISCHARGE_MONTH") - 1];
        String mtfName = rs.getString("MTF_NAME");
        int abstractionStageCount = rs.getInt("ABSTRACTION_STAGE_COUNT");
        int consensusStageCount = rs.getInt("CONSENSUS_STAGE_COUNT");
        int physicianStageCount = rs.getInt("PHYSICIAN_STAGE_COUNT");
        int completedStageCount = rs.getInt("COMPLETED_STAGE_COUNT");

        Map<String, Map<String, Object>> rMap = monthlyDmis.get(month);
        if (!rMap.containsKey(dmisId)) {
          rMap.put(dmisId, new HashMap<String, Object>());
        }
        Map<String, Object> dmisByMonthMap = rMap.get(dmisId);
        dmisByMonthMap.put("mtf_name", mtfName);
        dmisByMonthMap.put("abstraction_stage_count", abstractionStageCount);
        dmisByMonthMap.put("consensus_stage_count", consensusStageCount);
        dmisByMonthMap.put("physician_stage_count", physicianStageCount);
        dmisByMonthMap.put("completed_stage_count", completedStageCount);
      }
    }

    List<MTFData> mtfData = TQMCHelper.getMtfs(con);

    String filePathString = makeMtfExcelReport(monthlyDmis, mtfData, payload.getYear(), localTime);

    String downloadKey = UUID.randomUUID().toString();
    InsightFile insightFile = new InsightFile();
    insightFile.setFileKey(downloadKey);
    insightFile.setFilePath(filePathString);
    insightFile.setDeleteOnInsightClose(true);
    this.insight.addExportFile(downloadKey, insightFile);

    return new NounMetadata(
        downloadKey, PixelDataType.CONST_STRING, PixelOperationType.FILE_DOWNLOAD);
  }

  private String makeMtfExcelReport(
      Map<String, LinkedHashMap<String, Map<String, Object>>> monthlyDmis,
      List<MTFData> mtfData,
      String year,
      LocalDateTime timestamp) {

    String excelFileName = "GTT_MTFCaseStatus_" + year;

    String excelfilePathString = null;

    try (Workbook workbook = new XSSFWorkbook()) {

      // Create font for headers
      org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
      headerFont.setFontName("Aptos Narrow");
      headerFont.setBold(true);
      headerFont.setFontHeightInPoints((short) 12);

      // Create style for headers
      CellStyle headerStyle = workbook.createCellStyle();
      headerStyle.setFont(headerFont);
      headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
      headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

      // Create font for body
      org.apache.poi.ss.usermodel.Font bodyFont = workbook.createFont();
      bodyFont.setFontName("Calibri");
      bodyFont.setFontHeightInPoints((short) 11);

      // Create style for body
      CellStyle bodyStyle = workbook.createCellStyle();
      bodyStyle.setFont(bodyFont);

      CellStyle coverStyle = workbook.createCellStyle();
      coverStyle.setWrapText(true);

      Sheet coverSheet = workbook.createSheet("Overview");
      coverSheet.setDisplayGridlines(false);
      int rowNum = 0;
      Row row;
      Cell cell;

      row = coverSheet.createRow(rowNum++);
      cell = row.createCell(0);
      cell.setCellValue("Overview of " + excelFileName);
      cell.setCellStyle(headerStyle);

      row = coverSheet.createRow(rowNum++);
      cell = row.createCell(0);
      row.createCell(0)
          .setCellValue(
              "This report compiles record (encounter) statuses by MTF, organized into monthly sheets by discharge month. "
                  + "Each sheet, named for its respective month, aggregates data specific to that period and based on the discharge date of the record. "
                  + "Each row represents an individual MTF, detailing the number of records in abstraction, consensus, physician stages, and the number completed.");
      cell.setCellStyle(coverStyle);

      row = coverSheet.createRow(rowNum++);
      row = coverSheet.createRow(rowNum++);
      cell = row.createCell(0);
      cell.setCellValue("Date Downloaded (UTC)");
      cell.setCellStyle(headerStyle);

      row = coverSheet.createRow(rowNum++);
      cell = row.createCell(0);
      row.createCell(0).setCellValue(ConversionUtils.getLocalDateTimeString(timestamp));
      cell.setCellStyle(coverStyle);

      coverSheet.setColumnWidth(0, 19500);

      for (String month : monthlyDmis.keySet()) {
        int[] colSum = new int[4];
        Sheet sheet = workbook.createSheet(month);
        rowNum = 0;
        int colNum = 0;
        row = sheet.createRow(rowNum++);

        String[] headers = {
          "DMIS",
          "MTF Name",
          "# In Abstraction",
          "# In Consensus",
          "# With Physician",
          "# Completed"
        };
        for (String header : headers) {
          cell = row.createCell(colNum++);
          cell.setCellValue(header);
          cell.setCellStyle(headerStyle);
        }

        for (MTFData m : mtfData) {
          colNum = 0;
          row = sheet.createRow(rowNum++);
          String mtfName = m.getAlias();
          String dmisId = m.getId();
          int aCount = 0;
          int cCount = 0;
          int pCount = 0;
          int completeCount = 0;
          if (monthlyDmis.get(month).containsKey(m.getId())) {
            Map<String, Object> dmisMap = monthlyDmis.get(month).get(m.getId());
            aCount = (Integer) dmisMap.get("abstraction_stage_count");
            cCount = (Integer) dmisMap.get("consensus_stage_count");
            pCount = (Integer) dmisMap.get("physician_stage_count");
            completeCount = (Integer) dmisMap.get("completed_stage_count");
          }
          cell = row.createCell(colNum++);
          cell.setCellValue(dmisId);
          cell.setCellStyle(bodyStyle);

          cell = row.createCell(colNum++);
          cell.setCellValue(mtfName);
          cell.setCellStyle(bodyStyle);

          cell = row.createCell(colNum++);
          cell.setCellValue(aCount);
          cell.setCellStyle(bodyStyle);
          colSum[0] += aCount;

          cell = row.createCell(colNum++);
          cell.setCellValue(cCount);
          cell.setCellStyle(bodyStyle);
          colSum[1] += cCount;

          cell = row.createCell(colNum++);
          cell.setCellValue(pCount);
          cell.setCellStyle(bodyStyle);
          colSum[2] += pCount;

          cell = row.createCell(colNum++);
          cell.setCellValue(completeCount);
          cell.setCellStyle(bodyStyle);
          colSum[3] += completeCount;
        }

        colNum = 1;
        row = sheet.createRow(rowNum++);

        cell = row.createCell(colNum++);
        cell.setCellValue("TOTAL");
        cell.setCellStyle(headerStyle);

        cell = row.createCell(colNum++);
        cell.setCellValue(colSum[0]);
        cell.setCellStyle(bodyStyle);

        cell = row.createCell(colNum++);
        cell.setCellValue(colSum[1]);
        cell.setCellStyle(bodyStyle);

        cell = row.createCell(colNum++);
        cell.setCellValue(colSum[2]);
        cell.setCellStyle(bodyStyle);

        cell = row.createCell(colNum++);
        cell.setCellValue(colSum[3]);
        cell.setCellStyle(bodyStyle);

        for (int i = 0; i < headers.length; i++) {
          sheet.autoSizeColumn(i);
        }
      }

      String extension = ".xlsx";

      excelfilePathString =
          Utility.getUniqueFilePath(insightFileDirectory, (excelFileName + extension));
      File excel = new File(excelfilePathString);
      try (FileOutputStream fos = new FileOutputStream(excel)) {
        workbook.write(fos);
      }
    } catch (IOException e) {
      throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, "Error creating excel");
    }

    if (excelfilePathString == null) {
      throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, "Error creating excel");
    }

    return excelfilePathString;
  }

  public static class Payload {
    private String year;

    public String getYear() {
      return year;
    }

    public void setYear(String year) {
      this.year = year;
    }
  }
}
