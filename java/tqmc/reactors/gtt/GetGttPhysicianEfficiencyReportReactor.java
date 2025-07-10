package tqmc.reactors.gtt;

import static tqmc.util.TQMCHelper.getAllUsers;
import static tqmc.util.TQMCHelper.getCompletedPhysicianWorkFlowsByUserDate;
import static tqmc.util.TQMCHelper.getMTFNameFromRecordId;
import static tqmc.util.TQMCHelper.getNumberOfEventsAndTriggersForGttWorkflow;
import static tqmc.util.TQMCHelper.getPhysicianCaseAverageDaysToComplete;
import static tqmc.util.TQMCHelper.getPhysicianWorkflowAssignedDate;
import static tqmc.util.TQMCHelper.getRecordIdFromGttPhysicianWorkFlowId;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import kotlin.Pair;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.AssetUtility;
import prerna.util.Utility;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.gtt.GttWorkflow;
import tqmc.domain.user.TQMCUserInfo;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;

public class GetGttPhysicianEfficiencyReportReactor extends AbstractTQMCReactor {

  private static final String[] USER_COMPARISON_HEADERS =
      new String[] {
        "User", "Number of cases Completed", "Average time from assignment to completion in days ",
      };

  private static final String[] MONTHLY_BREAKDOWN_HEADERS =
      new String[] {
        "User",
        "MTF",
        "Encounter ID",
        "Date assigned review",
        "Date completed review",
        "Time from assignment to competion in days",
        "# of adverse events",
        "# of triggers"
      };

  private static final String[] OVERVIEW_CONTENT =
      new String[] {
        "Overview of the Physician Metrics Report",
        "This report provides insight into the efficiency of users performing physician reviews for GTT. It includes all users as user roles can change in the system.",
        "",
        "User comparison",
        "This sheet provides a direct comparison of user metrics for the month and specifically time cases spent in their queue as well as the total cases complated by each physician",
        "",
        "Monthly Breakdown",
        "This sheet is the most granular and includes all case information completed during this month."
      };

  List<Integer> overViewHeaderIndexes =
      Arrays.stream(new int[] {0, 3, 6}).boxed().collect(Collectors.toList());

  private static final String MONTH = "month"; // YYYY-MM
  private static final String LAST_MONTH = "12";
  private static final String[] MONTHS = {
    "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12"
  };
  private static final String ADD_DAY = "01";

  public GetGttPhysicianEfficiencyReportReactor() {
    this.keysToGet = new String[] {MONTH};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    String monthString = this.keyValue.get(MONTH);
    String[] parseInput = monthString.split("-");
    String startDate = monthString + "-" + ADD_DAY;
    String endDate;

    try {
      if (parseInput[1].equals(LAST_MONTH)) {
        parseInput[0] = String.valueOf(Integer.valueOf(parseInput[0]) + 1);
      }
      endDate = parseInput[0] + "-" + MONTHS[Integer.valueOf(parseInput[1]) % 12] + "-" + ADD_DAY;
    } catch (RuntimeException e) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid input date", e);
    }

    Workbook workbook = new XSSFWorkbook();
    Sheet sheet1 = workbook.createSheet("Overview");
    Sheet sheet2 = workbook.createSheet("User Comparison");
    Sheet sheet3 = workbook.createSheet("Monthly Breakdown");

    List<TQMCUserInfo> allUsers = getAllUsers(con);

    CellStyle headerStyle = workbook.createCellStyle();
    Font bold = workbook.createFont();
    bold.setBold(true);
    headerStyle.setFont(bold);
    headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
    headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    headerStyle.setWrapText(true);

    CellStyle infoStyle = workbook.createCellStyle();
    infoStyle.setWrapText(true);

    for (int i = 0; i < OVERVIEW_CONTENT.length; i++) {
      Cell cell = sheet1.createRow(i).createCell(0);
      if (overViewHeaderIndexes.contains(i)) {
        cell.setCellStyle(headerStyle);
      } else {
        cell.setCellStyle(infoStyle);
      }

      cell.setCellValue(OVERVIEW_CONTENT[i]);
    }

    Row userComparisonsHeaderRow = sheet2.createRow(0);
    for (int i = 0; i < USER_COMPARISON_HEADERS.length; i++) {
      Cell cell = userComparisonsHeaderRow.createCell(i);
      cell.setCellStyle(headerStyle);
      cell.setCellValue(USER_COMPARISON_HEADERS[i]);
    }

    Row monthlyBreakdownHeaderRow = sheet3.createRow(0);
    for (int i = 0; i < MONTHLY_BREAKDOWN_HEADERS.length; i++) {
      Cell cell = monthlyBreakdownHeaderRow.createCell(i);
      cell.setCellStyle(headerStyle);
      cell.setCellValue(MONTHLY_BREAKDOWN_HEADERS[i]);
    }

    int sheet2Index = 1;
    int sheet3Index = 1;
    for (TQMCUserInfo physician : allUsers) {
      List<GttWorkflow> completedCases =
          getCompletedPhysicianWorkFlowsByUserDate(con, physician.getUserId(), startDate, endDate);

      Row sheet2Row = sheet2.createRow(sheet2Index);
      sheet2Row
          .createCell(0)
          .setCellValue(physician.getFirstName() + " " + physician.getLastName());
      sheet2Row.createCell(1).setCellValue(completedCases.size());
      sheet2Row
          .createCell(2)
          .setCellValue(getPhysicianCaseAverageDaysToComplete(con, physician, completedCases));

      for (GttWorkflow completedCase : completedCases) {
        Row sheet3Row = sheet3.createRow(sheet3Index);
        String recordId = getRecordIdFromGttPhysicianWorkFlowId(con, completedCase.getCaseId());

        sheet3Row
            .createCell(0)
            .setCellValue(physician.getFirstName() + " " + physician.getLastName());
        sheet3Row.createCell(1).setCellValue(getMTFNameFromRecordId(con, recordId));
        sheet3Row.createCell(2).setCellValue(recordId);
        LocalDateTime assignedDate =
            getPhysicianWorkflowAssignedDate(con, completedCase, physician);
        sheet3Row.createCell(3).setCellValue(assignedDate.toLocalDate().toString());
        sheet3Row
            .createCell(4)
            .setCellValue(completedCase.getSmssTimestamp().toLocalDate().toString());
        sheet3Row
            .createCell(5)
            .setCellValue(
                ConversionUtils.getDaysBetween(assignedDate, completedCase.getSmssTimestamp()));
        Pair<Integer, Integer> eventsAndTriggersForAbstraction =
            getNumberOfEventsAndTriggersForGttWorkflow(con, completedCase);
        sheet3Row.createCell(6).setCellValue(eventsAndTriggersForAbstraction.getFirst());
        sheet3Row.createCell(7).setCellValue(eventsAndTriggersForAbstraction.getSecond());

        sheet3Index++;
      }

      sheet2Index++;
    }

    sheet1.setColumnWidth(0, 25000);

    sheet2.autoSizeColumn(0);
    sheet2.autoSizeColumn(1);
    sheet2.autoSizeColumn(2);

    sheet3.autoSizeColumn(0);
    sheet3.autoSizeColumn(1);
    sheet3.autoSizeColumn(2);
    sheet3.autoSizeColumn(3);
    sheet3.autoSizeColumn(4);
    sheet3.autoSizeColumn(5);
    sheet3.autoSizeColumn(6);
    sheet3.autoSizeColumn(7);

    String fileDirectory =
        AssetUtility.getRootFolderPath(this.insight, AssetUtility.INSIGHT_SPACE_KEY, true);
    String baseFilename =
        "GTT_PhysicianEfficiency_Month_" + parseInput[0] + "_" + parseInput[1] + ".xlsx";
    String filePathString = Utility.getUniqueFilePath(fileDirectory, baseFilename);

    File f = new File(filePathString);

    try (FileOutputStream fileOut = new FileOutputStream(f); ) {
      workbook.write(fileOut);
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {
        workbook.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    NounMetadata retNoun = new NounMetadata(f, PixelDataType.FILE_REFERENCE);
    return retNoun;
  }
}
