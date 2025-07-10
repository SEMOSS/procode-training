package tqmc.reactors.soc;

import com.google.common.collect.Lists;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableCell.XWPFVertAlign;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDecimalNumber;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHpsMeasure;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTInd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLevelText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumFmt;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTOnOff;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import prerna.om.InsightFile;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.PixelOperationType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.AssetUtility;
import prerna.util.Utility;
import prerna.util.ZipUtils;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetSocReportReactor extends AbstractTQMCReactor {
  private static final Logger LOGGER = LogManager.getLogger(GetSocReportReactor.class);

  private static final String FORMAT = "format";
  private static final List<String> VALID_FORMATS = Lists.newArrayList("xlsx", "docx");
  private static final DateTimeFormatter DD_MONTH_YYYY_FORMATTER =
      DateTimeFormatter.ofPattern("dd MMMM yyyy");
  private static final DateTimeFormatter YYYY_MM_DD_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final Map<String, String> SOC_MET_CODES = new HashMap<>();

  static {
    SOC_MET_CODES.put("yes", "SOC Met");
    SOC_MET_CODES.put("no", "SOC Not Met");
    SOC_MET_CODES.put("utd", "Unable to Determine");
  }

  private static final Map<String, String> DEVIATION_CODES = new HashMap<>();

  static {
    DEVIATION_CODES.put("yes", "Yes");
    DEVIATION_CODES.put("no", "No");
    DEVIATION_CODES.put("utd", "Unable to Determine");
    DEVIATION_CODES.put("na", "Non-applicable SOC Met");
  }

  private static final Map<String, String> SYSTEM_ISSUE_CODES = new HashMap<>();

  static {
    SYSTEM_ISSUE_CODES.put("yes", "Yes");
    SYSTEM_ISSUE_CODES.put("no", "No");
    SYSTEM_ISSUE_CODES.put("utd", "Unable to Determine");
  }

  private static final String ATTESTED_QUERY =
      "SELECT"
          + " sc.RECORD_ID AS RECORD_ID, "
          + " sc.attestation_signature AS attestation_signature, "
          + " sc.attested_at AS attested_at, "
          + " sc.attestation_specialty AS SPECIALTY_NAME, "
          + " sc.attestation_subspecialty AS SUBSPECIALTY_NAME, "
          + " sr.ALIAS_RECORD_ID AS ALIAS_RECORD_ID "
          + "FROM "
          + " SOC_CASE sc "
          + "JOIN SOC_Workflow sw ON "
          + " sw.CASE_ID = sc.CASE_ID "
          + "LEFT JOIN TQMC_SPECIALTY ts ON "
          + " sc.SPECIALTY_ID = ts.SPECIALTY_ID "
          + "LEFT JOIN SOC_RECORD sr ON "
          + " sr.RECORD_ID = sc.RECORD_ID "
          + "WHERE "
          + " sc.CASE_ID = ? "
          + "AND sw.IS_LATEST = 1 "
          + "AND sw.STEP_STATUS = 'completed'";

  private String recordId;
  private String format;

  private String insightFileDirectory;
  private String projectAssetDirectory;

  private Map<String, String> recordMap = new LinkedHashMap<>();
  private List<String> mtfNames = new ArrayList<>();
  private Map<String, SocData> caseDataMap = new LinkedHashMap<>();
  private Map<String, List<Map<String, String>>> providerEvalsByCaseId = new LinkedHashMap<>();

  private BigInteger listNumber = BigInteger.valueOf(-1);
  private byte[] checkedBytes;
  private byte[] uncheckedBytes;

  public GetSocReportReactor() {
    this.keysToGet = new String[] {TQMCConstants.RECORD_ID, FORMAT};
    this.keyRequired = new int[] {1, 0};
  }

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    if (!hasProductManagementPermission(TQMCConstants.SOC)) {
      throw new TQMCException(ErrorCode.FORBIDDEN, "User doesn't have the correct Permissions");
    }

    recordId = keyValue.get(TQMCConstants.RECORD_ID);
    format = StringUtils.lowerCase(keyValue.get(FORMAT));
    if (format == null || format.isEmpty()) {
      format = "docx";
    } else if (!VALID_FORMATS.contains(format)) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Unsupported file format requested");
    }

    loadData(con);

    insightFileDirectory =
        AssetUtility.getRootFolderPath(this.insight, AssetUtility.INSIGHT_SPACE_KEY, true);
    projectAssetDirectory = AssetUtility.getProjectAssetsFolder(projectId);

    String outputFilePath = createReport(con);

    String outputDownloadKey = UUID.randomUUID().toString();
    InsightFile outputFile = new InsightFile();
    outputFile.setFileKey(outputDownloadKey);
    outputFile.setFilePath(outputFilePath);
    outputFile.setDeleteOnInsightClose(true);
    this.insight.addExportFile(outputDownloadKey, outputFile);
    return new NounMetadata(
        outputDownloadKey, PixelDataType.CONST_STRING, PixelOperationType.FILE_DOWNLOAD);
  }

  private String createReport(Connection con) {
    String zipFileName = "SOC_Report_" + recordId + ".zip";
    String zipFilePathString = Utility.getUniqueFilePath(insightFileDirectory, zipFileName);

    try (FileOutputStream fos = new FileOutputStream(zipFilePathString);
        ZipOutputStream zos = new ZipOutputStream(fos)) {
      if ("docx".equals(format)) {
        File f = createRecordDocx();
        addToZipAndDelete(f, zos);
      }
      for (String caseId : caseDataMap.keySet()) {
        if ("xlsx".equals(format)) {
          File f = createCaseXlsx(caseId);
          addToZipAndDelete(f, zos);
        }
        SocData caseData = caseDataMap.get(caseId);
        if (TQMCConstants.CASE_STEP_STATUS_COMPLETED.equals(caseData.getStepStatus())) {
          File f = createCaseAttestationPdf(con, caseId);
          addToZipAndDelete(f, zos);
        }
      }
    } catch (IOException e) {
      throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
    }

    return zipFilePathString;
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

  private File createCaseXlsx(String caseId) {
    try (Workbook workbook = new XSSFWorkbook()) {
      SocData caseData = caseDataMap.get(caseId);
      List<Map<String, String>> provEvals = providerEvalsByCaseId.get(caseId);

      org.apache.poi.ss.usermodel.Font boldFont = workbook.createFont();
      boldFont.setBold(true);
      CellStyle boldStyle = workbook.createCellStyle();
      boldStyle.setFont(boldFont);

      Sheet sheet = workbook.createSheet("Overview");

      int rowNum = 0;
      Cell cell = sheet.createRow(rowNum++).createCell(0);
      cell.setCellValue("Record ID");
      cell.setCellStyle(boldStyle);
      cell = sheet.createRow(rowNum++).createCell(0);
      cell.setCellValue("Review ID");
      cell.setCellStyle(boldStyle);
      rowNum++;
      cell = sheet.createRow(rowNum++).createCell(0);
      cell.setCellValue("Peer Reviewer Name");
      cell.setCellStyle(boldStyle);
      cell = sheet.createRow(rowNum++).createCell(0);
      cell.setCellValue("Completed Date");
      cell.setCellStyle(boldStyle);
      rowNum++;
      cell = sheet.createRow(rowNum++).createCell(0);
      cell.setCellValue("Statement of facts acknowledgement");
      cell.setCellStyle(boldStyle);

      rowNum = 0;
      sheet.getRow(rowNum++).createCell(1).setCellValue(recordId);
      sheet.getRow(rowNum++).createCell(1).setCellValue(caseId);
      rowNum++;
      sheet.getRow(rowNum++).createCell(1).setCellValue(caseData.getAttestationSignature());
      sheet
          .getRow(rowNum++)
          .createCell(1)
          .setCellValue(
              caseData.getAttestedAt() != null
                  ? DD_MONTH_YYYY_FORMATTER.format(caseData.getAttestedAt())
                  : "N/A");
      rowNum++;
      sheet.getRow(rowNum++).createCell(1).setCellValue("TRUE");
      sheet.autoSizeColumn(0);
      sheet.autoSizeColumn(1);

      sheet = workbook.createSheet("Nurse Review");

      rowNum = 0;
      cell = sheet.createRow(rowNum++).createCell(0);
      cell.setCellValue("Period of Care Under Review");
      cell.setCellStyle(boldStyle);
      cell = sheet.createRow(rowNum++).createCell(0);
      cell.setCellValue("Injury");
      cell.setCellStyle(boldStyle);
      cell = sheet.createRow(rowNum++).createCell(0);
      cell.setCellValue("Diagnosis");
      cell.setCellStyle(boldStyle);
      rowNum++;
      cell = sheet.createRow(rowNum++).createCell(0);
      cell.setCellValue("Summary of Facts of Case");
      cell.setCellStyle(boldStyle);
      cell = sheet.createRow(rowNum++).createCell(0);
      cell.setCellValue("Allegations");
      cell.setCellStyle(boldStyle);

      rowNum = 0;
      sheet
          .getRow(rowNum++)
          .createCell(1)
          .setCellValue(
              recordMap.getOrDefault("PERIOD_OF_CARE_START", "")
                  + " - "
                  + recordMap.getOrDefault("PERIOD_OF_CARE_END", ""));
      sheet.getRow(rowNum++).createCell(1).setCellValue(recordMap.getOrDefault("INJURY", ""));
      sheet.getRow(rowNum++).createCell(1).setCellValue(recordMap.getOrDefault("DIAGNOSES", ""));
      rowNum++;
      sheet
          .getRow(rowNum++)
          .createCell(1)
          .setCellValue(recordMap.getOrDefault("SUMMARY_OF_FACTS", ""));
      sheet.getRow(rowNum++).createCell(1).setCellValue(recordMap.getOrDefault("ALLEGATIONS", ""));
      sheet.autoSizeColumn(0);
      sheet.autoSizeColumn(1);

      if (provEvals != null && !provEvals.isEmpty()) {
        int providerNum = 0;
        for (Map<String, String> provEvalData : provEvals) {
          providerNum++;

          String sheetName = "Provider " + Integer.toString(providerNum);

          sheet = workbook.createSheet(sheetName);

          rowNum = 0;
          cell = sheet.createRow(rowNum++).createCell(0);
          cell.setCellValue(sheetName);
          cell.setCellStyle(boldStyle);
          rowNum++;
          cell = sheet.createRow(rowNum++).createCell(0);
          cell.setCellValue("Question 1");
          cell.setCellStyle(boldStyle);
          cell = sheet.createRow(rowNum++).createCell(0);
          cell.setCellValue("Was the standard of care met or not for this provider?");
          cell = sheet.createRow(rowNum++).createCell(0);
          cell.setCellValue("Rationale");
          cell = sheet.createRow(rowNum++).createCell(0);
          cell.setCellValue("Specific sections of the record used to formulate rationale");
          rowNum++;
          cell = sheet.createRow(rowNum++).createCell(0);
          cell.setCellValue("Question 2");
          cell.setCellStyle(boldStyle);
          cell = sheet.createRow(rowNum++).createCell(0);
          cell.setCellValue(
              "Did the deviation from the standard of care cause or contribute to a death, disability, or malpractice claim?");
          cell = sheet.createRow(rowNum++).createCell(0);
          cell.setCellValue("Rationale");
          cell = sheet.createRow(rowNum++).createCell(0);
          cell.setCellValue("Specific sections of the record used to formulate rationale");

          rowNum = 0;
          sheet.getRow(rowNum++).createCell(1).setCellValue(provEvalData.get("PROVIDER_NAME"));
          rowNum += 2;
          sheet
              .getRow(rowNum++)
              .createCell(1)
              .setCellValue(SOC_MET_CODES.getOrDefault(provEvalData.get("STANDARDS_MET"), ""));
          sheet
              .getRow(rowNum++)
              .createCell(1)
              .setCellValue(provEvalData.getOrDefault("STANDARDS_RATIONALE", ""));
          sheet
              .getRow(rowNum++)
              .createCell(1)
              .setCellValue(provEvalData.getOrDefault("STANDARDS_JUSTIFICATION", ""));
          rowNum += 2;
          sheet
              .getRow(rowNum++)
              .createCell(1)
              .setCellValue(DEVIATION_CODES.getOrDefault(provEvalData.get("DEVIATION_CLAIM"), ""));
          sheet
              .getRow(rowNum++)
              .createCell(1)
              .setCellValue(provEvalData.getOrDefault("DEVIATION_CLAIM_RATIONALE", ""));
          sheet
              .getRow(rowNum++)
              .createCell(1)
              .setCellValue(provEvalData.getOrDefault("DEVIATION_CLAIM_JUSTIFICATION", ""));
          sheet.autoSizeColumn(0);
          sheet.autoSizeColumn(1);
        }
      }

      sheet = workbook.createSheet("References & System Issues");

      rowNum = 0;
      cell = sheet.createRow(rowNum++).createCell(0);
      cell.setCellValue(
          "Please provide guidelines/evidence-based citations as references. Copies of articles may be submitted. Please note: references consulted must be \"at time of\" or \"prior to\" the actual delivery of care.");
      cell.setCellStyle(boldStyle);
      cell = sheet.createRow(rowNum++).createCell(0);
      cell.setCellValue("Did this case show evidence of a systems issue?");
      cell.setCellStyle(boldStyle);
      cell = sheet.createRow(rowNum++).createCell(0);
      cell.setCellValue("Rationale");
      cell.setCellStyle(boldStyle);
      cell = sheet.createRow(rowNum++).createCell(0);
      cell.setCellValue("Specific sections of the record used to formulate rationale");
      cell.setCellStyle(boldStyle);

      rowNum = 0;
      sheet.getRow(rowNum++).createCell(1).setCellValue(caseData.getSystemReferences());
      sheet
          .getRow(rowNum++)
          .createCell(1)
          .setCellValue(SYSTEM_ISSUE_CODES.getOrDefault(caseData.getSystemIssue(), ""));
      sheet.getRow(rowNum++).createCell(1).setCellValue(caseData.getSystemIssueRationale());
      sheet.getRow(rowNum++).createCell(1).setCellValue(caseData.getSystemIssueJustification());
      sheet.autoSizeColumn(0);
      sheet.autoSizeColumn(1);

      String excelFileName = "SOC_Report_" + caseId + ".xlsx";
      String excelfilePathString = Utility.getUniqueFilePath(insightFileDirectory, excelFileName);
      File excel = new File(excelfilePathString);
      try (FileOutputStream fos = new FileOutputStream(excel)) {
        workbook.write(fos);
      }
      return excel;
    } catch (SecurityException | IOException e) {
      throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
    }
  }

  private File createCaseAttestationPdf(Connection con, String caseId) {
    String pdfFileName = "SOC_Attested_" + caseId + ".pdf";
    String pdffilePathString = Utility.getUniqueFilePath(insightFileDirectory, pdfFileName);
    File pdf = new File(pdffilePathString);
    TQMCHelper.generateAttestationPdf(con, this.projectId, caseId, pdf, ATTESTED_QUERY);
    return pdf;
  }

  private File createRecordDocx() {

    String dobIsoString = recordMap.get("PATIENT_DOB");
    String dobString = "";
    if (dobIsoString != null) {
      LocalDate dob = LocalDate.from(YYYY_MM_DD_FORMATTER.parse(dobIsoString));
      dobString = DD_MONTH_YYYY_FORMATTER.format(dob);
    }

    try (XWPFDocument doc = new XWPFDocument()) {
      checkedBytes =
          Files.readAllBytes(
              Paths.get(projectAssetDirectory, "pdf_resources/checked.png").normalize());
      uncheckedBytes =
          Files.readAllBytes(
              Paths.get(projectAssetDirectory, "pdf_resources/unchecked.png").normalize());

      for (String caseId : caseDataMap.keySet()) {

        SocData caseData = caseDataMap.get(caseId);

        List<Map<String, String>> provEvals = providerEvalsByCaseId.get(caseId);
        String specialtyString = caseData.getSpecialtyName();
        String attestationSpecialtyString = caseData.getAttestationSpecialty();

        String attestationSubspecialtyString = caseData.getAttestationSubspecialty();
        attestationSpecialtyString += " (" + attestationSubspecialtyString + ")";

        String subspecialty = caseData.getSubspecialtyName();
        specialtyString += " (" + subspecialty + ")";

        LocalDateTime attestedAt = caseData.getAttestedAt();

        String tag =
            specialtyString
                + "/"
                + recordMap.get("PATIENT_LAST_NAME")
                + "/"
                + recordMap.get("ALIAS_RECORD_ID");

        String systemIssueString = SYSTEM_ISSUE_CODES.getOrDefault(caseData.getSystemIssue(), "");

        setCaseReviewSectionHeaderAndFooter(doc, tag);

        Boolean isComplete =
            TQMCConstants.CASE_STEP_STATUS_COMPLETED.equals(caseData.getStepStatus());
        Boolean isCompleteNurse =
            TQMCConstants.CASE_STEP_STATUS_COMPLETED.equals(recordMap.get("STEP_STATUS"));

        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);

        XWPFRun run = makeRun(p);
        run.setBold(true);
        run.setUnderline(UnderlinePatterns.SINGLE);
        run.setText(specialtyString);

        doc.createParagraph();

        p = doc.createParagraph();
        run = makeRun(p);
        if (attestedAt != null) {
          run.setText("Date: " + DD_MONTH_YYYY_FORMATTER.format(attestedAt));
        } else {
          run.setText("Date: " + null);
        }
        p = doc.createParagraph();
        run = makeRun(p);

        run.setText("[Name of Requesting Entity]");
        run.setTextHighlightColor("yellow");
        run.addCarriageReturn();
        run = makeRun(p);
        run.setText("Attn: ");
        run = makeRun(p);
        run.setText("[Name of POC]");
        run.setTextHighlightColor("yellow");
        run.addCarriageReturn();
        run = makeRun(p);
        run.setText("[POC e-mail]");
        run.setTextHighlightColor("yellow");
        run.addCarriageReturn();
        run.setText("[DHA Address]");

        run = makeRun(p);

        p = doc.createParagraph();
        run = makeRun(p);
        run.setText("TQMC Case ID: " + recordMap.get("ALIAS_RECORD_ID"));
        run.addCarriageReturn();
        run = makeRun(p);
        run.setText(
            "Patient Name: "
                + recordMap.get("PATIENT_FIRST_NAME")
                + " "
                + recordMap.get("PATIENT_LAST_NAME"));
        run.addCarriageReturn();
        run = makeRun(p);
        run.setText("Patient DOB: " + dobString);
        run.setTextHighlightColor("yellow");
        p = doc.createParagraph();
        run = makeRun(p);
        run.setText(
            "Please render a determination as to the Standard of Care and identification of any systems issues which may have impacted patient care outcomes. This determination will be based upon your knowledge of clinical practice and standards of care. Even though you may have treated the patient differently, the decision is based upon generally accepted standards of care.");

        p = doc.createParagraph();
        run = makeRun(p);
        run.setBold(true);
        run.setUnderline(UnderlinePatterns.SINGLE);
        run.setText("PEER REVIEW REPORT");

        p = doc.createParagraph();
        run = makeRun(p);
        run.setText("A peer-matched ");
        run = makeRun(p);
        run.setText(
            attestationSpecialtyString); // TODO edit to replace case specialty with peer specialty
        run = makeRun(p);
        run.setText(
            " has reviewed the medical records provided and is providing an independent medical opinion. This opinion is based upon the medical records, applicable citations and based upon years of medical practice and observations in training with regard to what standard medical practice was at the time care was delivered.");

        p = doc.createParagraph();
        run = makeRun(p);
        run.setBold(true);
        run.setUnderline(UnderlinePatterns.SINGLE);
        run.setText("CASE OVERVIEW");

        listNumber = listNumber.add(BigInteger.ONE);
        BigInteger outerAbstractNum = listNumber;
        BigInteger outerNumId =
            getAbstractNumber(doc, outerAbstractNum, STNumberFormat.UPPER_LETTER, 0);

        p = doc.createParagraph();

        p.setNumID(outerNumId);
        run = makeRun(p);
        run.setBold(true);
        run.setText("Military Treatment Facility: ");
        if (!mtfNames.isEmpty()) {
          if (mtfNames.size() == 1) {
            run = makeRun(p);
            run.setText(mtfNames.get(0));
          } else {
            p.setSpacingAfter(0);

            listNumber = listNumber.add(BigInteger.ONE);
            BigInteger innerAbstractNum = listNumber;
            BigInteger innerNumId =
                getAbstractNumber(doc, innerAbstractNum, STNumberFormat.BULLET, false, 1);

            for (String mtfName : mtfNames) {
              p = doc.createParagraph();
              p.setSpacingAfter(0);
              p.setNumID(innerNumId);
              run = makeRun(p);
              run.setText(mtfName);
            }

            CTP para = p.getCTP();
            CTPPr pr = para.getPPr() == null ? para.addNewPPr() : para.getPPr();
            CTSpacing spacing = pr.getSpacing() == null ? null : pr.getSpacing();
            if (spacing != null) {
              spacing.unsetAfter();
            }
          }
        }

        p = doc.createParagraph();
        p.setNumID(outerNumId);
        run = makeRun(p);
        run.setBold(true);
        run.setText("Provider and Specialty: ");

        if (provEvals != null && !provEvals.isEmpty()) {
          p.setSpacingAfter(0);

          listNumber = listNumber.add(BigInteger.ONE);
          BigInteger innerAbstractNum = listNumber;
          BigInteger innerNumId =
              getAbstractNumber(doc, innerAbstractNum, STNumberFormat.DECIMAL, false, 1);

          for (Map<String, String> provEvalData : provEvals) {
            p = doc.createParagraph();
            p.setSpacingAfter(0);
            p.setNumID(innerNumId);
            run = makeRun(p);
            run.setText(provEvalData.get("PROVIDER_NAME") + "/");
            run = makeRun(p);
            run.setText(specialtyString);
          }

          CTP para = p.getCTP();
          CTPPr pr = para.getPPr() == null ? para.addNewPPr() : para.getPPr();
          CTSpacing spacing = pr.getSpacing() == null ? null : pr.getSpacing();
          if (spacing != null) {
            spacing.unsetAfter();
          }
        }

        p = doc.createParagraph();
        p.setNumID(outerNumId);
        run = makeRun(p);
        run.setBold(true);
        run.setText("Reviewer and Specialty: ");
        if (isComplete) {
          run = makeRun(p);
          run.setText(caseData.getReviewerFirstName() + " " + caseData.getReviewerLastName() + "/");
          run = makeRun(p);
          run.setText(
              attestationSpecialtyString); // TODO swap with peer specialty not physician specialty
        }

        p = doc.createParagraph();
        p.setNumID(outerNumId);
        run = makeRun(p);
        run.setBold(true);
        run.setText("System Issue: ");
        if (isComplete) {
          run = makeRun(p);
          run.setText(systemIssueString);
        }

        p = doc.createParagraph();
        p.setNumID(outerNumId);
        run = makeRun(p);
        run.setBold(true);
        run.setText("Period of Care Under Review: ");
        if (isCompleteNurse) {
          run = makeRun(p);
          run.setText(
              recordMap.get("PERIOD_OF_CARE_START") + " - " + recordMap.get("PERIOD_OF_CARE_END"));
        }

        p = doc.createParagraph();
        p.setNumID(outerNumId);
        run = makeRun(p);
        run.setBold(true);
        run.setText("Injury: ");
        if (isCompleteNurse) {
          run = makeRun(p);
          run.setText(recordMap.get("INJURY"));
        }

        p = doc.createParagraph();
        p.setNumID(outerNumId);
        run = makeRun(p);
        run.setBold(true);
        run.setText("Case Type: ");
        run.setTextHighlightColor("yellow");
        if (isCompleteNurse) {
          run = makeRun(p);
          run.setText("");
        }

        p = doc.createParagraph();
        p.setNumID(outerNumId);
        run = makeRun(p);
        run.setBold(true);
        run.setText("Diagnosis: ");
        if (isCompleteNurse) {
          run = makeRun(p);
          run.setText(recordMap.get("DIAGNOSES"));
        }

        // add spacing paragraph to give space
        p = doc.createParagraph();
        run = makeRun(p);
        run.setText("\n");

        listNumber = listNumber.add(BigInteger.ONE);
        outerAbstractNum = listNumber;
        outerNumId = getAbstractNumber(doc, outerAbstractNum, STNumberFormat.DECIMAL, 0);

        p = doc.createParagraph();
        p.setNumID(outerNumId);
        run = makeRun(p);
        run.setBold(true);
        run.setText("Summary of Facts of Case: ");
        if (isCompleteNurse) {
          setMultilineText(doc, recordMap.get("SUMMARY_OF_FACTS"));
        }

        p = doc.createParagraph();
        p.setNumID(outerNumId);
        run = makeRun(p);
        run.setBold(true);
        run.setText("Discuss Allegations: ");
        if (isCompleteNurse) {
          setMultilineText(doc, recordMap.get("ALLEGATIONS"));
        }

        p = doc.createParagraph();
        p.setNumID(outerNumId);
        run = makeRun(p);
        run.setBold(true);
        run.setText(
            "Was the Standard of Care met or not met for each involved provider? Please check appropriate box and document rationale for your decision for each identified provider.");

        if (provEvals != null && !provEvals.isEmpty()) {
          p.setSpacingAfter(0);

          listNumber = listNumber.add(BigInteger.ONE);
          BigInteger innerAbstractNum = listNumber;
          BigInteger innerNumId =
              getAbstractNumber(doc, innerAbstractNum, STNumberFormat.DECIMAL, 1);

          for (Map<String, String> provEvalData : provEvals) {
            p = doc.createParagraph();
            p.setNumID(innerNumId);
            run = makeRun(p);
            run.setBold(true);
            run.setUnderline(UnderlinePatterns.SINGLE);
            run.setText(provEvalData.get("PROVIDER_NAME") + "/" + specialtyString);

            p = doc.createParagraph();
            p.setIndentationLeft(900);
            p.setIndentationHanging(180);
            run = makeRun(p);
            run.setBold(true);
            run.setText("You Must Check One Box:");

            XWPFTable table = doc.createTable(1, 10);
            table.setWidth("100%");
            table.setLeftBorder(XWPFBorderType.NONE, 0, 0, null);
            table.setRightBorder(XWPFBorderType.NONE, 0, 0, null);
            table.setTopBorder(XWPFBorderType.NONE, 0, 0, null);
            table.setBottomBorder(XWPFBorderType.NONE, 0, 0, null);
            table.setInsideHBorder(XWPFBorderType.NONE, 0, 0, null);
            table.setInsideVBorder(XWPFBorderType.NONE, 0, 0, null);

            XWPFTableRow row = table.getRows().get(0);

            List<XWPFTableCell> cells = row.getTableCells();
            XWPFTableCell leftCell = cells.get(0);
            leftCell.setWidth("14%");

            XWPFTableCell cell = cells.get(1);
            cell.setWidth("3%");
            cell.setVerticalAlignment(XWPFVertAlign.CENTER);
            p = cell.getParagraphs().get(0);
            p.setAlignment(ParagraphAlignment.CENTER);
            run = makeRun(p);
            if ("yes".equals(provEvalData.get("STANDARDS_MET")) && isComplete) {
              run.addPicture(
                  new ByteArrayInputStream(checkedBytes),
                  Document.PICTURE_TYPE_PNG,
                  "checked.png",
                  Units.toEMU(14),
                  Units.toEMU(14));
            } else {
              run.addPicture(
                  new ByteArrayInputStream(uncheckedBytes),
                  Document.PICTURE_TYPE_PNG,
                  "unchecked.png",
                  Units.toEMU(14),
                  Units.toEMU(14));
            }

            cell = cells.get(2);
            cell.setVerticalAlignment(XWPFVertAlign.CENTER);
            p = cell.getParagraphs().get(0);
            p.setAlignment(ParagraphAlignment.LEFT);
            run = makeRun(p);
            run.setBold(true);
            run.setText("SOC Met");

            cell = cells.get(3);
            cell.setWidth("2%");

            cell = cells.get(4);
            cell.setWidth("3%");
            cell.setVerticalAlignment(XWPFVertAlign.CENTER);
            p = cell.getParagraphs().get(0);
            p.setAlignment(ParagraphAlignment.CENTER);
            run = makeRun(p);
            if ("no".equals(provEvalData.get("STANDARDS_MET")) && isComplete) {
              run.addPicture(
                  new ByteArrayInputStream(checkedBytes),
                  Document.PICTURE_TYPE_PNG,
                  "checked.png",
                  Units.toEMU(14),
                  Units.toEMU(14));
            } else {
              run.addPicture(
                  new ByteArrayInputStream(uncheckedBytes),
                  Document.PICTURE_TYPE_PNG,
                  "unchecked.png",
                  Units.toEMU(14),
                  Units.toEMU(14));
            }

            cell = cells.get(5);
            cell.setVerticalAlignment(XWPFVertAlign.CENTER);
            p = cell.getParagraphs().get(0);
            p.setAlignment(ParagraphAlignment.LEFT);
            run = makeRun(p);
            run.setBold(true);
            run.setText("SOC Not Met");

            cell = cells.get(6);
            cell.setWidth("2%");

            cell = cells.get(7);
            cell.setWidth("3%");
            cell.setVerticalAlignment(XWPFVertAlign.CENTER);
            p = cell.getParagraphs().get(0);
            p.setAlignment(ParagraphAlignment.CENTER);
            run = makeRun(p);
            if ("utd".equals(provEvalData.get("STANDARDS_MET")) && isComplete) {
              run.addPicture(
                  new ByteArrayInputStream(checkedBytes),
                  Document.PICTURE_TYPE_PNG,
                  "checked.png",
                  Units.toEMU(14),
                  Units.toEMU(14));
            } else {
              run.addPicture(
                  new ByteArrayInputStream(uncheckedBytes),
                  Document.PICTURE_TYPE_PNG,
                  "unchecked.png",
                  Units.toEMU(14),
                  Units.toEMU(14));
            }

            cell = cells.get(8);
            cell.setVerticalAlignment(XWPFVertAlign.CENTER);
            p = cell.getParagraphs().get(0);
            p.setAlignment(ParagraphAlignment.LEFT);
            run = makeRun(p);
            run.setBold(true);
            run.setText("Unable to Determine");

            XWPFTableCell rightCell = cells.get(9);
            rightCell.setWidth("2%");

            p = doc.createParagraph();
            p.setIndentationLeft(900);
            p.setIndentationHanging(180);
            run = makeRun(p);
            run.setBold(true);
            run.setText("Rationale for your decision:");
            if (isComplete) {
              setMultilineText(doc, provEvalData.get("STANDARDS_RATIONALE"));
            }

            p = doc.createParagraph();
            p.setIndentationLeft(900);
            p.setIndentationHanging(180);
            run = makeRun(p);
            run.setBold(true);
            run.setText("Specific Sections of the clinical record used to formulate rationale:");
            if (isComplete) {
              setMultilineText(doc, provEvalData.get("STANDARDS_JUSTIFICATION"));
            }
          }
        }
        p = doc.createParagraph();
        p.setNumID(outerNumId);
        run = makeRun(p);
        run.setBold(true);
        run.setUnderline(UnderlinePatterns.SINGLE);
        run.setText("CASE DETERMINATION");
        run = makeRun(p);
        run.setBold(true);
        run.setText(
            ": If the standard of care was not met, did the deviation from the standard of care cause or contribute to a death, disability, or malpractice claim? Please check appropriate box and document rationale for your decision for each identified provider.");

        if (provEvals != null && !provEvals.isEmpty()) {
          p.setSpacingAfter(0);

          listNumber = listNumber.add(BigInteger.ONE);
          BigInteger innerAbstractNum = listNumber;
          BigInteger innerNumId =
              getAbstractNumber(doc, innerAbstractNum, STNumberFormat.DECIMAL, 1);

          for (Map<String, String> provEvalData : provEvals) {
            p = doc.createParagraph();
            p.setNumID(innerNumId);
            run = makeRun(p);
            run.setBold(true);
            run.setUnderline(UnderlinePatterns.SINGLE);
            run.setText(provEvalData.get("PROVIDER_NAME") + "/" + specialtyString);

            p = doc.createParagraph();
            p.setIndentationLeft(900);
            p.setIndentationHanging(180);
            run = makeRun(p);
            run.setBold(true);
            run.setText("You Must Check One Box:");

            XWPFTable table = doc.createTable(1, 13);
            table.setWidth("100%");
            table.setLeftBorder(XWPFBorderType.NONE, 0, 0, null);
            table.setRightBorder(XWPFBorderType.NONE, 0, 0, null);
            table.setTopBorder(XWPFBorderType.NONE, 0, 0, null);
            table.setBottomBorder(XWPFBorderType.NONE, 0, 0, null);
            table.setInsideHBorder(XWPFBorderType.NONE, 0, 0, null);
            table.setInsideVBorder(XWPFBorderType.NONE, 0, 0, null);

            XWPFTableRow row = table.getRows().get(0);

            List<XWPFTableCell> cells = row.getTableCells();
            XWPFTableCell leftCell = cells.get(0);
            leftCell.setWidth("14%");

            XWPFTableCell cell = cells.get(1);
            cell.setWidth("3%");
            cell.setVerticalAlignment(XWPFVertAlign.CENTER);
            p = cell.getParagraphs().get(0);
            p.setAlignment(ParagraphAlignment.CENTER);
            run = makeRun(p);
            if ("yes".equals(provEvalData.get("DEVIATION_CLAIM")) && isComplete) {
              run.addPicture(
                  new ByteArrayInputStream(checkedBytes),
                  Document.PICTURE_TYPE_PNG,
                  "checked.png",
                  Units.toEMU(14),
                  Units.toEMU(14));
            } else {
              run.addPicture(
                  new ByteArrayInputStream(uncheckedBytes),
                  Document.PICTURE_TYPE_PNG,
                  "unchecked.png",
                  Units.toEMU(14),
                  Units.toEMU(14));
            }

            cell = cells.get(2);
            cell.setVerticalAlignment(XWPFVertAlign.CENTER);
            p = cell.getParagraphs().get(0);
            p.setAlignment(ParagraphAlignment.LEFT);
            run = makeRun(p);
            run.setBold(true);
            run.setText("Yes");

            cell = cells.get(3);
            cell.setWidth("2%");

            cell = cells.get(4);
            cell.setWidth("3%");
            cell.setVerticalAlignment(XWPFVertAlign.CENTER);
            p = cell.getParagraphs().get(0);
            p.setAlignment(ParagraphAlignment.CENTER);
            run = makeRun(p);
            if ("no".equals(provEvalData.get("DEVIATION_CLAIM")) && isComplete) {
              run.addPicture(
                  new ByteArrayInputStream(checkedBytes),
                  Document.PICTURE_TYPE_PNG,
                  "checked.png",
                  Units.toEMU(14),
                  Units.toEMU(14));
            } else {
              run.addPicture(
                  new ByteArrayInputStream(uncheckedBytes),
                  Document.PICTURE_TYPE_PNG,
                  "unchecked.png",
                  Units.toEMU(14),
                  Units.toEMU(14));
            }

            cell = cells.get(5);
            cell.setVerticalAlignment(XWPFVertAlign.CENTER);
            p = cell.getParagraphs().get(0);
            p.setAlignment(ParagraphAlignment.LEFT);
            run = makeRun(p);
            run.setBold(true);
            run.setText("No");

            cell = cells.get(6);
            cell.setWidth("2%");

            cell = cells.get(7);
            cell.setWidth("3%");
            cell.setVerticalAlignment(XWPFVertAlign.CENTER);
            p = cell.getParagraphs().get(0);
            p.setAlignment(ParagraphAlignment.CENTER);
            run = makeRun(p);
            if ("utd".equals(provEvalData.get("DEVIATION_CLAIM")) && isComplete) {
              run.addPicture(
                  new ByteArrayInputStream(checkedBytes),
                  Document.PICTURE_TYPE_PNG,
                  "checked.png",
                  Units.toEMU(14),
                  Units.toEMU(14));
            } else {
              run.addPicture(
                  new ByteArrayInputStream(uncheckedBytes),
                  Document.PICTURE_TYPE_PNG,
                  "unchecked.png",
                  Units.toEMU(14),
                  Units.toEMU(14));
            }

            cell = cells.get(8);
            cell.setVerticalAlignment(XWPFVertAlign.CENTER);
            p = cell.getParagraphs().get(0);
            p.setAlignment(ParagraphAlignment.LEFT);
            run = makeRun(p);
            run.setBold(true);
            run.setText("Unable to Determine");

            cell = cells.get(9);
            cell.setWidth("2%");

            cell = cells.get(10);
            cell.setWidth("3%");
            cell.setVerticalAlignment(XWPFVertAlign.CENTER);
            p = cell.getParagraphs().get(0);
            p.setAlignment(ParagraphAlignment.CENTER);
            run = makeRun(p);
            if ("na".equals(provEvalData.get("DEVIATION_CLAIM")) && isComplete) {
              run.addPicture(
                  new ByteArrayInputStream(checkedBytes),
                  Document.PICTURE_TYPE_PNG,
                  "checked.png",
                  Units.toEMU(14),
                  Units.toEMU(14));
            } else {
              run.addPicture(
                  new ByteArrayInputStream(uncheckedBytes),
                  Document.PICTURE_TYPE_PNG,
                  "unchecked.png",
                  Units.toEMU(14),
                  Units.toEMU(14));
            }

            cell = cells.get(11);
            cell.setVerticalAlignment(XWPFVertAlign.CENTER);
            p = cell.getParagraphs().get(0);
            p.setAlignment(ParagraphAlignment.LEFT);
            run = makeRun(p);
            run.setBold(true);
            run.setText("Non-applicable SOC Met");

            XWPFTableCell rightCell = cells.get(12);
            rightCell.setWidth("2%");

            p = doc.createParagraph();
            p.setIndentationLeft(900);
            p.setIndentationHanging(180);
            run = makeRun(p);
            run.setBold(true);
            run.setText("Rationale for your decision:");
            if (isComplete) {
              setMultilineText(doc, provEvalData.get("DEVIATION_CLAIM_RATIONALE"));
            }

            p = doc.createParagraph();
            p.setIndentationLeft(900);
            p.setIndentationHanging(180);
            run = makeRun(p);
            run.setBold(true);
            run.setText("Specific Sections of the clinical record used to formulate rationale:");
            if (isComplete) {
              setMultilineText(doc, provEvalData.get("DEVIATION_CLAIM_JUSTIFICATION"));
            }
          }
        }

        p = doc.createParagraph();
        p.setNumID(outerNumId);
        run = makeRun(p);
        run.setBold(true);
        run.setUnderline(UnderlinePatterns.SINGLE);
        run.setText("REFERENCES");
        run = makeRun(p);
        run.setBold(true);
        run.setText(
            ": Practice guidelines/Evidence-based citations must be provided. Copies of articles may be submitted. Please Note: references consulted must be \"at the time of\" or \"prior to\" the actual delivery of care.");
        if (isComplete) {
          setMultilineText(doc, caseData.getSystemReferences());
        }

        p = doc.createParagraph();
        p.setNumID(outerNumId);
        run = makeRun(p);
        run.setBold(true);
        run.setText("Did this case show evidence of a systems issue?");

        p = doc.createParagraph();
        p.setIndentationLeft(900);
        p.setIndentationHanging(180);
        run = makeRun(p);
        run.setBold(true);
        run.setText("You Must Check One Box:");

        XWPFTable table = doc.createTable(1, 10);
        table.setWidth("100%");
        table.setLeftBorder(XWPFBorderType.NONE, 0, 0, null);
        table.setRightBorder(XWPFBorderType.NONE, 0, 0, null);
        table.setTopBorder(XWPFBorderType.NONE, 0, 0, null);
        table.setBottomBorder(XWPFBorderType.NONE, 0, 0, null);
        table.setInsideHBorder(XWPFBorderType.NONE, 0, 0, null);
        table.setInsideVBorder(XWPFBorderType.NONE, 0, 0, null);

        XWPFTableRow row = table.getRows().get(0);

        List<XWPFTableCell> cells = row.getTableCells();
        XWPFTableCell leftCell = cells.get(0);
        leftCell.setWidth("14%");

        XWPFTableCell cell = cells.get(1);
        cell.setWidth("3%");
        cell.setVerticalAlignment(XWPFVertAlign.CENTER);
        p = cell.getParagraphs().get(0);
        p.setAlignment(ParagraphAlignment.CENTER);
        run = makeRun(p);
        if ("yes".equals(caseData.getSystemIssue()) && isComplete) {
          run.addPicture(
              new ByteArrayInputStream(checkedBytes),
              Document.PICTURE_TYPE_PNG,
              "checked.png",
              Units.toEMU(14),
              Units.toEMU(14));
        } else {
          run.addPicture(
              new ByteArrayInputStream(uncheckedBytes),
              Document.PICTURE_TYPE_PNG,
              "unchecked.png",
              Units.toEMU(14),
              Units.toEMU(14));
        }

        cell = cells.get(2);
        cell.setVerticalAlignment(XWPFVertAlign.CENTER);
        p = cell.getParagraphs().get(0);
        p.setAlignment(ParagraphAlignment.LEFT);
        run = makeRun(p);
        run.setBold(true);
        run.setText("Yes");

        cell = cells.get(3);
        cell.setWidth("2%");

        cell = cells.get(4);
        cell.setWidth("3%");
        cell.setVerticalAlignment(XWPFVertAlign.CENTER);
        p = cell.getParagraphs().get(0);
        p.setAlignment(ParagraphAlignment.CENTER);
        run = makeRun(p);
        if ("no".equals(caseData.getSystemIssue()) && isComplete) {
          run.addPicture(
              new ByteArrayInputStream(checkedBytes),
              Document.PICTURE_TYPE_PNG,
              "checked.png",
              Units.toEMU(14),
              Units.toEMU(14));
        } else {
          run.addPicture(
              new ByteArrayInputStream(uncheckedBytes),
              Document.PICTURE_TYPE_PNG,
              "unchecked.png",
              Units.toEMU(14),
              Units.toEMU(14));
        }

        cell = cells.get(5);
        cell.setVerticalAlignment(XWPFVertAlign.CENTER);
        p = cell.getParagraphs().get(0);
        p.setAlignment(ParagraphAlignment.LEFT);
        run = makeRun(p);
        run.setBold(true);
        run.setText("No");

        cell = cells.get(6);
        cell.setWidth("2%");

        cell = cells.get(7);
        cell.setWidth("3%");
        cell.setVerticalAlignment(XWPFVertAlign.CENTER);
        p = cell.getParagraphs().get(0);
        p.setAlignment(ParagraphAlignment.CENTER);
        run = makeRun(p);
        if ("utd".equals(caseData.getSystemIssue()) && isComplete) {
          run.addPicture(
              new ByteArrayInputStream(checkedBytes),
              Document.PICTURE_TYPE_PNG,
              "checked.png",
              Units.toEMU(14),
              Units.toEMU(14));
        } else {
          run.addPicture(
              new ByteArrayInputStream(uncheckedBytes),
              Document.PICTURE_TYPE_PNG,
              "unchecked.png",
              Units.toEMU(14),
              Units.toEMU(14));
        }

        cell = cells.get(8);
        cell.setVerticalAlignment(XWPFVertAlign.CENTER);
        p = cell.getParagraphs().get(0);
        p.setAlignment(ParagraphAlignment.LEFT);
        run = makeRun(p);
        run.setBold(true);
        run.setText("Unable to Determine");

        XWPFTableCell rightCell = cells.get(9);
        rightCell.setWidth("2%");

        p = doc.createParagraph();
        p.setIndentationLeft(900);
        p.setIndentationHanging(180);
        run = makeRun(p);
        run.setBold(true);
        run.setText("Rationale for your decision:");
        if (isComplete) {
          setMultilineText(doc, caseData.getSystemIssueRationale());
        }

        p = doc.createParagraph();
        p.setIndentationLeft(900);
        p.setIndentationHanging(180);
        run = makeRun(p);
        run.setBold(true);
        run.setText("Specific Section(s) of the clinical record used to formulate rationale:");
        if (isComplete) {
          setMultilineText(doc, caseData.getSystemIssueJustification());
        }
      }

      String docxFileName = "SOC_Report_" + recordId + ".docx";
      String docxFilePathString = Utility.getUniqueFilePath(insightFileDirectory, docxFileName);
      File docx = new File(docxFilePathString);
      try (FileOutputStream out = new FileOutputStream(docx)) {
        doc.write(out);
      }
      return docx;
    } catch (IOException | InvalidFormatException e) {
      throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to generate report file", e);
    }
  }

  private void loadData(Connection con) throws SQLException {
    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT sr.PATIENT_DOB, sr.PATIENT_FIRST_NAME, sr.PATIENT_LAST_NAME, "
                + "sr.ALIAS_RECORD_ID, sr.REQUEST_DATE, sr.REVIEW_DEADLINE, "
                + "snr.ALLEGATIONS, snr.DIAGNOSES, snr.INJURY, snr.PERIOD_OF_CARE_START, snr.PERIOD_OF_CARE_END, snr.SUMMARY_OF_FACTS, sw.STEP_STATUS "
                + "FROM SOC_RECORD sr "
                + "LEFT JOIN SOC_NURSE_REVIEW snr ON snr.RECORD_ID = sr.RECORD_ID AND snr.DELETED_AT IS NULL AND snr.SUBMISSION_GUID IS NULL "
                + "LEFT JOIN SOC_WORKFLOW sw ON sw.CASE_ID = snr.NURSE_REVIEW_ID AND sw.IS_LATEST = 1 AND sw.STEP_STATUS = 'completed' "
                + "WHERE sr.RECORD_ID = ?")) {
      ps.setString(1, recordId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        if (rs.next()) {
          recordMap.put("PATIENT_DOB", rs.getString("PATIENT_DOB"));
          recordMap.put("PATIENT_FIRST_NAME", rs.getString("PATIENT_FIRST_NAME"));
          recordMap.put("PATIENT_LAST_NAME", rs.getString("PATIENT_LAST_NAME"));
          recordMap.put("ALIAS_RECORD_ID", rs.getString("ALIAS_RECORD_ID"));
          recordMap.put("REQUEST_DATE", rs.getString("REQUEST_DATE"));
          recordMap.put("REVIEW_DEADLINE", rs.getString("REVIEW_DEADLINE"));

          recordMap.put("ALLEGATIONS", rs.getString("ALLEGATIONS"));
          recordMap.put("DIAGNOSES", rs.getString("DIAGNOSES"));
          recordMap.put("INJURY", rs.getString("INJURY"));
          recordMap.put("PERIOD_OF_CARE_START", rs.getString("PERIOD_OF_CARE_START"));
          recordMap.put("PERIOD_OF_CARE_END", rs.getString("PERIOD_OF_CARE_END"));
          recordMap.put("SUMMARY_OF_FACTS", rs.getString("SUMMARY_OF_FACTS"));
          recordMap.put("STEP_STATUS", rs.getString("STEP_STATUS"));
        } else {
          throw new TQMCException(ErrorCode.NOT_FOUND);
        }
      }
    }

    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT m.MTF_NAME "
                + "FROM SOC_RECORD sr LEFT JOIN SOC_RECORD_MTF srm ON srm.RECORD_ID = sr.RECORD_ID "
                + "LEFT JOIN MTF m ON m.DMIS_ID = srm.DMIS_ID "
                + "WHERE sr.RECORD_ID = ? ORDER BY m.MTF_NAME ASC")) {
      ps.setString(1, recordId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {
          mtfNames.add(rs.getString(1));
        }
      }
    }

    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT sc.CASE_ID, sw.STEP_STATUS, sc.ATTESTATION_SIGNATURE, sc.ATTESTED_AT, "
                + "ts.SPECIALTY_NAME, ts.SUBSPECIALTY_NAME, "
                + "tu.FIRST_NAME, tu.LAST_NAME, "
                + "scse.REFERENCES, scse.SYSTEM_ISSUE, scse.SYSTEM_ISSUE_JUSTIFICATION, scse.SYSTEM_ISSUE_RATIONALE, "
                + "scpe.DEVIATION_CLAIM, scpe.DEVIATION_CLAIM_JUSTIFICATION, scpe.DEVIATION_CLAIM_RATIONALE, "
                + "scpe.PROVIDER_NAME, scpe.STANDARDS_JUSTIFICATION, scpe.STANDARDS_MET, scpe.STANDARDS_RATIONALE "
                + ", sc.ATTESTATION_SPECIALTY, sc.ATTESTATION_SUBSPECIALTY "
                + "FROM SOC_CASE sc LEFT JOIN TQMC_SPECIALTY ts ON ts.SPECIALTY_ID = sc.SPECIALTY_ID "
                + "LEFT JOIN TQMC_USER tu ON tu.USER_ID = sc.USER_ID "
                + "LEFT JOIN SOC_CASE_SYSTEM_EVALUATION scse ON scse.CASE_ID = sc.CASE_ID "
                + "LEFT JOIN SOC_CASE_PROVIDER_EVALUATION scpe ON scpe.CASE_ID = sc.CASE_ID AND scpe.DELETED_AT IS NULL "
                + "LEFT JOIN SOC_WORKFLOW sw ON sw.CASE_ID = sc.CASE_ID AND sw.IS_LATEST = 1 "
                + "WHERE sc.RECORD_ID = ? AND sc.DELETED_AT IS NULL AND sc.SUBMISSION_GUID IS NULL "
                + "ORDER BY sc.CASE_ID ASC, scpe.CASE_PROVIDER_EVALUATION_ID ")) {
      ps.setString(1, recordId);
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {

          String caseId = rs.getString("CASE_ID");

          if (!caseDataMap.containsKey(caseId)) {

            SocData socData = new SocData();
            socData.setStepStatus(rs.getString("STEP_STATUS"));
            socData.setAttestationSignature(rs.getString("ATTESTATION_SIGNATURE"));
            socData.setAttestedAt(rs.getObject("ATTESTED_AT", LocalDateTime.class));
            socData.setSpecialtyName(rs.getString("SPECIALTY_NAME"));
            socData.setSubspecialtyName(rs.getString("SUBSPECIALTY_NAME"));
            socData.setReviewerFirstName(rs.getString("FIRST_NAME"));
            socData.setReviewerLastName(rs.getString("LAST_NAME"));
            socData.setSystemReferences(rs.getString("REFERENCES"));
            socData.setSystemIssue(rs.getString("SYSTEM_ISSUE"));
            socData.setSystemIssueJustification(rs.getString("SYSTEM_ISSUE_JUSTIFICATION"));
            socData.setSystemIssueRationale(rs.getString("SYSTEM_ISSUE_RATIONALE"));
            socData.setAttestationSpecialty(rs.getString("ATTESTATION_SPECIALTY"));
            socData.setAttestationSubspecialty(rs.getString("ATTESTATION_SUBSPECIALTY"));
            caseDataMap.put(caseId, socData);
          }

          Map<String, String> providerEval = new HashMap<>();
          providerEval.put("DEVIATION_CLAIM", rs.getString("DEVIATION_CLAIM"));
          providerEval.put(
              "DEVIATION_CLAIM_JUSTIFICATION", rs.getString("DEVIATION_CLAIM_JUSTIFICATION"));
          providerEval.put("DEVIATION_CLAIM_RATIONALE", rs.getString("DEVIATION_CLAIM_RATIONALE"));
          providerEval.put("PROVIDER_NAME", rs.getString("PROVIDER_NAME"));
          providerEval.put("STANDARDS_JUSTIFICATION", rs.getString("STANDARDS_JUSTIFICATION"));
          providerEval.put("STANDARDS_MET", rs.getString("STANDARDS_MET"));
          providerEval.put("STANDARDS_RATIONALE", rs.getString("STANDARDS_RATIONALE"));

          List<Map<String, String>> providerEvals = providerEvalsByCaseId.get(caseId);
          if (providerEvals == null) {
            providerEvals = new ArrayList<>();
            providerEvalsByCaseId.put(caseId, providerEvals);
          }
          providerEvals.add(providerEval);
        }
      }
    }
  }

  private XWPFRun makeRun(XWPFParagraph paragraph) {
    return makeRun(paragraph, 24);
  }

  private XWPFRun makeRun(XWPFParagraph paragraph, int doubleSize) {
    XWPFRun run = paragraph.createRun();
    run.setFontFamily("Arial");

    CTR ctr = run.getCTR();
    CTRPr pr = ctr.isSetRPr() ? ctr.getRPr() : ctr.addNewRPr();
    CTHpsMeasure ctSize;
    if (pr.getSzList().size() > 0) {
      ctSize = pr.getSzList().get(0);
    } else {
      ctSize = pr.addNewSz();
    }
    ctSize.setVal(new BigInteger(Integer.toString(doubleSize)));

    return run;
  }

  private void setMultilineText(XWPFDocument doc, String text) {
    if (text == null) {
      return;
    }

    String[] lines = text.split("\n+");
    for (String line : lines) {
      XWPFParagraph p = doc.createParagraph();
      XWPFRun run = makeRun(p);
      run.setText(line);
    }
  }

  private BigInteger getAbstractNumber(
      XWPFDocument document, BigInteger abstractNumID, STNumberFormat.Enum numFmt, int level) {
    return getAbstractNumber(document, abstractNumID, numFmt, true, level);
  }

  private BigInteger getAbstractNumber(
      XWPFDocument document,
      BigInteger abstractNumID,
      STNumberFormat.Enum numFmt,
      boolean bold,
      int level) {

    CTAbstractNum ctAbsNum = CTAbstractNum.Factory.newInstance();
    ctAbsNum.setAbstractNumId(abstractNumID);

    CTLvl lvl = ctAbsNum.addNewLvl();

    CTDecimalNumber start = lvl.addNewStart();
    start.setVal(BigInteger.ONE);

    CTNumFmt fmt = lvl.addNewNumFmt();
    fmt.setVal(numFmt);

    CTLevelText lt = lvl.addNewLvlText();
    if (numFmt == STNumberFormat.BULLET) {
      if (level % 2 == 0) {
        lt.setVal("\u2022");
      } else {
        lt.setVal("\u25E6");
      }
    } else {
      lt.setVal("%1.");
    }

    lvl.addNewRPr();
    CTRPr rpr = lvl.getRPr();
    rpr.addNewSz().setVal(BigInteger.valueOf(24));
    rpr.addNewSzCs().setVal(BigInteger.valueOf(24));
    if (bold) {
      CTOnOff boldElement = rpr.addNewB();
      boldElement.setVal(true); // Alternative to STOnOff.ON
    }
    CTFonts f = rpr.addNewRFonts();
    f.setAscii("Arial");
    f.setHAnsi("Arial");

    lvl.addNewPPr();
    CTInd ind = lvl.getPPr().addNewInd();
    ind.setHanging(BigInteger.valueOf(360));
    ind.setLeft(BigInteger.valueOf(720 + level * 360));

    XWPFAbstractNum abstractNum = new XWPFAbstractNum(ctAbsNum);

    XWPFNumbering numbering = document.createNumbering();

    abstractNumID = numbering.addAbstractNum(abstractNum);

    BigInteger numID = numbering.addNum(abstractNumID);

    return numID;
  }

  private void setCaseReviewSectionHeaderAndFooter(XWPFDocument doc, String tag) {
    CTSectPr section;

    CTBody body = doc.getDocument().getBody();
    CTSectPr sectPr = body.getSectPr();
    if (sectPr != null) {
      XWPFParagraph lastParagraph = doc.getLastParagraph();
      lastParagraph.getCTP().addNewPPr().setSectPr(sectPr);
      body.unsetSectPr();
    }
    section = body.addNewSectPr();

    XWPFHeaderFooterPolicy headerFooterPolicy = new XWPFHeaderFooterPolicy(doc);
    XWPFHeader header = headerFooterPolicy.createHeader(XWPFHeaderFooterPolicy.DEFAULT);

    XWPFTable table = header.createTable(1, 2);
    table.setWidth("100%");
    table.setLeftBorder(XWPFBorderType.NONE, 0, 0, null);
    table.setRightBorder(XWPFBorderType.NONE, 0, 0, null);
    table.setTopBorder(XWPFBorderType.NONE, 0, 0, null);
    table.setBottomBorder(XWPFBorderType.NONE, 0, 0, null);
    table.setInsideHBorder(XWPFBorderType.NONE, 0, 0, null);
    table.setInsideVBorder(XWPFBorderType.NONE, 0, 0, null);

    XWPFTableRow row = table.getRows().get(0);

    List<XWPFTableCell> cells = row.getTableCells();
    XWPFTableCell leftHeaderCell = cells.get(0);
    leftHeaderCell.setWidth("50%");
    leftHeaderCell.setVerticalAlignment(XWPFVertAlign.CENTER);
    XWPFParagraph paragraph = leftHeaderCell.getParagraphs().get(0);
    paragraph.setAlignment(ParagraphAlignment.LEFT);
    XWPFRun run = makeRun(paragraph);
    try (FileInputStream delLogo =
        new FileInputStream(
            Paths.get(projectAssetDirectory, "pdf_resources/TQMC.png").normalize().toString())) {
      run.addPicture(
          delLogo, Document.PICTURE_TYPE_PNG, "TQMC.png", Units.toEMU(118), Units.toEMU(45));
    } catch (IOException | InvalidFormatException e) {
      LOGGER.warn("Failed to create report header", e);
    }

    XWPFTableCell rightHeaderCell = cells.get(1);
    paragraph = rightHeaderCell.getParagraphs().get(0);
    rightHeaderCell.setWidth("50%");
    rightHeaderCell.setVerticalAlignment(XWPFVertAlign.CENTER);
    paragraph.setAlignment(ParagraphAlignment.RIGHT);
    run = makeRun(paragraph);
    try (FileInputStream dhaLogo =
        new FileInputStream(
            Paths.get(projectAssetDirectory, "pdf_resources/DHA-Logo.png")
                .normalize()
                .toString())) {
      run.addPicture(
          dhaLogo, Document.PICTURE_TYPE_PNG, "DHA-Logo.png", Units.toEMU(95), Units.toEMU(45));
    } catch (IOException | InvalidFormatException e) {
      LOGGER.warn("Failed to create report header", e);
    }

    XWPFFooter footer = headerFooterPolicy.createFooter(XWPFHeaderFooterPolicy.DEFAULT);

    paragraph = footer.createParagraph();
    paragraph.setAlignment(ParagraphAlignment.CENTER);
    run = makeRun(paragraph);
    run.setItalic(true);
    run.setFontSize(8);
    run.setText(
        "This document is confidential Medical Quality Assurance information and is exempt from discovery and restricted from release, in accordance with 10 U.S.C. and 5 U.S.C. 552a. Information contained in this correspondence may be used only by authorized personnel in the conduct of official business. Any unauthorized discloser or misuse of information may result in criminal and/or civil penalties. If you are not the intended recipient of this correspondence, please destroy all copies of the correspondence after notifying the sender of your receipt of it.");

    paragraph = footer.createParagraph();
    paragraph.setAlignment(ParagraphAlignment.CENTER);
    run = makeRun(paragraph);
    run.setItalic(true);
    run.setFontSize(10);
    run.setText(tag);

    CTPageMar pageMar = section.getPgMar();
    pageMar = section.addNewPgMar();

    // 720 = 1 inch, 360 = 0.5 inch (1/2"), 180 = 0.25 inch (1/4"), 90 = 0.125
    // (1/8"), 45 = 0.0625 (1/16")
    pageMar.setLeft(BigInteger.valueOf(1440));
    pageMar.setRight(BigInteger.valueOf(1440));
    pageMar.setTop(BigInteger.valueOf(1800));
    pageMar.setBottom(BigInteger.valueOf(1935));

    pageMar.setHeader(BigInteger.valueOf(720));
    pageMar.setFooter(BigInteger.valueOf(495));
  }

  private class SocData {

    private String stepStatus;
    private String attestationSignature;
    private LocalDateTime attestedAt;
    private String specialtyName;
    private String subspecialtyName;
    private String reviewerFirstName;
    private String reviewerLastName;
    private String systemReferences;
    private String systemIssue;
    private String systemIssueJustification;
    private String systemIssueRationale;
    private String attestationSpecialty;
    private String attestationSubspecialty;

    public String getStepStatus() {
      return stepStatus;
    }

    public void setStepStatus(String stepStatus) {
      this.stepStatus = stepStatus;
    }

    public String getAttestationSignature() {
      return attestationSignature;
    }

    public void setAttestationSignature(String attestationSignature) {
      this.attestationSignature = attestationSignature;
    }

    public LocalDateTime getAttestedAt() {
      return attestedAt;
    }

    public void setAttestedAt(LocalDateTime attestedAt) {
      this.attestedAt = attestedAt;
    }

    public String getSpecialtyName() {
      return specialtyName;
    }

    public void setSpecialtyName(String specialtyName) {
      this.specialtyName = specialtyName;
    }

    public String getSubspecialtyName() {
      return subspecialtyName;
    }

    public void setSubspecialtyName(String subspecialtyName) {
      this.subspecialtyName = subspecialtyName;
    }

    public String getReviewerFirstName() {
      return reviewerFirstName;
    }

    public void setReviewerFirstName(String reviewerFirstName) {
      this.reviewerFirstName = reviewerFirstName;
    }

    public String getReviewerLastName() {
      return reviewerLastName;
    }

    public void setReviewerLastName(String reviewerLastName) {
      this.reviewerLastName = reviewerLastName;
    }

    public String getSystemReferences() {
      return systemReferences;
    }

    public void setSystemReferences(String systemReferences) {
      this.systemReferences = systemReferences;
    }

    public String getSystemIssue() {
      return systemIssue;
    }

    public void setSystemIssue(String systemIssue) {
      this.systemIssue = systemIssue;
    }

    public String getSystemIssueJustification() {
      return systemIssueJustification;
    }

    public void setSystemIssueJustification(String systemIssueJustification) {
      this.systemIssueJustification = systemIssueJustification;
    }

    public String getSystemIssueRationale() {
      return systemIssueRationale;
    }

    public void setSystemIssueRationale(String systemIssueRationale) {
      this.systemIssueRationale = systemIssueRationale;
    }

    public String getAttestationSpecialty() {
      return attestationSpecialty;
    }

    public void setAttestationSpecialty(String attestationSpecialty) {
      this.attestationSpecialty = attestationSpecialty;
    }

    public String getAttestationSubspecialty() {
      return attestationSubspecialty;
    }

    public void setAttestationSubspecialty(String attestationSubspecialty) {
      this.attestationSubspecialty = attestationSubspecialty;
    }
  }
}
