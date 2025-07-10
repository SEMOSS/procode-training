package tqmc.reactors.mn;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableCell.XWPFVertAlign;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute.Space;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;
import prerna.om.InsightFile;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.PixelOperationType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.AssetUtility;
import prerna.util.Utility;
import prerna.util.ZipUtils;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.mn.MnWorkflow;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.reactors.soc.GetSocReportReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class GetMnReportReactor extends AbstractTQMCReactor {
  private static final Logger LOGGER = LogManager.getLogger(GetSocReportReactor.class);
  private static final String OVERALL_QUERY_TEMPLATE =
      "SELECT"
          + " mnc.RECORD_ID, "
          + " mnw.CASE_ID AS REVIEW_ID, "
          + " mnc.USER_ID AS PEER_REVIEWER, "
          + " mnc.RECOMMENDATION_RESPONSE as REC_RESPONSE, "
          + " mnc.RECOMMENDATION_EXPLANATION as REC_EXPLANATION, "
          + " mat.APPEAL_TYPE_NAME, mat.IS_SECOND_LEVEL,"
          + " mr.ALIAS_RECORD_ID, mr.FACILITY_NAME, mr.CARE_CONTRACTOR_NAME,"
          + " mr.PATIENT_FIRST_NAME, mr.PATIENT_LAST_NAME,"
          + " mnw.SMSS_TIMESTAMP AS COMPLETED_DATE, "
          + " ts.SPECIALTY_NAME AS SPECIALTY_NAME, ts.SUBSPECIALTY_NAME AS SUBSPECIALTY_NAME "
          + "FROM "
          + " MN_WORKFLOW mnw "
          + "JOIN MN_CASE mnc ON "
          + " mnc.CASE_ID = mnw.CASE_ID "
          + "JOIN MN_RECORD mr ON mr.RECORD_ID = mnc.RECORD_ID AND mnc.SUBMISSION_GUID IS NULL "
          + "LEFT JOIN TQMC_SPECIALTY ts ON "
          + " mnc.SPECIALTY_ID = ts.SPECIALTY_ID "
          + "LEFT JOIN MN_APPEAL_TYPE mat ON mnc.APPEAL_TYPE_ID = mat.APPEAL_TYPE_ID "
          + "WHERE mnw.CASE_ID = ? "
          + "AND mnw.IS_LATEST = 1 "
          + "AND mnw.STEP_STATUS = 'completed' "
          + "ORDER BY mnw.SMSS_TIMESTAMP ASC, mnc.RECORD_ID ASC";
  private static final String QUESTION_QUERY_TEMPLATE =
      "SELECT"
          + " mnq.CASE_QUESTION AS QUESTION, "
          + " mnq.QUESTION_RATIONALE AS RATIONALE, "
          + " mnq.QUESTION_REFERENCE AS RECORD_REFERENCES, "
          + " mnq.QUESTION_RESPONSE AS RESPONSE "
          + "FROM "
          + " MN_WORKFLOW mnw "
          + "JOIN MN_CASE mnc ON "
          + " mnc.CASE_ID = mnw.CASE_ID "
          + "JOIN MN_CASE_QUESTION mnq ON "
          + " mnq.CASE_ID = mnw.CASE_ID "
          + "WHERE mnw.CASE_ID = ? "
          + "AND mnw.IS_LATEST = 1 "
          + "AND mnw.STEP_STATUS = 'completed' "
          + "AND mnq.DELETED_AT is NULL "
          + "ORDER BY mnq.QUESTION_NUMBER ASC";

  private static final String ATTESTED_QUERY =
      "SELECT"
          + " mnc.RECORD_ID AS RECORD_ID, "
          + " mnc.ATTESTATION_SIGNATURE AS attestation_signature, "
          + " mnc.ATTESTED_AT AS attested_at, "
          + " mnc.ATTESTATION_SPECIALTY AS SPECIALTY_NAME, "
          + " mnc.ATTESTATION_SUBSPECIALTY AS SUBSPECIALTY_NAME, "
          + " mr.ALIAS_RECORD_ID AS ALIAS_RECORD_ID "
          + "FROM "
          + " MN_CASE mnc "
          + "JOIN MN_WORKFLOW mnw ON "
          + " mnw.CASE_ID = mnc.CASE_ID "
          + "LEFT JOIN TQMC_SPECIALTY ts ON "
          + " mnc.SPECIALTY_ID = ts.SPECIALTY_ID "
          + "LEFT JOIN MN_RECORD mr ON "
          + " mr.RECORD_ID = mnc.RECORD_ID AND mnc.SUBMISSION_GUID IS NULL "
          + "WHERE "
          + " mnc.CASE_ID = ? "
          + "AND mnw.IS_LATEST = 1 "
          + "AND mnw.STEP_STATUS = 'completed'";

  private static final String FOOTER_CORE_TEXT =
      "If you have any questions, please contact us at tqmc@deloitte.com or 1-610-479-3250. \r\n\r\n"
          + "Thank you. \r\n"
          + "TQMC Medical Review Team  \r\n\r\n"
          + "\r\n"
          + "Attachments: \r\n"
          + " 1. Peer Reviewer CV/Professional Qualification Statement \r\n"
          + " 2. Peer Reviewer Attestation";

  private static final String FOOTER_TEXT =
      "TQMC Appeals | 2941 Fairview Park Drive 4th Floor, Falls Church, VA 22042\r\n"
          + "TQMC Hotline: 1-610-479-3250 | FAX 1-866-420-2852 | tqmc@deloitte.com";

  public GetMnReportReactor() {
    this.keysToGet = new String[] {TQMCConstants.CASE_ID};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    if (!hasProductManagementPermission(TQMCConstants.MN)) {
      throw new TQMCException(ErrorCode.FORBIDDEN, "User doesn't have the correct Permissions");
    }

    String caseId = this.keyValue.get(TQMCConstants.CASE_ID);
    MnWorkflow checker = TQMCHelper.getLatestMnWorkflow(con, caseId);
    if (!TQMCConstants.CASE_STEP_STATUS_COMPLETED.equals(checker.getStepStatus())) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Unable to export an incomplete case");
    }

    // Setting up file paths
    String fileDirectory =
        AssetUtility.getRootFolderPath(this.insight, AssetUtility.INSIGHT_SPACE_KEY, true);

    Map<String, String> overallQueryMap = new LinkedHashMap<>();

    try (PreparedStatement report = con.prepareStatement(OVERALL_QUERY_TEMPLATE)) {
      report.setString(1, caseId);
      ResultSet rs = report.executeQuery();

      if (rs.next()) {
        overallQueryMap.put("RECORD_ID", rs.getString("RECORD_ID"));
        overallQueryMap.put("REVIEW_ID", rs.getString("REVIEW_ID"));
        overallQueryMap.put("PEER_REVIEWER", rs.getString("PEER_REVIEWER"));
        overallQueryMap.put("REC_RESPONSE", rs.getString("REC_RESPONSE"));
        overallQueryMap.put("REC_EXPLANATION", rs.getString("REC_EXPLANATION"));
        overallQueryMap.put("APPEAL_TYPE_NAME", rs.getString("APPEAL_TYPE_NAME"));
        overallQueryMap.put("IS_SECOND_LEVEL", String.valueOf(rs.getInt("IS_SECOND_LEVEL") == 1));
        overallQueryMap.put("ALIAS_RECORD_ID", rs.getString("ALIAS_RECORD_ID"));
        overallQueryMap.put("FACILITY_NAME", rs.getString("FACILITY_NAME"));
        overallQueryMap.put("CARE_CONTRACTOR_NAME", rs.getString("CARE_CONTRACTOR_NAME"));
        overallQueryMap.put("PATIENT_FIRST_NAME", rs.getString("PATIENT_FIRST_NAME"));
        overallQueryMap.put("PATIENT_LAST_NAME", rs.getString("PATIENT_LAST_NAME"));
        overallQueryMap.put(
            "COMPLETED_DATE",
            ConversionUtils.getLocalDateStringFromDateSlashes(rs.getDate("COMPLETED_DATE")));
        overallQueryMap.put("SPECIALTY_NAME", rs.getString("SPECIALTY_NAME"));
        overallQueryMap.put("SUBSPECIALTY_NAME", rs.getString("SUBSPECIALTY_NAME"));

      } else {
        throw new TQMCException(ErrorCode.NOT_FOUND);
      }
    }

    String aliasRecordId = overallQueryMap.get("ALIAS_RECORD_ID");

    LocalDateTime now = ConversionUtils.getUTCFromLocalNow();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
    String date = formatter.format(now);

    String peerReviewDocName = aliasRecordId + " Peer Review " + date + ".docx";
    String peerReviewDocPathString = Utility.getUniqueFilePath(fileDirectory, peerReviewDocName);
    File peerReviewDoc = new File(peerReviewDocPathString);

    String pdfFileName = "MN_Attested_" + aliasRecordId + ".pdf";
    String pdfFilePath = Utility.getUniqueFilePath(fileDirectory, pdfFileName);
    File pdf = new File(pdfFilePath);

    String determinationLetterDocName = aliasRecordId + " Determination Letter " + date + ".docx";
    String determinationLetterDocPath =
        Utility.getUniqueFilePath(fileDirectory, determinationLetterDocName);
    File determinationLetterDoc = new File(determinationLetterDocPath);

    String externalReviewDocName = aliasRecordId + " TQMC External Review " + date + ".docx";
    String externalReviewDocPath = Utility.getUniqueFilePath(fileDirectory, externalReviewDocName);
    File externalReviewDoc = new File(externalReviewDocPath);

    List<Map<String, String>> questionList = new ArrayList<>();

    try (PreparedStatement report = con.prepareStatement(QUESTION_QUERY_TEMPLATE)) {
      report.setString(1, caseId);
      ResultSet rs = report.executeQuery();

      while (rs.next()) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("QUESTION", rs.getString("QUESTION"));
        row.put("RATIONALE", rs.getString("RATIONALE"));
        row.put("RECORD_REFERENCES", rs.getString("RECORD_REFERENCES"));
        row.put("RESPONSE", rs.getString("RESPONSE"));
        questionList.add(row);
      }

      if (questionList.isEmpty()) {
        throw new TQMCException(ErrorCode.NOT_FOUND);
      }
    }

    generateMnDocReport(overallQueryMap, questionList, caseId, peerReviewDoc);

    boolean isSecondLevel = overallQueryMap.get("IS_SECOND_LEVEL").equals("true");
    if (isSecondLevel) {
      generate2ndLevelMnDocReport(overallQueryMap, questionList, caseId, determinationLetterDoc);
    } else {
      generateExternalReviewDocReport(overallQueryMap, questionList, caseId, externalReviewDoc);
    }

    // Generate the attestated signature pdf
    TQMCHelper.generateAttestationPdf(con, this.projectId, caseId, pdf, ATTESTED_QUERY);

    // zip up report files
    String zipFileName = "MN_Report_" + caseId + ".zip";
    String zipfilePathString = Utility.getUniqueFilePath(fileDirectory, zipFileName);

    // Zip the files using ZipUtils
    try (FileOutputStream fos = new FileOutputStream(zipfilePathString);
        ZipOutputStream zos = new ZipOutputStream(fos)) {
      ZipUtils.addToZipFile(peerReviewDoc, zos);
      ZipUtils.addToZipFile(pdf, zos);
      if (isSecondLevel) {
        ZipUtils.addToZipFile(determinationLetterDoc, zos);
      } else {
        ZipUtils.addToZipFile(externalReviewDoc, zos);
      }
    } catch (IOException e) {
      throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
    }

    // Generate download key and add the zipped file to insight
    String zipDownloadKey = UUID.randomUUID().toString();
    InsightFile zipInsightFile = new InsightFile();
    zipInsightFile.setFileKey(zipDownloadKey);
    zipInsightFile.setFilePath(zipfilePathString);
    zipInsightFile.setDeleteOnInsightClose(true);
    this.insight.addExportFile(zipDownloadKey, zipInsightFile);

    // Return NounMetadata for the zipped file
    return new NounMetadata(
        zipDownloadKey, PixelDataType.CONST_STRING, PixelOperationType.FILE_DOWNLOAD);
  }

  private void generateMnDocReport(
      Map<String, String> reportMap,
      List<Map<String, String>> questionList,
      String caseId,
      File doc)
      throws SQLException {

    // Create a new XWPFDocument (representing a DOCX file)
    try (XWPFDocument document = new XWPFDocument();
        FileOutputStream out = new FileOutputStream(doc)) {

      generateHeaderAndFonts(document);

      XWPFParagraph generalInfo = document.createParagraph();
      XWPFRun run;

      String specialty = reportMap.get("SPECIALTY_NAME");
      String attestedAt = reportMap.get("COMPLETED_DATE");
      String aliasRecordId = reportMap.get("ALIAS_RECORD_ID");
      String patientLastName = reportMap.get("PATIENT_LAST_NAME");
      String patientFirstName = reportMap.get("PATIENT_FIRST_NAME");
      String appealTypeName = reportMap.get("APPEAL_TYPE_NAME");
      String recResponse = reportMap.get("REC_RESPONSE");
      String recExplanation = reportMap.get("REC_EXPLANATION");

      run = createRun(generalInfo);
      run.setText("Date: " + attestedAt);
      run.addBreak();
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Case ID: " + aliasRecordId);
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Beneficiary Name: " + patientFirstName + " " + patientLastName);
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Enrollment:");

      run = createRun(generalInfo);
      run.setColor("FF0000");
      run.setText(
          " (Select One) TRICARE Prime, TRICARE Select, TRICARE Standard, TRICARE for Life, TRICARE Extra  ");
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Sponsor:");

      run = createRun(generalInfo);
      run.setColor("FF0000");
      run.setText(" First Name Last Name");
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Sponsor SSN:");

      run = createRun(generalInfo);
      run.setColor("FF0000");
      run.setText(" #### (Last 4 digits only)");
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Type of Care:");

      run = createRun(generalInfo);
      run.setColor("FF0000");
      run.setText(
          " (Select One) Acute Inpatient Hospital, Inpatient Psychiatric Hospital, Skilled Nursing Facility, Long Term Acute Care (LTAC), Partial Hospitalization (Psychiatric and Substance Use Disorder), Residential Treatment Center ");
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Date(s) of Service:");

      run = createRun(generalInfo);
      run.setColor("FF0000");
      run.setText(" MM/DD/YYYY - MM/DD/YYYY (or present)");
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Type of Appeal: " + appealTypeName);
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Provider Name:");

      run = createRun(generalInfo);
      run.setColor("FF0000");
      run.setText(" First Name Last Name");
      run.addBreak();

      run = createRun(generalInfo);
      run.setColor("FF0000");
      run.setText("Network Provider, Non-Network Participating, Non-Network Non-Participating");
      run.addBreak();
      run.addBreak();

      run = createRun(generalInfo);
      run.setBold(true);
      run.setText("FILE COPY");
      run.addBreak();
      run.addBreak();

      run = createRun(generalInfo);
      run.setText(
          "We are authorized by the Defense Health Agency under the TRICARE "
              + "Quality Monitoring Contract to review health services provided to TRICARE "
              + "beneficiaries to determine if the services meet medically acceptable "
              + "standards of care, are medically necessary, and are delivered in the most "
              + "appropriate setting. We have completed review of the above referenced case, "
              + "per your request dated ");

      run = createRun(generalInfo);
      run.setTextHighlightColor("yellow");
      run.setText("< Received date >.");

      run = createRun(generalInfo);
      run.setText(
          " The case has been reviewed by a " + specialty + ", who has responded as below.");
      run.addBreak();

      XWPFParagraph questions = document.createParagraph();
      int qNum = 1;

      for (int i = 0; i < questionList.size(); i++) {
        Map<String, String> questionMap = questionList.get(i);

        run = createRun(questions);
        run.setUnderline(UnderlinePatterns.SINGLE);
        run.setText("Question " + "#" + qNum);

        run = createRun(questions);
        run.setText(": ");
        run.addBreak();
        run.addBreak();

        run = createRun(questions);
        run.setText(questionMap.get("QUESTION"));
        run.addBreak();
        run.addBreak();

        // get response, capitalize first letter, add period
        String response = questionMap.get("RESPONSE").trim();
        response = response.substring(0, 1).toUpperCase() + response.substring(1);
        if (!response.endsWith(".")) {
          response += ".";
        }

        run = createRun(questions);
        run.setUnderline(UnderlinePatterns.SINGLE);
        run.setText("Response " + "#" + qNum);

        run = createRun(questions);
        run.setText(": ");
        run.addBreak();
        run.addBreak();

        run = createRun(questions);
        run.setText(response);
        run.addBreak();
        run.addBreak();

        String rationale = questionMap.get("RATIONALE").trim();
        if (!rationale.endsWith(".")) {
          rationale += ".";
        }

        run = createRun(questions);
        run.setUnderline(UnderlinePatterns.SINGLE);
        run.setText("Rationale " + "#" + qNum);

        run = createRun(questions);
        run.setText(": ");
        run.addBreak();
        run.addBreak();

        run = createRun(questions);
        run.setText(rationale);
        run.addBreak();
        run.addBreak();

        String recordReferences = questionMap.get("RECORD_REFERENCES");
        run = createRun(questions);
        run.setUnderline(UnderlinePatterns.SINGLE);
        run.setText("Specific sections of the record used to formulate rationale");

        run = createRun(questions);
        run.setText(": ");
        run.addBreak();
        run.addBreak();

        run = createRun(questions);
        run.setText(recordReferences);
        run.addBreak();
        run.addBreak();

        qNum++;
      }

      if (recResponse != null && recExplanation != null) {
        run = createRun(questions);
        run.setUnderline(UnderlinePatterns.SINGLE);
        run.setText("Reconsideration Outcome");

        run = createRun(questions);
        run.setText(": " + recResponse);
        run.addBreak();
        run.addBreak();

        run = createRun(questions);
        run.setUnderline(UnderlinePatterns.SINGLE);
        run.setText("Reconsideration Explanation");

        run = createRun(questions);
        run.setText(": " + recExplanation);
        run.addBreak();
      }

      // add final questions

      XWPFParagraph regards = document.createParagraph();
      run = createRun(regards);
      addTexttoParagraph(run, FOOTER_CORE_TEXT);

      // Save the document to a file
      document.write(out);
    } catch (IOException e) {
      throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, "Error saving docx", e);
    }
  }

  // sets the font/size and returns run
  private static XWPFRun createRun(XWPFParagraph paragraph) {
    XWPFRun run = paragraph.createRun();
    run.setFontSize(12);
    run.setFontFamily("Times New Roman");
    return run;
  }

  private static void addTexttoParagraph(XWPFRun run, String text) {
    // creates new lines for when \r\n exist since they do not cause a new line in
    // the export
    if (text.contains("\r\n")) {
      String[] lines = text.split("\r\n");
      run.setText(lines[0], 0); // set first line into XWPFRun
      for (int i = 1; i < lines.length; i++) {
        // add break and insert new text
        run.addBreak();
        run.setText(lines[i]);
      }
    } else {
      run.setText(text, 0);
    }
  }

  private void generateHeaderAndFonts(XWPFDocument document) {
    String projectAssetDirectory = AssetUtility.getProjectAssetsFolder(projectId);

    XWPFRun run;

    // setting the default Font
    XWPFStyles styles = document.createStyles();
    CTFonts fonts = CTFonts.Factory.newInstance();
    fonts.setEastAsia("Times New Roman");
    fonts.setHAnsi("Times New Roman");
    fonts.setAscii("Times New Roman");
    styles.setDefaultFonts(fonts);

    CTSectPr section;

    CTBody body = document.getDocument().getBody();
    CTSectPr sectPr = body.getSectPr();
    if (sectPr != null) {
      XWPFParagraph lastParagraph = document.getLastParagraph();
      lastParagraph.getCTP().addNewPPr().setSectPr(sectPr);
      body.unsetSectPr();
    }
    section = body.addNewSectPr();

    XWPFHeaderFooterPolicy headerFooterPolicy = new XWPFHeaderFooterPolicy(document);
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
    run = createRun(paragraph);
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
    run = createRun(paragraph);
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

    XWPFFooter footer = document.createFooter(HeaderFooterType.DEFAULT);
    XWPFParagraph footerP = footer.createParagraph();
    footerP.setAlignment(ParagraphAlignment.CENTER);
    run = createRun(footerP);
    run.setFontSize(10);
    addTexttoParagraph(run, FOOTER_TEXT);

    // Create centered page number
    footerP = footer.createParagraph();
    footerP.setAlignment(ParagraphAlignment.CENTER);
    run = createRun(footerP);
    run.getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);

    run = createRun(footerP);
    run.setFontSize(10);
    CTText text = run.getCTR().addNewInstrText();
    text.setSpace(Space.PRESERVE);
    text.setStringValue("PAGE");

    run = createRun(footerP);
    run.getCTR().addNewFldChar().setFldCharType(STFldCharType.END);

    // make new paragraph
    // set center alignment
    // create page element
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

  private void generate2ndLevelMnDocReport(
      Map<String, String> reportMap,
      List<Map<String, String>> questionList,
      String caseId,
      File doc)
      throws SQLException {

    // Create a new XWPFDocument (representing a DOCX file)
    try (XWPFDocument document = new XWPFDocument();
        FileOutputStream out = new FileOutputStream(doc)) {

      generateHeaderAndFonts(document);
      generate2ndIntroText(document, reportMap);
      generateStatementOfIssuesSection(document);
      generateApplicableAuthoritySection(document);
      generateInpatientMentalHealthSection(document);
      generatePsychiatricSection(document);
      generatePreauthorizationSection(document);
      generateSkilledNursingSection(document);
      generateResidentialTreatmentSection(document);
      generateSubstanceUseSection(document);
      generateAcuteInpatientSection(document);
      generateDiscussionSection(document);
      generateTQMCDecisionSection(document, reportMap, questionList);
      generateWaiverSection(document);
      generateProviderAndBeneSection(document);
      generateProviderAndBene2Section(document);
      generateHoldHarmlessSection(document);
      generationPointOfServiceSection(document);
      generateAppealRightsSection(document);
      generate2ndClosingSection(document, reportMap);

      // Save the document to a file
      document.write(out);
    } catch (IOException e) {
      throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, "Error saving docx", e);
    }
  }

  private void generate2ndIntroText(XWPFDocument document, Map<String, String> reportMap) {

    XWPFParagraph generalInfo = document.createParagraph();
    XWPFRun run;

    String attestedAt = reportMap.get("COMPLETED_DATE");
    String aliasRecordId = reportMap.get("ALIAS_RECORD_ID");
    String patientLastName = reportMap.get("PATIENT_LAST_NAME");
    String patientFirstName = reportMap.get("PATIENT_FIRST_NAME");
    String appealTypeName = reportMap.get("APPEAL_TYPE_NAME");

    run = createRun(generalInfo);
    run.setText("Date: " + attestedAt);
    run.addBreak();
    run.addBreak();

    run = createRun(generalInfo);
    run.setText("Case ID: " + aliasRecordId);
    run.addBreak();

    run = createRun(generalInfo);
    run.setText("Beneficiary Name: " + patientFirstName + " " + patientLastName);
    run.addBreak();

    run = createRun(generalInfo);
    run.setText("Enrollment:");

    run = createRun(generalInfo);
    run.setColor("FF0000");
    run.setText(
        " (Select One) TRICARE Prime, TRICARE Select, TRICARE Standard, TRICARE for Life, TRICARE Extra  ");
    run.addBreak();

    run = createRun(generalInfo);
    run.setText("Sponsor:");

    run = createRun(generalInfo);
    run.setColor("FF0000");
    run.setText(" First Name Last Name");
    run.addBreak();

    run = createRun(generalInfo);
    run.setText("Sponsor SSN:");

    run = createRun(generalInfo);
    run.setColor("FF0000");
    run.setText(" #### (Last 4 digits only)");
    run.addBreak();

    run = createRun(generalInfo);
    run.setText("Type of Care:");

    run = createRun(generalInfo);
    run.setColor("FF0000");
    run.setText(
        " (Select One) Acute Inpatient Hospital, Inpatient Psychiatric Hospital, Skilled Nursing Facility, Long Term Acute Care (LTAC), Partial Hospitalization (Psychiatric and Substance Use Disorder), Residential Treatment Center ");
    run.addBreak();

    run = createRun(generalInfo);
    run.setText("Date(s) of Service:");

    run = createRun(generalInfo);
    run.setColor("FF0000");
    run.setText(" MM/DD/YYYY - MM/DD/YYYY (or present)");
    run.addBreak();

    run = createRun(generalInfo);
    run.setText("Type of Appeal: " + appealTypeName);
    run.addBreak();

    run = createRun(generalInfo);
    run.setText("Provider Name:");

    run = createRun(generalInfo);
    run.setColor("FF0000");
    run.setText(" First Name Last Name");
    run.addBreak();

    run = createRun(generalInfo);
    run.setColor("FF0000");
    run.setText("Network Provider, Non-Network Participating, Non-Network Non-Participating");
    run.addBreak();
    run.addBreak();

    run = createRun(generalInfo);
    run.setColor("FF0000");
    run.setTextHighlightColor("yellow");
    run.setText("Authorized Representative Name ");

    run = createRun(generalInfo);
    run.setTextHighlightColor("yellow");
    run.setText("as Authorized Representative for");

    run = createRun(generalInfo);
    run.setText(" " + patientFirstName + " " + patientLastName);
    run.addBreak();

    run = createRun(generalInfo);
    run.setColor("FF0000");
    run.setText("(OR: Non-Network Facility Name, if facility is appealing party)");
    run.addBreak();

    run = createRun(generalInfo);
    run.setColor("FF0000");
    run.setText(
        "(OR: " + patientFirstName + " " + patientLastName + " if beneficiary on own behalf)");
    run.addBreak();

    run = createRun(generalInfo);
    run.setColor("FF0000");
    run.setText("Mailing Address of Appealing Party");
    run.addBreak();

    run = createRun(generalInfo);
    run.setColor("FF0000");
    run.setText("City, State Zip of Appealing Party");
    run.addBreak();
    run.addBreak();

    run = createRun(generalInfo);
    run.setText("Dear ");

    run = createRun(generalInfo);
    run.setColor("FF0000");
    run.setText("Appealing Party");

    run = createRun(generalInfo);
    run.setText(", ");
    run.addBreak();

    XWPFParagraph paragraph = document.createParagraph();
    paragraph.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(paragraph);
    run.setText(
        "We are authorized by the Defense Health Agency under the TRICARE Quality Monitoring Contract"
            + " (TQMC) to review health care services provided to TRICARE beneficiaries to determine if the"
            + " services meet medically acceptable standards of care, are medically necessary, and are delivered"
            + " in the most appropriate setting.");
    paragraph = document.createParagraph();
    paragraph.setAlignment(ParagraphAlignment.BOTH);
  }

  private void generateStatementOfIssuesSection(XWPFDocument document) {
    XWPFParagraph header = document.createParagraph();
    header.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun run = createRun(header);
    run.setBold(true);
    run.setText("STATEMENT OF ISSUES");
    run.addBreak();

    XWPFParagraph body = document.createParagraph();
    run = createRun(body);
    run.setBold(true);
    run.setTextHighlightColor("yellow");
    run.setText("NOTE TO DRAFTER - Please complete all sections in ");

    run = createRun(body);
    run.setBold(true);
    run.setTextHighlightColor("yellow");
    run.setColor("FF0000");
    run.setText("RED ");

    run = createRun(body);
    run.setBold(true);
    run.setTextHighlightColor("yellow");
    run.setText("and remove red text before sending ");
    body = document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);
    run = createRun(body);
    run.setColor("FF0000");
    run.setItalic(true);
    run.setText(
        "The contractor and the TQMC contractor shall summarize the issue or issues under appeal and shall "
            + "be clear and concise. All issues shall be addressed for example, a reconsideration determination "
            + "in all cases requiring preadmission authorization shall address the requirement for preadmission "
            + "authorization of the care as well as whether the requirement was met. ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    body = document.createParagraph();

    run = createRun(body);
    run.setText("Whether the provision of ");

    run = createRun(body);
    run.setTextHighlightColor("yellow");
    run.setText("[insert type of service denied]");

    run = createRun(body);
    run.setText(" during the timeframe of ");

    run = createRun(body);
    run.setTextHighlightColor("yellow");
    run.setText("[insert timeframe]");

    run = createRun(body);
    run.setText(" was medically necessary and delivered in an appropriate setting.");
    document.createParagraph();
  }

  private void generateApplicableAuthoritySection(XWPFDocument document) {
    XWPFParagraph header = document.createParagraph();
    header.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun run = createRun(header);
    run.setBold(true);
    run.setText("APPLICABLE AUTHORITY ");
    run.addBreak();

    XWPFParagraph body = document.createParagraph();
    run = createRun(body);
    run.setText(
        "TRICARE benefits are authorized by Congressional legislation incorporated in Chapter 55 of "
            + "Title 10, United States Code, and implemented by the Secretary of Defense and the Secretary "
            + "of Health and Human Services in Title 32, Code of Federal Regulations, Part 199 (32 CFR 199). "
            + "Specific regulation provision pertinent to this case are set forth below.");
    body = document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setBold(true);
    run.setText("32 CFR \u00A7199.2(b) ");

    run = createRun(body);
    run.setBold(true);
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Defines medically or psychologically necessary");

    run = createRun(body);
    run.setText(
        " as the frequency, extent, and types of medical services or supplies which represent "
            + "appropriate medical care and that are generally accepted by qualified professionals to be "
            + "reasonable and adequate for the diagnosis and treatment of illness, injury, pregnancy, and "
            + "mental disorders or that are reasonable and adequate for well-baby care. ");

    body = document.createParagraph();
    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setBold(true);
    run.setText("32 CFR \u00A7199.2(b) ");

    run = createRun(body);
    run.setBold(true);
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Defines appropriate medical care");

    run = createRun(body);
    run.setText(" that level of care is covered by CHAMPUS. ");
    run.addBreak();
  }

  private void generateInpatientMentalHealthSection(XWPFDocument document) {
    XWPFParagraph header = document.createParagraph();
    header.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun run = createRun(header);
    run.setBold(true);
    run.setFontSize(14);
    run.setTextHighlightColor("yellow");
    run.setText("Inpatient Mental Health <Delete section if NOT Applicable>  ");
    document.createParagraph();

    XWPFParagraph body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);
    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setText("32 CFR \u00A7199.4 (b) (8) ");

    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Defines Inpatient mental health service");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        " as those services furnished by institutional and professional providers for treatment of a "
            + "nervous or mental disorder (as defined in \u00A7 199.2) to a patient admitted to a "
            + "CHAMPUS-authorized acute are general hospital; a psychiatric hospital; or, unless otherwise "
            + "exempted, a special institutional provider.");
    body = document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "CHAMPUS, Title 32, Part 199.4(b) (8) (i) Criteria for determining medical or psychological "
            + "necessity. In determining the medical or psychological necessity of acute inpatient mental "
            + "health services, the evaluation conducted by the Director, OCHAMPUS (or designee) shall "
            + "consider the appropriate level of care for the patient, the intensity of services required by "
            + "the patient, and the availability of that care. The purpose of such acute inpatient care is "
            + "to stabilize a life-threatening or severely disabling condition within the context of a brief, "
            + "intensive model of inpatient care in order to permit management of the patient's condition at "
            + "a less intensive level of care. Such care is appropriate only if the patient requires services "
            + "of an intensity and nature that are generally recognized as being effectively and safely "
            + "provided only in an acute inpatient hospital setting. In addition to the criteria set forth "
            + "in this paragraph (b)(6) of this section, additional evaluation standards, consistent with "
            + "such criteria, may be adopted by the Director, OCHAMPUS (or designee). Acute inpatient care "
            + "shall not be considered necessary unless the patient needs to be observed and assessed on a "
            + "24-hour basis by skilled nursing staff, and/or requires continued intervention by a "
            + "multidisciplinary treatment team; and in addition, at least one of the following criteria is "
            + "determined to be met: ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText("(A) Patient poses a serious risk of harm to self-and/or others. ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "(B) Patient is in need of high dosage, intensive medication or somatic and/or psychological "
            + "treatment, with potentially serious side effects. ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText("(C) Patient has acute disturbances of mood, behavior, or thinking. ");
    document.createParagraph();
  }

  private void generatePsychiatricSection(XWPFDocument document) {
    XWPFParagraph header = document.createParagraph();
    header.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun run = createRun(header);
    run.setBold(true);
    run.setFontSize(14);
    run.setTextHighlightColor("yellow");
    run.setText(
        "Psychiatric and substance use disorder partial hospitalization services "
            + "<Delete section if NOT Applicable> ");

    XWPFParagraph body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);
    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setText("32 CFR \u00A7199.4 (b) (9) ");

    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Defines Psychiatric and substance use disorder partial hospitalization services");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        " as those services furnished by a TRICARE authorized partial "
            + "hospitalization program and authorized mental health providers for the active treatment of a "
            + "mental disorder. All services must follow a medical model and vest patient care under the "
            + "general direction of a licensed TRICARE authorized physician employed by the partial "
            + "hospitalization program to ensure medication and physical needs of all the patients are "
            + "considered. The primary or attending provider must be a TRICARE authorized mental health "
            + "provider (see paragraph (c)(3)(ix) of this section), operating within the scope of his/her "
            + "license. These categories include physicians, clinical psychologists, certified psychiatric "
            + "nurse specialists, clinical social workers, marriage and family counselors, TRICARE certified "
            + "mental health counselors, pastoral counselors, and supervised mental health counselors. All "
            + "categories practice independently except pastoral counselors and supervised mental health "
            + "counselors who must practice under the supervision of TRICARE authorized physicians. Partial "
            + "hospitalization services and interventions are provided at a high degree of intensity and "
            + "restrictiveness of care, with medical supervision and medication management. Partial "
            + "hospitalization services are covered as a basic program benefit only if they are provided in "
            + "accordance with paragraph (b)(9) of this section. Such programs must enter into a "
            + "participation agreement with TRICARE and be accredited and in substantial compliance with the "
            + "specified standards of an accreditation organization approved by the Director.");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "CHAMPUS, Title 32, Part 199.4(b) (9) (ii). Criteria for determining medical or psychological "
            + "necessity of psychiatric and SUD partial hospitalization services. Partial hospitalization "
            + "services will be considered necessary only if all of the following conditions are present:");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "(A) The patient is suffering significant impairment from a mental disorder (as defined in "
            + "\u00A7 199.2) which interferes with age appropriate functioning or the patient is in need of "
            + "rehabilitative services for the management of withdrawal symptoms from alcohol, "
            + "sedative-hypnotics, opioids, or stimulants that require medically-monitored ambulatory "
            + "detoxification, with direct access to medical services and clinically intensive programming "
            + "of rehabilitative care based on individual treatment plans. ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "(B) The patient is unable to maintain himself or herself in the community, with appropriate "
            + "support, at a sufficient level of functioning to permit an adequate course of therapy "
            + "exclusively on an outpatient basis, to include outpatient treatment program, outpatient office "
            + "visits, or intensive outpatient services (but is able, with appropriate support, to maintain "
            + "a basic level of functioning to permit partial hospitalization services and presents no "
            + "substantial imminent risk of harm to self or others). These patients require medical support; "
            + "however, they do not require a 24-hour medical environment. ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "(C) The patient is in need of crisis stabilization, acute symptom reduction, treatment of "
            + "partially stabilized mental health disorders, or services as a transition from an inpatient "
            + "program. ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "(D) The admission into the partial hospitalization program is based on the development of "
            + "an individualized diagnosis and treatment plan expected to be effective for that patient and "
            + "permit treatment at a less intensive level. ");
    document.createParagraph();
    document.createParagraph();
  }

  private void generatePreauthorizationSection(XWPFDocument document) {
    XWPFParagraph header = document.createParagraph();
    header.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun run = createRun(header);
    run.setBold(true);
    run.setFontSize(14);
    run.setTextHighlightColor("yellow");
    run.setText("Preauthorization <Delete section if NOT Applicable> ");

    XWPFParagraph body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);
    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setText("32 CFR \u00A7199.2 (b) ");

    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Defines Preauthorization");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        " as a decision issued in writing, or electronically by the Director, TRICARE Management "
            + "Activity, or a designee, that TRICARE benefits are payable for certain services that a "
            + "beneficiary has not yet received. The term prior authorization is commonly substituted for "
            + "preauthorization and has the same meaning.");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setText("32 CFR (\u00A7199.2 (b) ");

    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Defines Medically or psychologically necessary preauthorization");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        " as pre (or prior) authorization for payment for medical/surgical or psychological services "
            + "based upon criteria that are generally accepted by qualified professionals to be reasonable "
            + "for diagnosis and treatment of an illness, injury, pregnancy, and mental disorder.");
    document.createParagraph();
  }

  private void generateSkilledNursingSection(XWPFDocument document) {
    XWPFParagraph header = document.createParagraph();
    header.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun run = createRun(header);
    run.setBold(true);
    run.setFontSize(14);
    run.setTextHighlightColor("yellow");
    run.setText("Skilled Nursing Facility <Delete section if NOT Applicable> ");

    XWPFParagraph body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);
    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setText("32 CFR \u00A7199.2 (b) ");

    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Defines custodial care");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        " as treatment or services, regardless of who recommends such treatment or services or "
            + "where such treatment or services are provided that:");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "1. Can be rendered safely and reasonably by a person who is not medically skilled:  ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText("or");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "2. Is or are designed mainly to help the patient with the activities of daily living. ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setText("32 CFR \u00A7199.2 (b) ");

    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Defines skilled nursing services");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        " as services that includes application of professional nursing services and skills by an RN, "
            + "LPN, or LVN, that are required to be performed under the general supervision/direction of a "
            + "TRICARE-authorized physician to ensure the safety of the patient and achieve the medically "
            + "desired result in accordance with accepted standards of practice. ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setText("32 CFR \u00A7199.6(b) (4) (vi) ");

    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Defines Skilled nursing facility");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        " as an institution (or a distinct part of an institution) that meets the criteria as set forth in \u00A7");

    run = createRun(body);
    run.setColor("FF0000");
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("199.6(b)(4)(vi)");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(".");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "CHAMPUS, Title 32, Part 199.4(b) (3) (xiv) Skilled nursing facility (SNF) services. "
            + "Covered  services in  SNFs  are  the same  as provided under Medicare under section 1861(h) "
            + "and (i) of the Social Security Act (42 U.S.C.1395x(h) and (i)) and 42 CFR part 409, subparts "
            + "C and D, except that the Medicare limitation on the number of days of coverage under section "
            + "1812(a) and (b) of the Social Security Act (42 U.S.C. 1395d(a) and (b)) and 42 CFR 409.61(b) "
            + "shall not be applicable under TRICARE. Skilled nursing facility care for each spell of "
            + "illness shall continue to be provided for as long as necessary and appropriate. For a SNF "
            + "admission to be covered under TRICARE, the beneficiary must have a qualifying hospital stay "
            + "meaning an inpatient hospital stay of three consecutive days or more, not including the "
            + "hospital leave day. The beneficiary must enter the SNF within 30 days of leaving the "
            + "hospital, or within such time as it would be medically appropriate to begin an active course "
            + "of treatment, where the individual's condition is such that SNF care would not be medically "
            + "appropriate within 30 days after discharge from a hospital. The skilled services must be for "
            + "a medical condition that was either treated during the qualifying three-day hospital stay or "
            + "started while the beneficiary was already receiving covered SNF care. Additionally, an "
            + "individual shall be deemed not to have been discharged from a SNF, if within 30 days after "
            + "discharge from a SNF, the individual is again admitted to a SNF. Adoption by TRICARE of most "
            + "Medicare coverage standards does not include Medicare coinsurance amounts. Extended care "
            + "services furnished to an inpatient of a SNF by such SNF (except as provided in paragraphs "
            + "(b) (3) (xiv) (C), (b) (3) (xiv) (F), and (b) (3) (xiv) (G) of this section) include: ");
    document.createParagraph();

    XWPFParagraph list = document.createParagraph();
    list.setIndentationLeft(720);

    run = createRun(list);
    run.setColor("FF0000");
    run.setText(
        "A. Nursing care provided by or under the supervision of a registered professional nurse; ");
    run.addBreak();
    run.addBreak();

    run = createRun(list);
    run.setColor("FF0000");
    run.setText("B. Bed and board in connection with the furnishing of such nursing care; ");
    run.addBreak();
    run.addBreak();

    run = createRun(list);
    run.setColor("FF0000");
    run.setText(
        "C. Physical or occupational therapy or speech-language pathology services furnished by "
            + "the SNF or by others under arrangements with them by the facility; ");
    run.addBreak();
    run.addBreak();

    run = createRun(list);
    run.setColor("FF0000");
    run.setText("D. Medical social services;  ");
    run.addBreak();
    run.addBreak();

    run = createRun(list);
    run.setColor("FF0000");
    run.setText(
        "E. Such drugs, biological, supplies, appliances, and equipment, furnished for use in the "
            + "SNF, as are ordinarily furnished for the care and treatment of inpatients; ");
    run.addBreak();
    run.addBreak();

    run = createRun(list);
    run.setColor("FF0000");
    run.setText(
        "F. Medical services provided by an intern or resident-in-training of a hospital with which "
            + "the facility has such an agreement in effect; and ");
    run.addBreak();
    run.addBreak();

    run = createRun(list);
    run.setColor("FF0000");
    run.setText(
        "G. Such other services necessary to the health of the patients are generally provided by "
            + "SNFs, or by others under arrangements with them made by the facility. ");
    run.addBreak();
    run.addBreak();
  }

  private void generateResidentialTreatmentSection(XWPFDocument document) {
    XWPFParagraph header = document.createParagraph();
    header.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun run = createRun(header);
    run.setBold(true);
    run.setFontSize(14);
    run.setTextHighlightColor("yellow");
    run.setText("Residential Treatment Center <Delete section if NOT Applicable> ");

    XWPFParagraph body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);
    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setText("32 CFR \u00A7199.2 (b) ");

    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Defines Residential Treatment Center (RTC)");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        " facility (or distinct part of a facility) which meets the criteria in 32 CFR 199.6(b)(4)(vii). ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setText("32 CFR 199.6(b)(4)(vii) ");

    run = createRun(body);
    run.setBold(true);
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setColor("FF0000");
    run.setText("Defines Residential Treatment Center:");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        " A Residential Treatment Center (RTC) is a facility or a distinct part of a facility "
            + "that provides to beneficiaries under 21 years of age a medically supervised, "
            + "interdisciplinary program of mental health treatment. An RTC is appropriate for patients "
            + "whose predominant symptom presentation is essentially stabilized, although not resolved, "
            + "and who have persistent dysfunction in major life areas. Residential treatment may be "
            + "complemented by family therapy and case management for community-based resources. "
            + "Discharge planning should support transitional care for the patient and family, to "
            + "include resources available in the geographic area where the patient will be residing. "
            + "The extent and pervasiveness of the patient�s problems require a protected and highly "
            + "structured therapeutic environment. Residential treatment is differentiated from: ");

    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setItalic(true);
    run.setColor("FF0000");
    run.setText("(i)");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "  Acute psychiatric care, which requires medical treatment and 24-hour availability of a "
            + "full range of diagnostic and therapeutic services to establish and implement an effective plan "
            + "of care which will reverse life-threatening and/or severely incapacitating symptoms; ");

    body = document.createParagraph();
    run = createRun(body);
    run.setItalic(true);
    run.setColor("FF0000");
    run.setText("(ii)");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "  Partial hospitalization, which provides a less than 24-hour-per-day, seven-day-per-week "
            + "treatment program for patients who continue to exhibit psychiatric problems but can function "
            + "with support in some of the major life areas;");

    body = document.createParagraph();
    run = createRun(body);
    run.setItalic(true);
    run.setColor("FF0000");
    run.setText("(iii)");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "  A group home, which is a professionally directed living arrangement with the "
            + "availability of psychiatric consultation and treatment for patients with significant family "
            + "dysfunction and/or chronic but stable psychiatric disturbances;");

    body = document.createParagraph();
    run = createRun(body);
    run.setItalic(true);
    run.setColor("FF0000");
    run.setText("(iv)");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "  Therapeutic school, which is an educational program supplemented by psychological and "
            + "psychiatric services;");

    body = document.createParagraph();
    run = createRun(body);
    run.setItalic(true);
    run.setColor("FF0000");
    run.setText("(v)");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "  Facilities that treat patients with a primary diagnosis of substance use disorder; and");

    body = document.createParagraph();
    run = createRun(body);
    run.setItalic(true);
    run.setColor("FF0000");
    run.setText("(vi)");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "  Facilities providing care for patients with a primary diagnosis of mental retardation or "
            + "developmental disability.");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);
  }

  private void generateSubstanceUseSection(XWPFDocument document) {
    XWPFParagraph header = document.createParagraph();
    header.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun run = createRun(header);
    run.setBold(true);
    run.setFontSize(14);
    run.setTextHighlightColor("yellow");
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText(
        "Substance use disorder rehabilitation facility (SUDRF) "
            + "<Delete section if NOT Applicable>");
    document.createParagraph();

    XWPFParagraph body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);
    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setText("32 CFR \u00A7199.2 (b) ");

    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Defines Substance use disorder rehabilitation facility (SUDRF)");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        " as a facility or a distinct part of a facility that meets the criteria in \u00A7 199.6(b)(4)(xiv).");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "CHAMPUS, Title 32, Part \u00A7 199.6(b)(4)(xiv).  A SUDRF is a residential or rehabilitation "
            + "facility, or distinct part of a facility, that provides medically monitored, "
            + "interdisciplinary addiction-focused treatment to beneficiaries who have psychoactive "
            + "substance use disorders. Qualified health care professionals provide 24-hour, "
            + "seven-day-per-week, assessment, treatment, and evaluation. A SUDRF is appropriate for "
            + "patients whose addiction-related symptoms, or concomitant physical and emotional/behavioral "
            + "problems reflect persistent dysfunction in several major life areas. Residential or inpatient "
            + "rehabilitation is differentiated from: ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "(i) Acute psychoactive substance use treatment and from treatment of acute "
            + "biomedical/emotional/behavioral problems, which problems are either life-threatening and/or "
            + "severely incapacitating and often occur within the context of a discrete episode of "
            + "addiction-related biomedical or psychiatric dysfunction; ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "(ii) A partial hospitalization center, which serves patients who exhibit "
            + "emotional/behavioral dysfunction but who can function in the community for defined periods "
            + "of time with support in one or more of the major life areas; ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "(iii) A group home, sober-living environment, halfway house, or three-quarter way house; ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "(iv) Therapeutic schools, which are educational programs supplemented by addiction-focused "
            + "services; ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "(v) Facilities that treat patients with primary psychiatric diagnoses other than "
            + "psychoactive substance use or dependence; and ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "(vi) Facilities that care for patients with the primary diagnosis of mental retardation "
            + "or developmental disability. ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);
  }

  private void generateAcuteInpatientSection(XWPFDocument document) {
    XWPFParagraph header = document.createParagraph();
    header.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun run = createRun(header);
    run.setBold(true);
    run.setFontSize(14);
    run.setTextHighlightColor("yellow");
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Acute Inpatient Hospital");
    document.createParagraph();

    XWPFParagraph body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);
    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setText("32 CFR \u00A7199.4 (b) (8) ");

    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Defines Inpatient");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        " as a patient who has been admitted to a hospital or other authorized institution for "
            + "bed occupancy for purposes of receiving necessary medical care, with the reasonable "
            + "expectation that the patient will remain in the institution at least 24 hours, and with "
            + "the registration and assignment of an inpatient number or designation. Institutional care in "
            + "connection with in and out (ambulatory) surgery is not included within the meaning of "
            + "inpatient whether or not an inpatient number or designation is made by the hospital or other "
            + "institution. If the patient has been received at the hospital, but death occurs before the "
            + "actual admission occurs, an inpatient admission exists as if the patient had lived and had "
            + "been formally admitted.");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setText("32 CFR \u00A7199. 199.6 (b) (4) (i) ");

    run = createRun(body);
    run.setBold(true);
    run.setColor("FF0000");
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Defines Hospital, acute care, general and special");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        " as an institution that provides inpatient services, that also may provide outpatient "
            + "services (including clinical and ambulatory surgical services), and that: ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    XWPFParagraph list = document.createParagraph();
    list.setIndentationLeft(720);

    run = createRun(list);
    run.setColor("FF0000");
    run.setText(
        "(A) Is engaged primarily in providing to inpatients, by or under the supervision of "
            + "physicians, diagnostic and therapeutic services for the medical or surgical diagnosis "
            + "and treatment of illness, injury, or bodily malfunction (including maternity). ");

    list = document.createParagraph();
    list.setAlignment(ParagraphAlignment.BOTH);
    list.setIndentationLeft(720);

    run = createRun(list);
    run.setColor("FF0000");
    run.setText(
        "(B) Maintains clinical records on all inpatients (and outpatients if the facility operates "
            + "an outpatient department or emergency room). ");

    list = document.createParagraph();
    list.setAlignment(ParagraphAlignment.BOTH);
    list.setIndentationLeft(720);

    run = createRun(list);
    run.setColor("FF0000");
    run.setText("(C) Has bylaws in effect with respect to its operations and medical staff. ");

    list = document.createParagraph();
    list.setAlignment(ParagraphAlignment.BOTH);
    list.setIndentationLeft(720);

    run = createRun(list);
    run.setColor("FF0000");
    run.setText("(D) Has a requirement that every patient be under the care of a physician. ");

    list = document.createParagraph();
    list.setAlignment(ParagraphAlignment.BOTH);
    list.setIndentationLeft(720);

    run = createRun(list);
    run.setColor("FF0000");
    run.setText(
        "(E) Provides 24-hour nursing service rendered or supervised by a registered professional "
            + "nurse and has a licensed practical nurse or registered professional nurse on duty at all "
            + "times. ");

    list = document.createParagraph();
    list.setAlignment(ParagraphAlignment.BOTH);
    list.setIndentationLeft(720);

    run = createRun(list);
    run.setColor("FF0000");
    run.setText(
        "(F) Has in effect a hospital utilization review plan that is operational and functioning. ");

    list = document.createParagraph();
    list.setAlignment(ParagraphAlignment.BOTH);
    list.setIndentationLeft(720);

    run = createRun(list);
    run.setColor("FF0000");
    run.setText(
        "(G) In the case of an institution in a state in which state or applicable local law "
            + "provides for the licensing of hospitals, the hospital: ");

    list = document.createParagraph();
    list.setIndentationLeft(840);

    run = createRun(list);
    run.setColor("FF0000");
    run.setText("(1) Is licensed pursuant to such law, or ");

    list = document.createParagraph();
    list.setAlignment(ParagraphAlignment.BOTH);
    list.setIndentationLeft(840);

    run = createRun(list);
    run.setColor("FF0000");
    run.setText(
        "(2) Is approved by the agency of such state or locality responsible for licensing "
            + "hospitals as meeting the standards established for such licensing. ");

    list = document.createParagraph();
    list.setIndentationLeft(720);

    run = createRun(list);
    run.setColor("FF0000");
    run.setText("(H) Has in effect an operating plan and budget. ");

    list = document.createParagraph();
    list.setAlignment(ParagraphAlignment.BOTH);
    list.setIndentationLeft(720);

    run = createRun(list);
    run.setColor("FF0000");
    run.setText(
        "(I) Is accredited by the JCAH or meets such other requirements as the Secretary of Health "
            + "and Human Services, the Secretary of Transportation, or the Secretary of Defense finds "
            + "necessary in the interest of the health and safety of patients who are admitted to and "
            + "furnished services in the institution. ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    body = document.createParagraph();

    run = createRun(body);
    run.setBold(true);
    run.setText("32 CFR \u00A7199.4(a) (1) (i) ");

    run = createRun(body);
    run.setBold(true);
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Scope of benefits");

    run = createRun(body);
    run.setText(
        ". Subject to all applicable definitions, conditions, limitations, or exclusions specified "
            + "in this part, the CHAMPUS Basic Program will pay for medically necessary services and "
            + "supplies required in the diagnosis and treatment of illness or injury, including maternity "
            + "care and well-baby care. Benefits include specified medical services and supplies provided "
            + "to eligible beneficiaries from authorized individual professional provider, and professional "
            + "ambulance services, prescription drugs, authorized medical supplies, and rental or purchase "
            + "of durable medical equipment. ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setBold(true);
    run.setText("32 CFR \u00A7199.4(a) (5) ");

    run = createRun(body);
    run.setBold(true);
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Right to information");

    run = createRun(body);
    run.setText(
        ". As a condition precedent to the provision of benefits hereunder, OCHAMPUS or its CHAMPUS "
            + "fiscal intermediaries shall be entitled to receive information from a physician or hospital "
            + "or other person, institution, or organization (including a local, state, or U.S. Government "
            + "agency) providing services or supplies to the beneficiary for which claims or requests for "
            + "approval for benefits are submitted. Such information and records may relate to the "
            + "attendance, testing, monitoring, or examination or diagnosis of, or treatment rendered, or "
            + "services and supplies furnished to a beneficiary, and shall be necessary for the accurate and "
            + "efficient administration of CHAMPUS benefits. Before a determination will be made on a "
            + "request for preauthorization or claim of benefits, a beneficiary or sponsor must provide "
            + "particular additional information relevant to the requested determination, when necessary. "
            + "The recipient of such information shall in every case hold such records confidential except "
            + "when: ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setText(
        "(i) Disclosure of such information is authorized specifically by the beneficiary; ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setText(
        "(ii) Disclosure is necessary to permit authorized governmental officials to investigate "
            + "and prosecute criminal actions, or ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setText(
        "(iii) Disclosure is authorized or required specifically under the terms of the Privacy Act or Freedom of Information Act (refer to Sec. 199.1(m) of this part). ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setText(
        "For the purposes of determining the applicability of and implementing the provisions of "
            + "Secs. 199.8, 199.11, and 199.12, or any provision of similar purpose of any other medical "
            + "benefits coverage or entitlement, OCHAMPUS or CHAMPUS fiscal intermediaries may release, "
            + "without consent or notice to any beneficiary or sponsor, to any person, organization, "
            + "government agency, provider, or other entity any information with respect to any beneficiary "
            + "when such release constitutes a routine use published in the Federal Register in accordance "
            + "with DoD 5400.11-R (Privacy Act (5 U.S.C. 552a)). Before a person's claim of benefits will "
            + "be adjudicated, the person must furnish to CHAMPUS information that reasonably may be "
            + "expected to be in his or her possession and that is necessary to make the benefit "
            + "determination. Failure to provide the requested information may result in denial of the "
            + "claim. ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setBold(true);
    run.setText("32 CFR \u00A7199.4(a) (13) ");

    run = createRun(body);
    run.setBold(true);
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Implementation instructions");

    run = createRun(body);
    run.setText(
        ". The Director, OCHAMPUS, shall issue policies, procedures, instructions, guidelines, "
            + "standards, and/or criteria to implement 32 CFR 199.4 ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setBold(true);
    run.setText("32 CFR \u00A7199.4(g) ");

    run = createRun(body);
    run.setBold(true);
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Exclusions and Limitations");

    run = createRun(body);
    run.setText(
        ". In addition to any definitions, requirements, conditions, or limitations enumerated and "
            + "described in other sections of this part, the following specifically are excluded from the "
            + "Basic Program: (1) Not medically or psychologically necessary. Services and supplies that are "
            + "not medically or psychologically necessary for the diagnosis or treatment of a covered "
            + "illness (including mental disorder) or injury, for the diagnosis and treatment of pregnancy "
            + "or well-baby care. (2) Unnecessary diagnostic tests. X-ray, laboratory, and pathological "
            + "services and machine diagnostic tests not related to a specific illness or injury or a "
            + "definitive set of symptoms except for cancer screening mammography and cancer screening "
            + "Papanicolaou (PAP) tests provided under the terms and conditions contained in the guidelines "
            + "adopted by the Director, OCHAMPUS. (3) Institutional level of care. Services and supplies "
            + "related to inpatient stays in hospitals or other authorized institutions above the "
            + "appropriate level required to provide necessary medical care. (4) Diagnostic admission. "
            + "Services and supplies related to an inpatient admission primarily to perform diagnostic "
            + "tests, examinations, and procedures that could have been and are performed routinely on an "
            + "outpatient basis. NOTE: If it is determined that the diagnostic x-ray, laboratory, and "
            + "pathological services and machine tests performed during such admission were medically "
            + "necessary and would have been covered if performed on an outpatient basis, CHAMPUS benefits "
            + "may be extended for such diagnostic procedures only, but cost-sharing will be computed as "
            + "if performed on an outpatient basis. (5) Unnecessary postpartum inpatient stay, mother, or "
            + "newborn. Postpartum inpatient stay of a mother for purposes of staying with the newborn "
            + "infant (usually primarily for the purpose of breast feeding the infant) when the infant (but "
            + "not the mother) requires the extended stay; or continued inpatient stay of a newborn infant "
            + "primarily for purposes of remaining with the mother when the mother (but not the newborn "
            + "infant) requires extended postpartum inpatient stay. (6) Therapeutic absences. Therapeutic "
            + "absences from an inpatient facility, except when such absences are specifically included in "
            + "a treatment plan approved by the Director, OCHAMPUS, or a designee. For cost-sharing "
            + "provisions refer to Sec. 199.14, paragraph (f) (3). (7) Custodial care. Custodial care as "
            + "defined in Sec. 199.2. (8) Domiciliary care. Domiciliary care as defined in Sec. 199.2. (9) "
            + "Rest or rest cures. Inpatient stays primarily for rest or rest cures. (10) Amounts above "
            + "allowable costs or charges. Costs of services and supplies to the extent amounts billed are "
            + "over the CHAMPUS determined allowable cost or charge, as provided for in Sec. 199.14. (11) No "
            + "legal obligation to pay, no charge would be made. Services or supplies for which the "
            + "beneficiary or sponsor has no legal obligation to pay; or for which no charge would be made "
            + "if the beneficiary or sponsor was not eligible under CHAMPUS; or whenever CHAMPUS is a "
            + "secondary payer for claims subject to the CHAMPUS DRG-based payment system, amounts, when "
            + "combined with the primary payment, which would be in excess of charges (or the amount the "
            + "provider is obligated to accept as payment in full, if it is less than the charges). (12) "
            + "Furnished without charge. Services or supplies furnished without charge. (13) Furnished by "
            + "local, state, or Federal Government. Services and supplies paid for, or eligible for payment, "
            + "directly or indirectly by a local, state, or Federal Government, except as provided under "
            + "CHAMPUS, or by government hospitals serving the general public, or medical care provided by a "
            + "Uniformed Service medical care facility, or benefits provided under title XIX of the Social "
            + "Security Act (Medicaid) (refer to Sec. 199.8 of this part). (14) Study, grant, or research "
            + "programs. Services and supplies provided as a part of or under a scientific or medical study, "
            + "grant, or research program. (15) Unproven drugs, devices, and medical treatments or procedures.");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setBold(true);
    run.setText("32 CFR \u00A7199.4(g) (63) ");

    run = createRun(body);
    run.setBold(true);
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Non-covered condition, unauthorized provider");

    run = createRun(body);
    run.setText(
        ". All services and supplies (including inpatient institutional costs) related to a "
            + "non-covered condition or treatment, including any necessary follow-on care or the treatment of "
            + "complications, are excluded from coverage except as provided under paragraph (e)(9) of this "
            + "section. In addition, all services and supplies provided by an unauthorized provider are excluded.");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setBold(true);
    run.setText("32 CFR \u00A7199.4(g) (1) ");

    run = createRun(body);
    run.setBold(true);
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Exclusions and limitations");

    run = createRun(body);
    run.setText(
        ". In addition to any definitions, requirements, conditions, or limitations enumerated and "
            + "described in other sections of this part, the following specifically are excluded from the Basic "
            + "Program: ");

    run = createRun(body);
    run.setItalic(true);
    run.setText("(1) Not medically or psychologically necessary. ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setText(
        "Services and supplies that are not medically or psychologically necessary for the diagnosis "
            + "or treatment of a covered illness (including mental disorder, to include substance use disorder) "
            + "or injury, for the diagnosis and treatment of pregnancy or well-baby care except as provided in "
            + "the following paragraph. ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setBold(true);
    run.setText("32 CFR 199.4(g)(74) ");

    run = createRun(body);
    run.setBold(true);
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Exclusions and limitations");

    run = createRun(body);
    run.setText(
        ". Note: The fact that a physician may prescribe, order, recommend, or approve a service or "
            + "supply does not, of itself, make it medically necessary or make the charge an allowable expense, "
            + "even though it is not listed specifically as an exclusion. ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setBold(true);
    run.setText("32 CFR \u00A7199.10(a) (3) ");

    run = createRun(body);
    run.setBold(true);
    run.setUnderline(UnderlinePatterns.SINGLE);
    run.setText("Burden of proof");

    run = createRun(body);
    run.setText(
        ". The burden of proof is on the appealing party to establish affirmatively by substantial "
            + "evidence the appealing party's entitlement under law and this part to the authorization of CHAMPUS "
            + "benefits, approval of authorized CHAMPUS provider status, or removal of sanctions imposed under "
            + "Sec. 199.9 of this part. If a presumption exists under the provisions of this part or information "
            + "constitutes prima facie evidence under provisions of this part, the appealing party must produce "
            + "evidence reasonably sufficient to rebut the presumption or prima facie evidence as part of the "
            + "appealing party's burden of proof. CHAMPUS shall not pay any part of the cost or fee, including "
            + "attorney fees, associated with producing or submitting evidence in support of an appeal. ");
    document.createParagraph();
  }

  private void generateDiscussionSection(XWPFDocument document) {
    XWPFParagraph header = document.createParagraph();
    header.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun run = createRun(header);
    run.setBold(true);
    run.setText("DISCUSSION");

    XWPFParagraph body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);
    run = createRun(body);
    run.setBold(true);
    run.setTextHighlightColor("yellow");
    run.setText("NOTE TO DRAFTER - Please complete all sections in ");

    run = createRun(body);
    run.setBold(true);
    run.setTextHighlightColor("yellow");
    run.setColor("FF0000");
    run.setText("RED");

    run = createRun(body);
    run.setBold(true);
    run.setTextHighlightColor("yellow");
    run.setText(" and remove red text before sending ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setItalic(true);
    run.setColor("FF0000");
    run.setText(
        "The contractor and the TQMC contractor shall discuss the original and any added information "
            + "relevant to the issue(s) under appeal, clearly and concisely, and shall state the relevant "
            + "medical history: patient's condition, including symptoms. Usually, one or two paragraphs "
            + "will suffice unless the issues are complex. The contractor and the TQMC contractor shall "
            + "include a discussion of any secondary issues raised by the appealing party or which may have "
            + "been discovered during the reconsideration process.  ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText("The initial reviewer determined that the ");

    run = createRun(body);
    run.setColor("FF0000");
    run.setTextHighlightColor("yellow");
    run.setText("< Type of Care i.e. Residential Treatment Center, Skilled Nursing Facility >");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        " level of care was not medically necessary for the reason noted in the prior determination letter. "
            + "An appeal was filed, and the TQMC determination is as stated below.   ");

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);
  }

  private void generateTQMCDecisionSection(
      XWPFDocument document,
      Map<String, String> reportMap,
      List<Map<String, String>> questionList) {

    String specialty = reportMap.get("SPECIALTY_NAME");
    String recResponse = reportMap.get("REC_RESPONSE");
    String recExplanation = reportMap.get("REC_EXPLANATION");

    XWPFParagraph header = document.createParagraph();
    header.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun run = createRun(header);
    run.setBold(true);
    run.setText("TQMC DECISION");

    XWPFParagraph body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);
    run = createRun(body);
    run.setBold(true);
    run.setTextHighlightColor("yellow");
    run.setText("NOTE TO DRAFTER - Please complete all sections in ");

    run = createRun(body);
    run.setBold(true);
    run.setTextHighlightColor("yellow");
    run.setColor("FF0000");
    run.setText("RED");

    run = createRun(body);
    run.setBold(true);
    run.setTextHighlightColor("yellow");
    run.setText(" and remove red text before sending ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setText("This determination is based on the following rationale provided by ");

    run = createRun(body);
    run.setTextHighlightColor("yellow");
    run.setText("an independent reviewer, board-certified in " + specialty + ".");

    run = createRun(body);
    run.setText(
        " Additionally, the reviewer has signed a certification stating no known conflicts of interest "
            + "exist, and who was not a prior physician reviewer on this case.");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setText(
        "After reviewing all the information contained in your medical record and considering any "
            + "additional information submitted, the reviewer has ");

    run = createRun(body);
    run.setTextHighlightColor("yellow");
    run.setText(recResponse);

    run = createRun(body);
    run.setText(" the denial.  ");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        " The external reviewer, who is board-certified in "
            + specialty
            + " determined the care in dispute ");

    run = createRun(body);
    run.setColor("FF0000");
    run.setTextHighlightColor("yellow");
    if ("Upheld".equals(recResponse)) {
      run.setText("was not");
    } else {
      run.setText("was");
    }

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(" medically necessary, stating in part that:");
    document.createParagraph();

    XWPFParagraph explanation = document.createParagraph();
    explanation.setIndentationLeft(720); // 0.5 inch
    explanation.setIndentationRight(720); // 0.5 inch

    run = createRun(explanation);
    run.setTextHighlightColor("yellow");
    run.setText(recExplanation);

    document.createParagraph();
    XWPFParagraph questions = document.createParagraph();

    run = createRun(questions);
    run.setItalic(true);
    run.setColor("FF0000");
    run.setText(
        "Notice to Drafter: The information below may be summarized and used to supplement the explanation "
            + "above, or deleted. Do not include full questions, responses etc. in this letter.");

    questions = document.createParagraph();
    int qNum = 1;
    for (int i = 0; i < questionList.size(); i++) {
      Map<String, String> questionMap = questionList.get(i);

      run = createRun(questions);
      run.setUnderline(UnderlinePatterns.SINGLE);
      run.setText("Question " + "#" + qNum);

      run = createRun(questions);
      run.setText(": " + questionMap.get("QUESTION"));
      run.addBreak();

      // get response, capitalize first letter, add period
      String response = questionMap.get("RESPONSE").trim();
      response = response.substring(0, 1).toUpperCase() + response.substring(1);
      if (!response.endsWith(".")) {
        response += ".";
      }

      run = createRun(questions);
      run.setUnderline(UnderlinePatterns.SINGLE);
      run.setText("Response " + "#" + qNum);

      run = createRun(questions);
      run.setText(": " + response);
      run.addBreak();

      String rationale = questionMap.get("RATIONALE").trim();
      if (!rationale.endsWith(".")) {
        rationale += ".";
      }

      run = createRun(questions);
      run.setUnderline(UnderlinePatterns.SINGLE);
      run.setText("Rationale " + "#" + qNum);

      run = createRun(questions);
      run.setText(": " + rationale);
      run.addBreak();

      String recordReferences = questionMap.get("RECORD_REFERENCES");
      run = createRun(questions);
      run.setUnderline(UnderlinePatterns.SINGLE);
      run.setText("Specific sections of the record used to formulate rationale");

      run = createRun(questions);
      run.setText(": " + recordReferences);
      run.addBreak();

      qNum++;
    }

    if (recResponse != null) {
      run = createRun(questions);
      run.setUnderline(UnderlinePatterns.SINGLE);
      run.setText("Reconsideration Outcome");

      run = createRun(questions);
      run.setText(": " + recResponse);
      run.addBreak();
    }
  }

  private void generateWaiverSection(XWPFDocument document) {
    XWPFParagraph header = document.createParagraph();
    header.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun run = createRun(header);
    run.setBold(true);
    run.setFontSize(14);
    run.setText("Waiver of Liability");
    run.addBreak();

    run = createRun(header);
    run.setBold(true);
    run.setFontSize(14);
    run.setColor("FF0000");
    run.setTextHighlightColor("yellow");
    run.setText("<Take out section if care delivered by all network providers> ");
    run.addBreak();

    XWPFParagraph body = document.createParagraph();

    run = createRun(body);
    run.setFontSize(14);
    run.setBold(true);
    run.setTextHighlightColor("yellow");
    run.setText("NOTE TO DRAFTER - Please complete all sections in ");

    run = createRun(body);
    run.setFontSize(14);
    run.setBold(true);
    run.setColor("FF0000");
    run.setTextHighlightColor("yellow");
    run.setText("RED ");

    run = createRun(body);
    run.setFontSize(14);
    run.setBold(true);
    run.setTextHighlightColor("yellow");
    run.setText("and remove red text before sending ");
    run.addBreak();
    run.addBreak();

    run = createRun(body);
    run.setColor("FF0000");
    run.setText("<");

    run = createRun(body);
    run.setTextHighlightColor("yellow");
    run.setText(
        "TOM CH 1 Sec 4.1. P: 2.1.5 Applicable to All TRICARE Beneficiaries for retrospective "
            + "determinations that services ");

    run = createRun(body);
    run.setTextHighlightColor("yellow");
    run.setBold(true);
    run.setText("are not medically necessary");

    run = createRun(body);
    run.setTextHighlightColor("yellow");
    run.setText(" regardless of the beneficiary's TRICARE health plan coverage.");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(">");
    run.addBreak();
    run.addBreak();

    run = createRun(body);
    run.setColor("FF0000");
    run.setText("<");

    run = createRun(body);
    run.setTextHighlightColor("yellow");
    run.setText("Waiver of liability ");

    run = createRun(body);
    run.setTextHighlightColor("yellow");
    run.setBold(true);
    run.setText("applies only");

    run = createRun(body);
    run.setTextHighlightColor("yellow");
    run.setText(" to retrospective determinations that services are not medically necessary");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(">");
    run.addBreak();
    run.addBreak();

    run = createRun(body);
    run.setColor("FF0000");
    run.setText("<");

    run = createRun(body);
    run.setTextHighlightColor("yellow");
    run.setText("Waiver of liability ");

    run = createRun(body);
    run.setTextHighlightColor("yellow");
    run.setBold(true);
    run.setText("does not apply");

    run = createRun(body);
    run.setTextHighlightColor("yellow");
    run.setText(
        " to denials of cost-sharing for services which have not yet been given in the case of "
            + "preauthorizations and Concurrent Reviews");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(">");
    run.addBreak();
    run.addBreak();

    run = createRun(body);
    run.setColor("FF0000");
    run.setText("<");

    run = createRun(body);
    run.setTextHighlightColor("yellow");
    run.setText("Waiver of liability ");

    run = createRun(body);
    run.setTextHighlightColor("yellow");
    run.setBold(true);
    run.setText("does not apply");

    run = createRun(body);
    run.setTextHighlightColor("yellow");
    run.setText(
        " to denials based on factual determinations, which include coverage limitations under 32 "
            + "CFR 199.4, the TRICARE Policy Manual, or TRICARE guidelines and other factual determinations "
            + "as described in the TOM, Chapter 12, Section 5");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(">");
    document.createParagraph();
    document.createParagraph();
    document.createParagraph();
  }

  private void generateProviderAndBeneSection(XWPFDocument document) {

    XWPFParagraph header = document.createParagraph();
    header.setAlignment(ParagraphAlignment.CENTER);

    XWPFRun run = createRun(header);
    run.setFontSize(16);
    run.setBold(true);
    run.setTextHighlightColor("yellow");
    run.setText(
        "THE SECTION BELOW WOULD BE USED IF RETROSPECTIVE REVIEW"
            + ". Do not use if Decision is Reversed");
    document.createParagraph();

    header = document.createParagraph();
    header.setAlignment(ParagraphAlignment.CENTER);
    run = createRun(header);
    run.setBold(true);
    run.setText("PROVIDER AND BENEFICIARY LIABILITY DETERMINATION ");
    run.addBreak();

    XWPFParagraph body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);
    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "The provider and beneficiary liability determination addresses whether or not the provider "
            + "and beneficiary knew or could have reasonably been expected to know that the care/services "
            + "rendered would not be covered by TRICARE as a result of the denial determination. Refer to "
            + "the 32 CFR 199.4 (h) Waiver of Liability set forth below.  ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText("1.    Beneficiary Liability Determination");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "The initial notice/letter was mailed to the beneficiary on <DATE>. There is a presumption "
            + "that the beneficiary's receipt of the \"notice of denial\" would be five calendar days after "
            + "the date of the initial denial determination. Therefore, it is presumed that the beneficiary "
            + "received notice <DATE>. There is no other documentation that the beneficiary was otherwise "
            + "informed that that the care was excluded on the basis of medical necessity, therefore, it is "
            + "determined that the beneficiary is liable for payment effective <DATE>, since the "
            + "beneficiary's liability begins on the day after the date that the denial letter would be "
            + "presumed to have been received. ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "This beneficiary was notified of the initial denial on <DATE> that the <type of care/level "
            + "of care> was not necessary. If there was a cost associated with the care that was assumed, "
            + "the beneficiary's liability ");

    run = createRun(body);
    run.setColor("FF0000");
    run.setTextHighlightColor("yellow");
    run.setText("ends/begins");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(" from <DATE> and forward. ");
    document.createParagraph();
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText("2.    Non-Network Provider Liability ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "In reference to the physician peer reviewer rationale completed for this case, and in "
            + "accordance with acceptable standards of practice for < level of care>. ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "Denial notification was given to the provider on <DATE> for the denial date from <DATE> and "
            + "forward. Therefore, the provider bears liability $XXXXXXX. ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "Per TRICARE Policy Manual 6010.63, Chapter 1, Section 4.1, Paragraph 2.5.2, for the dates "
            + "in question for the patient's Episode of Care, the provider should have known, or reasonably "
            + "expected to have known, that the services provided to the beneficiary were not a covered "
            + "benefit. ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "The provider could reasonably be expected to know the services were not medically necessary, "
            + "because such services are not considered to be medically necessary in accordance with the "
            + "acceptable standards of practice in the local medical community and the provider's knowledge "
            + "of such standards. ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "The amount in dispute remaining, as a result of the decision, is approximately $XXXX and "
            + "the amount in dispute was determined/calculated by obtaining the daily rate of $XX per day "
            + "rate for <type of care/ level of care> ");

    run = createRun(body);
    run.setColor("FF0000");
    run.setTextHighlightColor("yellow");
    run.setText("from XXXXX.");
    document.createParagraph();
  }

  private void generateProviderAndBene2Section(XWPFDocument document) {

    XWPFParagraph header = document.createParagraph();
    header.setAlignment(ParagraphAlignment.BOTH);

    XWPFRun run = createRun(header);
    run.setFontSize(16);
    run.setBold(true);
    run.setTextHighlightColor("yellow");
    run.setText("THE SECTION BELOW WOULD BE USED IF CONCURRENT REVIEW ");
    document.createParagraph();

    header = document.createParagraph();
    header.setAlignment(ParagraphAlignment.CENTER);
    run = createRun(header);
    run.setFontSize(14);
    run.setBold(true);
    run.setTextHighlightColor("yellow");
    run.setText("Do not use if Decision is Reversed");
    document.createParagraph();
    document.createParagraph();

    header = document.createParagraph();
    header.setAlignment(ParagraphAlignment.CENTER);
    run = createRun(header);
    run.setBold(true);
    run.setText("PROVIDER AND BENEFICIARY LIABILITY DETERMINATION ");
    document.createParagraph();

    XWPFParagraph body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);
    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "Subject to application of other TRICARE definitions and criteria, the principle of waiver "
            + "of liability is summarized as follows: If the beneficiary did not know, or could not "
            + "reasonably be expected to know, that certain services were potentially excludable from the "
            + "TRICARE Program by reason of being not medically necessary, not provided at an appropriate "
            + "level, custodial care, or other reason relative to reasonableness, necessity or "
            + "appropriateness (hereafter, all such services are referred to as not medically necessary), "
            + "then the beneficiary is not held liable for such services and, under certain circumstances, "
            + "payment is made for the excludable services as if the exclusion for such services did not "
            + "apply. Note:  The word service(s), as used in this section, include(s) services and supplies. ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "The provider and beneficiary liability determination addresses whether or not the provider "
            + "and beneficiary knew or could have reasonably been expected to know that the care/services "
            + "rendered would not be covered by TRICARE as a result of the denial determination. Refer to the "
            + "32 CFR 199.4 (h) Waiver of Liability set forth below.  ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText("1.    Beneficiary Liability Determination ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "This beneficiary was notified of the initial denial on <DATE> that <level of care> was not "
            + "necessary ");

    run = createRun(body);
    run.setColor("FF0000");
    run.setTextHighlightColor("yellow");
    run.setText("xxxxxxxx");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        ". If there was a cost associated with the care that was assumed, the beneficiary's liability "
            + "begins <DATE>.");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText("2.    Non-Network Provider Liability ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "In reference to the physician peer reviewer's rationale completed for this case, and in "
            + "accordance with acceptable standards of practice for < level of care>, the provider's liability "
            + "ends <DATE>. ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "Denial notification was given to the provider on <DATE> for the denial date from <DATE>. "
            + "Therefore, the provider bears liability for <care received i.e., SNF> services through <DATE>. ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "Per TRICARE Policy Manual 6010.63-M, Chapter 1, Section 4.1, Paragraph 2.5.2 for the dates in "
            + "question for the patient's Episode of Care, the provider should have known, or reasonably "
            + "expected to have known, that the services provided to the beneficiary were not a covered benefit. ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "The provider could reasonably be expected to know the services were not medically necessary, "
            + "because such services are not considered to be medically necessary in accordance with the "
            + "acceptable standards of practice in the local medical community and the provider's knowledge of "
            + "such standards. ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "The amount in dispute remaining, as a result of the decision, is anticipated to be greater "
            + "than $300.00. ");
    document.createParagraph();
    document.createParagraph();
  }

  private void generateHoldHarmlessSection(XWPFDocument document) {
    XWPFParagraph header = document.createParagraph();
    header.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun run = createRun(header);
    run.setBold(true);
    run.setText("HOLD HARMLESS");

    XWPFParagraph body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);
    run = createRun(body);
    run.setFontSize(14);
    run.setBold(true);
    run.setTextHighlightColor("yellow");
    run.setText(
        "<Hold harmless provisions are applied only to care provided by a network provider, but may be left in as informational only as appropriate in most cases> ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "A network provider cannot bill you for non-covered care unless you are informed in advance "
            + "that the care will not be covered by TRICARE, and you waive your right to be held harmless "
            + "by agreeing in advance (which agreement is evidenced in writing) to pay for the specific "
            + "non-covered care. If the service has already been provided when you receive this letter and "
            + "it was provided by a network provider who was aware of your TRICARE eligibility, and if there "
            + "was no such agreement and you have paid for the care, you may seek a refund for the amount "
            + "you paid. This can be done by requesting a refund from < ");

    run = createRun(body);
    run.setColor("FF0000");
    run.setBold(true);
    run.setText("(insert contractor name and address)");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(">");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "Include documentation of your payment for the care, by writing to the above address. If you "
            + "have not paid for the care and have not signed such an agreement, and a network provider is "
            + "seeking payment for the care, please notify the Defense Health Agency, Beneficiary and "
            + "Provider Services Directorate, 16401 East Centretech Parkway, Aurora, Colorado 80011-9066. ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "Under hold harmless provisions, the beneficiary has no financial liability and, therefore, "
            + "has no further appeal rights. If, however, you agreed in advance to waive your right to be "
            + "held harmless, you will be financially liable, and the appeal rights outlined below would "
            + "apply. Similarly, the appeal rights outlined below apply if you have not yet received the "
            + "care of if you received the care from a non-network provider and there is $300.00 or more in "
            + "dispute. ");
    document.createParagraph();
    document.createParagraph();
  }

  private void generationPointOfServiceSection(XWPFDocument document) {
    XWPFParagraph header = document.createParagraph();
    header.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun run = createRun(header);
    run.setBold(true);
    run.setText("POINT OF SERVICE");

    XWPFParagraph body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);
    run = createRun(body);
    run.setFontSize(14);
    run.setBold(true);
    run.setColor("FF0000");
    run.setTextHighlightColor("yellow");
    run.setText(
        "< [TRICARE Prime only - Nurse to verify whether beneficiary was Prime at time of service and include or exclude]>");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "Should you, as a TRICARE Prime beneficiary, elect to proceed with this service and the "
            + "service is provided by a non-network provider, and provided the service is found upon appeal "
            + "to have been medically necessary, benefits will be payable under the deductible and "
            + "cost-share amounts for POS claims and your out-of-pocket expenses will be higher than they "
            + "would be had you received the service from a network provider. No more than 50% of the "
            + "allowable charge can be paid by the government for care provided under the POS option.");
    document.createParagraph();
    document.createParagraph();
  }

  private void generateAppealRightsSection(XWPFDocument document) {
    XWPFParagraph header = document.createParagraph();
    header.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun run = createRun(header);
    run.setBold(true);
    run.setText("APPEAL RIGHTS");

    header = document.createParagraph();

    XWPFParagraph body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);
    run = createRun(body);
    run.setTextHighlightColor("yellow");
    run.setText("The TQMC reviewer has ");

    run = createRun(body);
    run.setTextHighlightColor("yellow");
    run.setBold(true);
    run.setText("reversed");

    run = createRun(body);
    run.setTextHighlightColor("yellow");
    run.setText(" the denial of care; therefore, no additional appeal rights are available.");
    body = document.createParagraph();

    run = createRun(body);
    run.setFontSize(14);
    run.setBold(true);
    run.setColor("FF0000");
    run.setTextHighlightColor("yellow");
    run.addBreak();
    run.setText("<Remove below section if denial is reversed; remove above if denial is upheld> ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "It was further determined by review of the medical record that continued stay at a ");

    run = createRun(body);
    run.setColor("FF0000");
    run.setTextHighlightColor("yellow");
    run.setText("<Residential Treatment Facility, Skilled Nursing Facility, etc.> <was/was not>");

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(" medically necessary starting on <DATE>. ");

    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "In accordance with 32 CFR \u00A7199.10, an appropriate appealing party (i.e. the TRICARE "
            + "beneficiary or the non-network participating provider of care) or an appointed representative "
            + "of an appropriate appealing party has the right to request a hearing from the Defense Health "
            + "Agency (DHA), formerly the TRICARE Management Activity (TMA), if there is disagreement with "
            + "this reconsideration determination. However, this reconsideration determination regarding "
            + "medical necessity is final for the provider of care. The provider of care may request a "
            + "hearing for the Defense Health Agency relating to the waiver of liability determination of "
            + "whether the provider knew or could reasonably have been expected to know that the services "
            + "were excludable. The request must be in writing, be signed, and commencement of the requested "
            + "services must occur prior to 60 calendar days from the date of this reconsideration "
            + "determination and the request for a hearing must nevertheless be postmarked or received by "
            + "the Appeals and Hearings Division, the Defense Health Agency, 16401 East Centretech Parkway, "
            + "Aurora, Colorado 80011-9066, within 60 calendar days of the date of this reconsideration "
            + "determination. ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "If the postmark on the envelope is not legible, or if another method of transmitting the "
            + "appeal request is used, then the date of receipt by the Defense Health Agency is deemed to be "
            + "the date of filing. ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "Additional documentation in support of the appeal may be submitted. However, because a "
            + "request for hearing must be received within 60 calendar days of the date of this "
            + "reconsideration determination, a request for hearing should not be delayed pending the "
            + "acquisition of any additional documentation. If additional documentation is to be submitted "
            + "at a later date, the letter requesting the hearing must include a statement that additional "
            + "documentation will be submitted and the expected date of submission. ");
    document.createParagraph();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "Upon receiving your request, all TRICARE claims related to the entire course of treatment "
            + "will be reviewed. ");
    document.createParagraph();
    document.createParagraph();
  }

  private void generate2ndClosingSection(XWPFDocument document, Map<String, String> reportMap) {

    String firstName = reportMap.get("PATIENT_FIRST_NAME");
    String lastName = reportMap.get("PATIENT_LAST_NAME");
    String careContractorName = reportMap.get("CARE_CONTRACTOR_NAME");

    XWPFParagraph body = document.createParagraph();
    XWPFRun run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "By receiving a copy of this letter, "
            + firstName
            + " "
            + lastName
            + " is being notified of this decision.");
    run.addBreak();
    run.addBreak();

    run = createRun(body);
    run.setText("Sincerely, ");
    run.addBreak();

    run = createRun(body);
    run.setText("TQMC Medical Review Team ");
    run.addBreak();
    run.addBreak();

    run = createRun(body);
    run.setText("cc: ");
    run.addBreak();

    run = createRun(body);
    run.setText(careContractorName);
    run.addBreak();

    body = document.createParagraph();
    body.setAlignment(ParagraphAlignment.BOTH);

    run = createRun(body);
    run.setColor("FF0000");
    run.setText(
        "< If the appealing party is a non-network participating provider, a copy of the "
            + "reconsideration determination shall be furnished to the beneficiary. Conversely, the "
            + "non-network participating providers shall be furnished copies of the determination if the "
            + "beneficiary filed the appeal> ");
  }

  private void generateExternalReviewDocReport(
      Map<String, String> reportMap,
      List<Map<String, String>> questionList,
      String caseId,
      File doc)
      throws SQLException {

    // Create a new XWPFDocument (representing a DOCX file)
    try (XWPFDocument document = new XWPFDocument();
        FileOutputStream out = new FileOutputStream(doc)) {

      generateHeaderAndFonts(document);

      XWPFParagraph generalInfo = document.createParagraph();
      XWPFRun run;

      String specialty = reportMap.get("SPECIALTY_NAME");
      String attestedAt = reportMap.get("COMPLETED_DATE");
      String aliasRecordId = reportMap.get("ALIAS_RECORD_ID");
      String patientLastName = reportMap.get("PATIENT_LAST_NAME");
      String patientFirstName = reportMap.get("PATIENT_FIRST_NAME");
      String appealTypeName = reportMap.get("APPEAL_TYPE_NAME");

      run = createRun(generalInfo);
      run.setText("Date: " + attestedAt);
      run.addBreak();
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Case ID: " + aliasRecordId);
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Beneficiary Name: " + patientFirstName + " " + patientLastName);
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Enrollment:");

      run = createRun(generalInfo);
      run.setColor("FF0000");
      run.setText(
          " (Select One) TRICARE Prime, TRICARE Select, TRICARE Standard, TRICARE for Life, TRICARE Extra  ");
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Sponsor:");

      run = createRun(generalInfo);
      run.setColor("FF0000");
      run.setText(" First Name Last Name");
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Sponsor SSN:");

      run = createRun(generalInfo);
      run.setColor("FF0000");
      run.setText(" #### (Last 4 digits only)");
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Type of Care:");

      run = createRun(generalInfo);
      run.setColor("FF0000");
      run.setText(
          " (Select One) Acute Inpatient Hospital, Inpatient Psychiatric Hospital, Skilled Nursing Facility, Long Term Acute Care (LTAC), Partial Hospitalization (Psychiatric and Substance Use Disorder), Residential Treatment Center ");
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Date(s) of Service:");

      run = createRun(generalInfo);
      run.setColor("FF0000");
      run.setText(" MM/DD/YYYY - MM/DD/YYYY (or present)");
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Type of Appeal: " + appealTypeName);
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Provider Name:");

      run = createRun(generalInfo);
      run.setColor("FF0000");
      run.setText(" First Name Last Name");
      run.addBreak();

      run = createRun(generalInfo);
      run.setColor("FF0000");
      run.setText("Network Provider, Non-Network Participating, Non-Network Non-Participating");
      run.addBreak();
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Office of Appeals and Hearings");
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Defense Health Agency");
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Attn: ");

      run = createRun(generalInfo);
      run.setTextHighlightColor("yellow");
      run.setText("DHA OGC POC Name");
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("16401 East Centretech Parkway");
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Aurora, CO 80011-9066");
      run.addBreak();
      run.addBreak();

      run = createRun(generalInfo);
      run.setText("Dear ");

      run = createRun(generalInfo);
      run.setTextHighlightColor("yellow");
      run.setText("DHA OGC POC:");
      run.addBreak();
      run.addBreak();

      run = createRun(generalInfo);
      run.setText(
          "We are authorized by the Defense Health Agency under the TRICARE "
              + "Quality Monitoring Contract to review health services provided to TRICARE "
              + "beneficiaries to determine if the services meet medically acceptable "
              + "standards of care, are medically necessary, and are delivered in the most "
              + "appropriate setting. We have completed review of the above referenced case, "
              + "per your request dated ");

      run = createRun(generalInfo);
      run.setTextHighlightColor("yellow");
      run.setText("< Received date >.");

      run = createRun(generalInfo);
      run.setText(
          " The case has been reviewed by a " + specialty + ", who has responded as below.");
      run.addBreak();

      XWPFParagraph questions = document.createParagraph();
      int qNum = 1;

      for (int i = 0; i < questionList.size(); i++) {
        Map<String, String> questionMap = questionList.get(i);

        run = createRun(questions);
        run.setUnderline(UnderlinePatterns.SINGLE);
        run.setText("Question " + "#" + qNum);

        run = createRun(questions);
        run.setText(": ");
        run.addBreak();
        run.addBreak();

        run = createRun(questions);
        run.setText(questionMap.get("QUESTION"));
        run.addBreak();
        run.addBreak();

        // get response, capitalize first letter, add period
        String response = questionMap.get("RESPONSE").trim();
        response = response.substring(0, 1).toUpperCase() + response.substring(1);
        if (!response.endsWith(".")) {
          response += ".";
        }

        run = createRun(questions);
        run.setUnderline(UnderlinePatterns.SINGLE);
        run.setText("Response " + "#" + qNum);

        run = createRun(questions);
        run.setText(": ");
        run.addBreak();
        run.addBreak();

        run = createRun(questions);
        run.setText(response);
        run.addBreak();
        run.addBreak();

        String rationale = questionMap.get("RATIONALE").trim();
        if (!rationale.endsWith(".")) {
          rationale += ".";
        }

        run = createRun(questions);
        run.setUnderline(UnderlinePatterns.SINGLE);
        run.setText("Rationale " + "#" + qNum);

        run = createRun(questions);
        run.setText(": ");
        run.addBreak();
        run.addBreak();

        run = createRun(questions);
        run.setText(rationale);
        run.addBreak();
        run.addBreak();

        String recordReferences = questionMap.get("RECORD_REFERENCES");
        run = createRun(questions);
        run.setUnderline(UnderlinePatterns.SINGLE);
        run.setText("Specific sections of the record used to formulate rationale");

        run = createRun(questions);
        run.setText(": ");
        run.addBreak();
        run.addBreak();

        run = createRun(questions);
        run.setText(recordReferences);
        run.addBreak();
        run.addBreak();

        qNum++;
      }

      // add final questions

      XWPFParagraph regards = document.createParagraph();
      run = createRun(regards);
      addTexttoParagraph(run, FOOTER_CORE_TEXT);

      // Save the document to a file
      document.write(out);
    } catch (IOException e) {
      throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, "Error saving docx", e);
    }
  }
}
