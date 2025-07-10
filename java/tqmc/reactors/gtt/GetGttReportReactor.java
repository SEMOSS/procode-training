package tqmc.reactors.gtt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import prerna.om.InsightFile;
import prerna.sablecc2.om.NounStore;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.PixelOperationType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.AssetUtility;
import prerna.util.Utility;
import prerna.util.ZipUtils;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.MTFData;
import tqmc.domain.base.TQMCException;
import tqmc.domain.gtt.GttRecord;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetGttReportReactor extends AbstractTQMCReactor {

  private static final Logger LOGGER = LogManager.getLogger(GetGttReportReactor.class);
  private static final String MONTH = "month"; // YYYY-MM
  private static final String BASE_QUERY =
      "SELECT gpc.record_id, "
          + "tr.dmis_id, "
          + "COALESCE(m.alias_mtf_name, m.mtf_name) AS MTF_NAME, "
          + "tr.length_of_stay, "
          + "tr.discharge_date, "
          + "tr.DHN, "
          + "gw.smss_timestamp AS COMPLETED_DATE, "
          + "CONCAT(tu.last_name, ', ', tu.first_name) AS NAME, "
          + "COALESCE(gett.name, '') AS GETT_NAME, COALESCE(gtt.bucket, '') AS GTT_BUCKET, "
          + "COALESCE(gtt.name, '') AS GTT_NAME, COALESCE(ghct.name, '') AS GHCT_NAME, "
          + "present_on_admission, COALESCE(grce.event_description, '') AS EVENT_DESCRIPTION, "
          + "COALESCE(gcn2.note, '') AS NOTE "
          + "FROM GTT_Workflow gw "
          + "LEFT JOIN Gtt_physician_case gpc "
          + "ON gpc.case_id = gw.case_id "
          + "LEFT JOIN Gtt_Record_Case_Event grce "
          + "ON grce.case_id = gw.case_id "
          + "LEFT JOIN Tqmc_record tr "
          + "ON tr.record_id = gpc.record_id "
          + "LEFT JOIN MTF m "
          + "ON tr.dmis_id = m.dmis_id "
          + "LEFT JOIN ("
          + "	SELECT gcn.case_id, "
          + "	MIN(gcn.note) AS note "
          + "	FROM gtt_case_note gcn "
          + "	INNER JOIN ("
          + "		SELECT case_id, "
          + "		MAX(updated_at) AS recent_update "
          + "		FROM gtt_case_note "
          + "		WHERE is_external = 1 GROUP BY case_id"
          + "	) subrecord "
          + "	ON gcn.case_id = subrecord.case_id "
          + "	AND gcn.updated_at = subrecord.recent_update "
          + "	WHERE gcn.is_external = 1 GROUP BY gcn.case_id"
          + ") gcn2 "
          + "ON gcn2.case_id = gpc.case_id "
          + "LEFT JOIN Gtt_event_type_template gett "
          + "ON gett.event_type_template_id = grce.event_type_template_id "
          + "LEFT JOIN Gtt_Trigger_Template gtt "
          + "ON gtt.trigger_template_id = grce.trigger_template_id "
          + "LEFT JOIN Gtt_Harm_Category_Template ghct "
          + "ON ghct.harm_category_template_id = grce.harm_category_template_id "
          + "LEFT JOIN Tqmc_User tu "
          + "ON tu.user_id = gpc.physician_user_id "
          + "WHERE gw.case_type = 'physician' "
          + "AND gw.is_latest = 1 "
          + "AND gw.step_status = 'completed'";

  private static final String SMSS_TIMESTAMP_FILTER =
      "AND smss_timestamp >= ? " + "AND smss_timestamp < ?";

  private static final String DISCHARGE_FILTER =
      "AND tr.discharge_date >= ? " + "AND tr.discharge_date < ?";

  private static final String QUERY_ORDER =
      "ORDER BY CAST(tr.dmis_id AS INTEGER), tr.discharge_date ASC, gpc.record_id ASC";

  private static final String MTF_QUERY =
      "SELECT DMIS_ID, "
          + "COALESCE(alias_mtf_name, mtf_name) AS MTF_NAME "
          + "FROM MTF "
          + "ORDER BY DMIS_ID";

  private static final String[] EXPORT_HEADERS =
      new String[] {
        "DMIS",
        "MTF Name",
        "DHN",
        "Encounter ID",
        "Length of Stay (Days)",
        "Discharge Date",
        "Completed Date",
        "Trigger Module",
        "Trigger",
        "Adverse Event Type",
        "Level of Harm",
        "Present on Admission",
        "Description",
        "Note"
      };

  private static final int TOTAL_COLUMNS = EXPORT_HEADERS.length;

  private MtfListWrapper mtfListWrapper;

  public GetGttReportReactor() {
    this.keysToGet = new String[] {MONTH};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    // check that user has a management role and has permission to access GTT
    if (!(hasProductPermission(TQMCConstants.GTT) && hasRole(TQMCConstants.MANAGEMENT_LEAD))) {
      throw new TQMCException(
          ErrorCode.FORBIDDEN, "User is unauthorized to perform this operation");
    }

    mtfListWrapper = new MtfListWrapper(con);
    LocalDate inputDate = null;
    try {
      inputDate =
          LocalDate.parse(
              this.keyValue.get(MONTH) + "-01", ConversionUtils.YYYY_MM_DD_DASH_FORMATTER);
    } catch (DateTimeParseException e) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid input date", e);
    }
    String titleDate = inputDate.format(DateTimeFormatter.ofPattern("yyyy_MM"));

    List<File> allFiles = new ArrayList<>();
    String zipFileName = "GTT_Reports_" + titleDate + ".zip";
    String fileDirectory =
        AssetUtility.getRootFolderPath(this.insight, AssetUtility.INSIGHT_SPACE_KEY, true);
    String filePath = Utility.getUniqueFilePath(fileDirectory, zipFileName);

    try {
      allFiles.add(createCompletedDtFile(con, inputDate));
      allFiles.add(createDischargeDtFile(con, inputDate));
      allFiles.add(createEfficiencyFile(con, inputDate));
      allFiles.add(createPhysEfficiencyFile(con, inputDate));
      createZip(allFiles, filePath);

    } catch (IOException e) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "File creation failed", e);
    }

    String downloadKey = UUID.randomUUID().toString();
    InsightFile insightFile = new InsightFile();

    insightFile.setFileKey(downloadKey);
    insightFile.setFilePath(filePath);
    insightFile.setDeleteOnInsightClose(true);
    this.insight.addExportFile(downloadKey, insightFile);

    return new NounMetadata(
        downloadKey, PixelDataType.CONST_STRING, PixelOperationType.FILE_DOWNLOAD);
  }

  private File createZip(List<File> allFiles, String filePath) throws IOException {

    File zipFile = new File(filePath);
    try (FileOutputStream fos = new FileOutputStream(zipFile);
        ZipOutputStream zos = new ZipOutputStream(fos)) {

      for (File f : allFiles) {
        addToZipAndDelete(f, zos);
      }
      return zipFile;
    }
  }

  private File createPhysEfficiencyFile(Connection con, LocalDate date) {
    String dateRange = date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    GetGttPhysicianEfficiencyReportReactor ggperReactor =
        new GetGttPhysicianEfficiencyReportReactor();
    ggperReactor.setInsight(this.insight);
    NounStore ns = new NounStore("nsGetGttEfficiencyReportReactor");
    ns.makeNoun(MONTH).add(dateRange, PixelDataType.CONST_STRING);
    ggperReactor.setNounStore(ns);
    ggperReactor.curNoun(MONTH);

    // Execute reactor, convert its data, and add to zip
    NounMetadata ggerNounMetadata = ggperReactor.execute();
    Object value = ggerNounMetadata.getValue();

    TQMCHelper.checkIfTqmcException(value);
    return (File) value;
  }

  private File createEfficiencyFile(Connection con, LocalDate date) {
    String dateRange = date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    GetGttEfficiencyReportReactor ggerReactor = new GetGttEfficiencyReportReactor();
    ggerReactor.setInsight(this.insight);
    NounStore ns = new NounStore("nsGetGttEfficiencyReportReactor");
    ns.makeNoun(MONTH).add(dateRange, PixelDataType.CONST_STRING);
    ggerReactor.setNounStore(ns);
    ggerReactor.curNoun(MONTH);

    // Execute reactor, convert its data, and add to zip
    NounMetadata ggerNounMetadata = ggerReactor.execute();
    Object value = ggerNounMetadata.getValue();

    TQMCHelper.checkIfTqmcException(value);
    return (File) value;
  }

  private File createDischargeDtFile(Connection con, LocalDate inputDate)
      throws SQLException, IOException {

    String combinedQuery = BASE_QUERY + DISCHARGE_FILTER + QUERY_ORDER;
    DateReportData reportData = getSQLData(con, combinedQuery, inputDate);

    try (Workbook workbook = new XSSFWorkbook()) {
      generateDischargeDtCoverSheet(workbook, inputDate);
      generateWorkbookData(workbook, reportData);

      String date = inputDate.format(DateTimeFormatter.ofPattern("yyyy_MM"));
      String fileName = "GTT_MTFFindingsbyDischargeDt_" + date + ".xlsx";
      return workbookToFile(workbook, fileName);
    }
  }

  private File createCompletedDtFile(Connection con, LocalDate inputDate)
      throws SQLException, IOException {

    String combinedQuery = BASE_QUERY + SMSS_TIMESTAMP_FILTER + QUERY_ORDER;
    DateReportData reportData = getSQLData(con, combinedQuery, inputDate);

    try (Workbook workbook = new XSSFWorkbook()) {
      generateCompletedDtCoverSheet(workbook, inputDate);
      generateWorkbookData(workbook, reportData);

      String date = inputDate.format(DateTimeFormatter.ofPattern("yyyy_MM"));
      String fileName = "GTT_MTFFindingsbyCompletedDt_" + date + ".xlsx";
      return workbookToFile(workbook, fileName);
    }
  }

  private File workbookToFile(Workbook workbook, String fileName) throws IOException {

    // create file
    // create printStream, attach to file
    // write workbook to printstream
    String fileDirectory =
        AssetUtility.getRootFolderPath(this.insight, AssetUtility.INSIGHT_SPACE_KEY, true);
    String filePath = Utility.getUniqueFilePath(fileDirectory, fileName);

    File f = new File(filePath);
    try (FileOutputStream os = new FileOutputStream(f)) {
      workbook.write(os);
    }
    return f;
  }

  private Workbook generateWorkbookData(Workbook workbook, DateReportData reportData)
      throws SQLException {
    // Create overview sheet
    CellStyle headerStyle = getHeaderStyle(workbook);
    List<List<GttRecord>> queryList = reportData.getMTFDataList();
    List<MTFData> emptyList = reportData.getEmptyMTFList();

    Sheet sheet = workbook.createSheet("All MTF Overview");
    Row row;
    createHeaders(sheet, headerStyle, 0);
    int rowCount = 0;
    for (int i = 0; i < queryList.size(); i++) {
      for (int j = 0; j < queryList.get(i).size(); j++) {
        GttRecord gttRecord = queryList.get(i).get(j);
        row = sheet.createRow(rowCount + 1);
        writeOverallRow(row, gttRecord);
        rowCount++;
      }
    }

    /**
     * List all empty MTFs in the All MTF Overview (Keep commented unless requested to re-add empty
     * MTF's)
     */
    //    for (int i = 0; i < emptyList.size(); i++) {
    //      row = sheet.createRow(i + rowCount + 1);
    //      row.createCell(0).setCellValue(emptyList.get(i).getId());
    //      row.createCell(1).setCellValue(emptyList.get(i).getName());
    //    }

    sizeColumns(sheet, TOTAL_COLUMNS);

    // Create a sheet for each MTF and populate with associated records

    List<MTFData> mtfList = mtfListWrapper.getList();

    for (int i = 0; i < queryList.size(); i++) {

      List<GttRecord> gttRecordList = queryList.get(i);

      // Clean invalid characters from sheetTitle
      String mtfName = cleanSheetTitle(mtfList.get(i).getName());
      String dmisId = cleanSheetTitle(mtfList.get(i).getId());
      sheet = workbook.createSheet(mtfName + " - " + dmisId);
      createHeaders(sheet, headerStyle, 3);

      for (int j = 0; j < queryList.get(i).size(); j++) {
        GttRecord gttRecord = gttRecordList.get(j);
        row = sheet.createRow(j + 1);
        writeMtfRow(row, gttRecord);
      }
      sizeColumns(sheet, TOTAL_COLUMNS - 3);
    }

    return workbook;
  }

  private Workbook generateCompletedDtCoverSheet(Workbook workbook, LocalDate date) {

    String titleDate = date.format(DateTimeFormatter.ofPattern("yyyy_MM"));
    String month = date.format(DateTimeFormatter.ofPattern("MMMM"));
    // Create style for headers in the sheets
    CellStyle headerStyle = getHeaderStyle(workbook);
    CellStyle coverStyle = getCoverStyle(workbook);

    // Generate cover sheet
    Cell cell;
    Sheet coverSheet = workbook.createSheet("Cover");
    coverSheet.setDisplayGridlines(false);

    Row row = coverSheet.createRow(0);
    cell = row.createCell(0);
    cell.setCellValue("Overview of GTT_MTFFindingsbyCompletedDt_" + titleDate);
    cell.setCellStyle(headerStyle);

    row = coverSheet.createRow(1);
    cell = row.createCell(0);
    row.createCell(0)
        .setCellValue(
            "This report consolidates all triggers and adverse events documented from "
                + "completed cases at a particular MTF in the month of "
                + month
                + ". The data is representative of completed case information at the time of download. "
                + "There is an overview sheet which contains every documented trigger and adverse "
                + "event regardless of MTF. Then, there are individual sheets for each MTF so that you "
                + "can monitor and report findings at the MTF-level.");
    cell.setCellStyle(coverStyle);

    row = coverSheet.createRow(3);
    cell = row.createCell(0);
    cell.setCellValue("All MTF Overview Sheet");
    cell.setCellStyle(headerStyle);

    row = coverSheet.createRow(4);
    cell = row.createCell(0);
    row.createCell(0)
        .setCellValue(
            "This sheet contains all documented triggers and adverse events from completed "
                + "cases in the month of "
                + month
                + ". All MTFs will populate in this sheet, but if there is no completed data for the "
                + "MTF this month, then the row will be empty.");
    cell.setCellStyle(coverStyle);

    row = coverSheet.createRow(6);
    cell = row.createCell(0);
    cell.setCellValue("MTF Sheets");
    cell.setCellStyle(headerStyle);

    row = coverSheet.createRow(7);
    cell = row.createCell(0);
    row.createCell(0)
        .setCellValue(
            "The remainder of the sheets in this report contain trigger and adverse event "
                + "information for the corresponding MTF. If a sheet is empty, that indicates the MTF "
                + "has no data completed for the month of "
                + month
                + " at the time of "
                + "download.");
    cell.setCellStyle(coverStyle);

    row = coverSheet.createRow(9);
    cell = row.createCell(0);
    cell.setCellValue("Date Downloaded (UTC)");
    cell.setCellStyle(headerStyle);

    row = coverSheet.createRow(10);
    cell = row.createCell(0);
    row.createCell(0)
        .setCellValue(ConversionUtils.getLocalDateTimeString(ConversionUtils.getUTCFromLocalNow()));
    cell.setCellStyle(coverStyle);

    coverSheet.setColumnWidth(0, 19750);
    return workbook;
  }

  private Workbook generateDischargeDtCoverSheet(Workbook workbook, LocalDate date) {

    String titleDate = date.format(DateTimeFormatter.ofPattern("yyyy_MM"));
    String month = date.format(DateTimeFormatter.ofPattern("MMMM"));
    // Create style for headers in the sheets
    CellStyle headerStyle = getHeaderStyle(workbook);
    CellStyle coverStyle = getCoverStyle(workbook);

    // Generate cover sheet
    Cell cell;
    Sheet coverSheet = workbook.createSheet("Cover");
    coverSheet.setDisplayGridlines(false);

    Row row = coverSheet.createRow(0);
    cell = row.createCell(0);
    cell.setCellValue("Overview of GTT_MTFFindingsbyDischargeDt_" + titleDate);
    cell.setCellStyle(headerStyle);

    row = coverSheet.createRow(1);
    cell = row.createCell(0);
    row.createCell(0)
        .setCellValue(
            "This report consolidates all triggers and adverse events from completed cases at a particular MTF for the month of "
                + month
                + " based on discharge date. The data is representative of completed case information at the time of download. There is an overview sheet which contains every documented trigger and adverse event regardless of MTF. Then, there are individual sheets for each MTF so that you can monitor and report findings at the MTF-level.");
    cell.setCellStyle(coverStyle);

    row = coverSheet.createRow(3);
    cell = row.createCell(0);
    cell.setCellValue("All MTF Overview Sheet");
    cell.setCellStyle(headerStyle);

    row = coverSheet.createRow(4);
    cell = row.createCell(0);
    row.createCell(0)
        .setCellValue(
            "This sheet contains all documented triggers and adverse events from completed cases in the month of "
                + month
                + " based on discharge date. All MTFs will populate in this sheet, but if there is no completed data for the MTF this month, then the row will be empty.");
    cell.setCellStyle(coverStyle);

    row = coverSheet.createRow(6);
    cell = row.createCell(0);
    cell.setCellValue("MTF Sheets");
    cell.setCellStyle(headerStyle);

    row = coverSheet.createRow(7);
    cell = row.createCell(0);
    row.createCell(0)
        .setCellValue(
            "The remainder of the sheets in this report contain trigger and adverse event "
                + "information for the corresponding MTF. If a sheet is empty, that indicates the MTF "
                + "has no data completed for the month of "
                + month
                + " at the time of "
                + "download.");
    cell.setCellStyle(coverStyle);

    row = coverSheet.createRow(9);
    cell = row.createCell(0);
    cell.setCellValue("Date Downloaded (UTC)");
    cell.setCellStyle(headerStyle);

    row = coverSheet.createRow(10);
    cell = row.createCell(0);
    row.createCell(0)
        .setCellValue(ConversionUtils.getLocalDateTimeString(ConversionUtils.getUTCFromLocalNow()));
    cell.setCellStyle(coverStyle);

    coverSheet.setColumnWidth(0, 19750);
    return workbook;
  }

  private DateReportData getSQLData(Connection con, String combinedQuery, LocalDate inputDate)
      throws SQLException {
    String startDate = inputDate.toString();
    String endDate = inputDate.plusMonths(1).toString();

    List<MTFData> mtfList = mtfListWrapper.getList();
    List<List<GttRecord>> MTFDataList = new ArrayList<>();
    List<MTFData> listOfEmptyMtfs = new ArrayList<>();

    try (PreparedStatement ps = con.prepareStatement(combinedQuery)) {
      ps.setString(1, startDate);
      ps.setString(2, endDate);
      // Create a List<List<Map>> data structure. Inner map is one row of data.
      // Middle List contains all rows for one DMIS_ID. Outer List contains all
      // DMIS_IDs / MTFs

      ResultSet rs = ps.executeQuery();
      boolean moreRows = true;
      // Grab first element
      if (!rs.next()) {
        moreRows = false;
      }

      for (int mtfIndex = 0; mtfIndex < mtfList.size(); mtfIndex++) {
        // List of all records for one MTF
        List<GttRecord> gttRecordList = new ArrayList<>();

        Boolean isDataForMtf = false;
        if (moreRows && mtfList.get(mtfIndex).getName().equals(rs.getString("MTF_NAME"))) {
          isDataForMtf = true;
        } else {
          // listOfEmptyMtfs.add(mtfList.get(mtfIndex));
        }
        while (moreRows && isDataForMtf) {
          GttRecord gttRecord = new GttRecord();
          gttRecord.setDmisId(rs.getString("dmis_id"));
          gttRecord.setMtfName(rs.getString("MTF_NAME"));
          gttRecord.setRecordId(rs.getString("record_id"));
          gttRecord.setDhn(rs.getString("DHN"));
          gttRecord.setLos(rs.getInt("length_of_stay"));
          gttRecord.setDischargeDate(
              ConversionUtils.getLocalDateFromDate(rs.getDate("discharge_date")));
          gttRecord.setCompletedDate(
              ConversionUtils.getLocalDateFromDate(rs.getDate("COMPLETED_DATE")));
          gttRecord.setGettName(rs.getString("GETT_NAME"));
          gttRecord.setGttBucket(rs.getString("GTT_BUCKET"));
          gttRecord.setGttName(rs.getString("GTT_NAME"));
          gttRecord.setGhctName(rs.getString("GHCT_NAME"));
          boolean res = rs.getInt("present_on_admission") == 1;
          gttRecord.setPresentOnAdmission((rs.wasNull()) ? null : res);
          gttRecord.setEventDescription(rs.getString("EVENT_DESCRIPTION"));
          gttRecord.setNote(rs.getString("NOTE"));

          gttRecordList.add(gttRecord);

          if (!rs.next()) {
            moreRows = false;
          }

          if (moreRows && !mtfList.get(mtfIndex).getName().equals(rs.getString("MTF_NAME"))) {
            isDataForMtf = false;
          }
        }
        // List of all MTFs
        MTFDataList.add(gttRecordList);
      }
      return new DateReportData(MTFDataList, listOfEmptyMtfs);
    }
  }

  private CellStyle getHeaderStyle(Workbook workbook) {
    Font headerFont = workbook.createFont();
    headerFont.setBold(true);
    headerFont.setFontHeightInPoints((short) 12);
    headerFont.setFontName("Aptos Narrow Bold");
    CellStyle headerStyle = workbook.createCellStyle();
    headerStyle.setFont(headerFont);
    headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
    headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

    return headerStyle;
  }

  private CellStyle getCoverStyle(Workbook workbook) {
    CellStyle coverStyle = workbook.createCellStyle();
    coverStyle.setWrapText(true);

    return coverStyle;
  }

  /**
   * Create the predesignated headers for a sheet
   *
   * @param sheet - sheet to insert headers into @Param style - format of header cells
   * @param offset - how many headers to skip. first x headers
   */
  private void createHeaders(Sheet sheet, CellStyle style, int offset) {
    Row headerRow = sheet.createRow(0);
    for (int i = offset; i < EXPORT_HEADERS.length; i++) {
      Cell cell = headerRow.createCell(i - offset);
      cell.setCellValue(EXPORT_HEADERS[i]);
      cell.setCellStyle(style);
    }
  }

  private void writeOverallRow(Row row, GttRecord gttRecord) {
    int rowNum = 0;
    row.createCell(rowNum++).setCellValue(gttRecord.getDmisId());
    row.createCell(rowNum++).setCellValue(gttRecord.getMtfName());
    row.createCell(rowNum++).setCellValue(gttRecord.getDhn());
    row.createCell(rowNum++).setCellValue(gttRecord.getRecordId());
    row.createCell(rowNum++).setCellValue(gttRecord.getLos());
    row.createCell(rowNum++)
        .setCellValue(ConversionUtils.getLocalDateString(gttRecord.getDischargeDate()));
    row.createCell(rowNum++)
        .setCellValue(ConversionUtils.getLocalDateString(gttRecord.getCompletedDate()));
    row.createCell(rowNum++).setCellValue(gttRecord.getGttBucket());
    row.createCell(rowNum++).setCellValue(gttRecord.getGttName());
    row.createCell(rowNum++).setCellValue(gttRecord.getGettName());
    row.createCell(rowNum++).setCellValue(gttRecord.getGhctName());
    row.createCell(rowNum++)
        .setCellValue(
            String.valueOf(
                gttRecord.getPresentOnAdmission() == null
                    ? ""
                    : gttRecord.getPresentOnAdmission()));
    row.createCell(rowNum++).setCellValue(gttRecord.getEventDescription());
    row.createCell(rowNum++).setCellValue(gttRecord.getNote());
  }

  private void writeMtfRow(Row row, GttRecord gttRecord) {
    int rowNum = 0;
    row.createCell(rowNum++).setCellValue(gttRecord.getRecordId());
    row.createCell(rowNum++).setCellValue(gttRecord.getLos());
    row.createCell(rowNum++)
        .setCellValue(ConversionUtils.getLocalDateString(gttRecord.getDischargeDate()));
    row.createCell(rowNum++)
        .setCellValue(ConversionUtils.getLocalDateString(gttRecord.getCompletedDate()));
    row.createCell(rowNum++).setCellValue(gttRecord.getGttBucket());
    row.createCell(rowNum++).setCellValue(gttRecord.getGttName());
    row.createCell(rowNum++).setCellValue(gttRecord.getGettName());
    row.createCell(rowNum++).setCellValue(gttRecord.getGhctName());
    row.createCell(rowNum++)
        .setCellValue(
            String.valueOf(
                gttRecord.getPresentOnAdmission() == null
                    ? ""
                    : gttRecord.getPresentOnAdmission()));
    row.createCell(rowNum++).setCellValue(gttRecord.getEventDescription());
    row.createCell(rowNum++).setCellValue(gttRecord.getNote());
  }

  private void sizeColumns(Sheet sheet, int numColumns) {
    for (int i = 0; i < numColumns; i++) {
      sheet.autoSizeColumn(i);
      sheet.setColumnWidth(
          i, Math.min(TQMCConstants.MAX_EXCEL_COLUMN_WIDTH, sheet.getColumnWidth(i) + 512));
    }
  }

  private String cleanSheetTitle(String title) {
    for (String invalidChar : TQMCConstants.INVALID_EXCEL_CHARACTERS) {
      title = title.replace(invalidChar, " ");
    }
    // limit title length to 24 due to excel sheet length requirements
    if (title.length() > 24) {
      title = title.substring(0, 24);
    }
    return title;
  }

  private void addToZipAndDelete(File f, ZipOutputStream zos) throws IOException {
    try {
      ZipUtils.addToZipFile(f, zos);
    } finally {
      if (f != null && f.exists()) {
        try {
          f.delete();
        } catch (SecurityException e) {
          LOGGER.warn("Error deleting file " + f.getAbsolutePath(), e);
        }
      }
    }
  }

  /**
   * Is an MtfList of type List<MTFData>. Exists to maintain proper data encapsulation for
   * cacheList. Should only be accessible through getContents
   */
  private class MtfListWrapper {

    private List<MTFData> cacheList;
    private Connection con;

    public MtfListWrapper(Connection con) {
      this.con = con;
    }

    /**
     * Get the MtfList.
     *
     * @param con - the database which holds the list contents
     * @return - the mtfList
     * @throws SQLException
     */
    public List<MTFData> getList() throws SQLException {

      if (cacheList != null) {
        return cacheList;
      }

      try (PreparedStatement ps = con.prepareStatement(MTF_QUERY)) {
        ResultSet rs = ps.executeQuery();
        cacheList = new ArrayList<MTFData>();
        while (rs.next()) {
          MTFData mtf = new MTFData(rs.getString("MTF_NAME"), rs.getString("DMIS_ID"), null);

          cacheList.add(mtf);
        }
        return cacheList;
      }
    }
  }

  private class DateReportData {

    private List<MTFData> emptyMTFList;
    private List<List<GttRecord>> MTFDataList;

    public DateReportData(List<List<GttRecord>> MTFDataList, List<MTFData> emptyMTFList) {
      this.MTFDataList = MTFDataList;
      this.emptyMTFList = emptyMTFList;
    }

    public List<MTFData> getEmptyMTFList() {
      return emptyMTFList;
    }

    public List<List<GttRecord>> getMTFDataList() {
      return MTFDataList;
    }
  }
}
