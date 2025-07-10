package tqmc.reactors.gtt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kotlin.Pair;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
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
import tqmc.util.TQMCHelper;

public class GetGttEfficiencyReportReactor extends AbstractTQMCReactor {

  private static final String MONTH = "month"; // YYYY-MM
  private static final String LAST_MONTH = "12";
  private static final String[] MONTHS = {
    "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12"
  };
  private static final String ADD_DAY = "01";

  public GetGttEfficiencyReportReactor() {
    this.keysToGet = new String[] {MONTH};
    this.keyRequired = new int[] {1};
  }

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  private static final String[] ABSTRACTOR_IRR_PAIRING_HEADERS =
      new String[] {
        "Abstractor 1",
        "Abstractor 2",
        "Average IRR (based on abstractions w/ completed consensus)",
        "# of consensus cases completed this month "
      };

  private static final String[] USER_COMPARISON_HEADERS =
      new String[] {
        "User",
        "Role",
        "Number of abstractions completed",
        "Average time from assignment to completion days",
        "Average time to complete in minutes",
        "Average # of adverse events found per record",
        "Average # of triggers  found per record",
        "Number of consensus cases completed",
        "Average IRR (based on abstractions w/ completed consensus)"
      };

  private static final String[] MONTHLY_BREAKDOWN_HEADERS =
      new String[] {
        "Encounter ID",
        "MTF",
        "User",
        "Date assigned review",
        "Date completed review",
        "Time from assignment to competion in days",
        "Time taken to completion in minutes",
        "# of adverse events",
        "# of triggers",
        "IRR"
      };

  private static final String[] OVERVIEW_CONTENT =
      new String[] {
        "Overview of the Abstractor and ML Metrics Report",
        "This report provides insight into the efficiency of users performing abstractions for GTT. It includes all users since management leads can change a user's role to allow them to abstract cases at any point in time. These reports accumulate data as cases are abstracted, reviewed for concensus, and completed; and are added to the appropriate month's download based on the completion dates. Please consider that if an abstraction is completed on the last day of the month, the corresponding data for IRR or a consensus may not yet be available depending on whether the second abstractor has completed their review.",
        "",
        "Abstractor Pair Metrics",
        "This sheet allows you to understand the success of a unique pairing of users performing abstraction by reviewing the average IRR against the # of completed consensus cases. Note: The average IRR is determined based on the completed abstractions so the consensus case completion number can only represent records that have both abstraction and the consensus complete as of the date of the download.",
        "",
        "User comparison",
        "This sheet provides a direct comparison of user metrics for the month and specifically, at time of download. The metrics focus on averages around abstraction completion rates, time abstractions spent in their queue, time spent actually abstracting as well as an average number of triggers and adverse events found. This sheet also includes averages on the number of completed consensus cases by a particular user and their personal average IRR.",
        "",
        "Monthly Breakdown",
        "This sheet is the most granular and includes all abstraction information completed during this month, at time of the download. You can view each Encounter ID and the individual abstractions completed. Note that if both abstractions are not complete then the IRR will not be populated."
      };

  List<Integer> overViewHeaderIndexes =
      Arrays.stream(new int[] {0, 3, 6, 9}).boxed().collect(Collectors.toList());

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
    List<TQMCUserInfo> allGttUsers = TQMCHelper.getAllUsers(con);
    List<String> selectedUserIds = new ArrayList<String>();

    Map<String, List<GttWorkflow>> abstractionMap =
        TQMCHelper.getAbsractionCaseMap(con, startDate, endDate);

    Map<String, Set<String>> consensusMap = TQMCHelper.getConsensusMap(con, startDate, endDate);

    CellStyle headerStyle = workbook.createCellStyle();
    Font bold = workbook.createFont();
    bold.setBold(true);
    headerStyle.setFont(bold);
    headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
    headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    headerStyle.setWrapText(true);

    CellStyle infoStyle = workbook.createCellStyle();
    infoStyle.setWrapText(true);

    CellStyle percentageStyle = workbook.createCellStyle();
    DataFormat dataFormat = workbook.createDataFormat();
    percentageStyle.setDataFormat(dataFormat.getFormat("0.00%"));

    Sheet sheet1 = workbook.createSheet("Overview");
    for (int i = 0; i < OVERVIEW_CONTENT.length; i++) {
      Cell cell = sheet1.createRow(i).createCell(0);
      if (overViewHeaderIndexes.contains(i)) {
        cell.setCellStyle(headerStyle);
      } else {
        cell.setCellStyle(infoStyle);
      }

      cell.setCellValue(OVERVIEW_CONTENT[i]);
    }

    // create 2nd sheet
    Sheet sheet2 = workbook.createSheet("Abstractor Pair Metrics");
    Row abstractorIrrPairingsHeaderRow = sheet2.createRow(0);
    for (int i = 0; i < ABSTRACTOR_IRR_PAIRING_HEADERS.length; i++) {
      Cell cell = abstractorIrrPairingsHeaderRow.createCell(i);
      cell.setCellStyle(headerStyle);
      cell.setCellValue(ABSTRACTOR_IRR_PAIRING_HEADERS[i]);
    }

    // Create the third sheet
    Sheet sheet3 = workbook.createSheet("User Comparison");

    // header row for third sheet
    Row userComparisonsHeaderRow = sheet3.createRow(0);
    for (int i = 0; i < USER_COMPARISON_HEADERS.length; i++) {
      Cell cell = userComparisonsHeaderRow.createCell(i);
      cell.setCellStyle(headerStyle);
      cell.setCellValue(USER_COMPARISON_HEADERS[i]);
    }

    // Create the fourth sheet
    Sheet sheet4 = workbook.createSheet("Monthly Breakdown");

    // header row for 4rd sheet
    Row monthlyBreakdownHeaderRow = sheet4.createRow(0);
    for (int i = 0; i < MONTHLY_BREAKDOWN_HEADERS.length; i++) {
      Cell cell = monthlyBreakdownHeaderRow.createCell(i);
      cell.setCellStyle(headerStyle);
      cell.setCellValue(MONTHLY_BREAKDOWN_HEADERS[i]);
    }

    int sheetOneIndex = 1;
    int sheetThreeIndex = 1;
    for (int i = 1; i <= allGttUsers.size(); i++) {
      TQMCUserInfo user = allGttUsers.get(i - 1);
      Row dataRow = sheet3.createRow(i);
      dataRow.createCell(0).setCellValue(user.getFirstName() + ' ' + user.getLastName());
      dataRow.createCell(1).setCellValue(user.getRole());

      List<GttWorkflow> completedAbstractions = new ArrayList();

      if (abstractionMap.containsKey(user.getUserId())) {
        completedAbstractions = abstractionMap.get(user.getUserId());
      }

      Set<String> completedConsensusCaseIds = new HashSet();

      if (consensusMap.containsKey(user.getUserId())) {
        completedConsensusCaseIds = consensusMap.get(user.getUserId());
      }

      dataRow.createCell(2).setCellValue(completedAbstractions.size());
      dataRow
          .createCell(3)
          .setCellValue(TQMCHelper.getAverageDaysToComplete(con, user, completedAbstractions));
      dataRow
          .createCell(4)
          .setCellValue(TQMCHelper.getAverageCompletionTime(con, completedAbstractions));

      Pair<Integer, Integer> eventsAndTriggers =
          TQMCHelper.getNumberOfEventsAndTriggers(con, completedAbstractions);
      if (completedAbstractions.size() > 0) {
        dataRow
            .createCell(5)
            .setCellValue((double) eventsAndTriggers.getFirst() / completedAbstractions.size());
        dataRow
            .createCell(6)
            .setCellValue((double) eventsAndTriggers.getSecond() / completedAbstractions.size());
      }

      dataRow.createCell(7).setCellValue(completedConsensusCaseIds.size());
      if (completedConsensusCaseIds.size() > 0) {
        Cell averageIrrCell = dataRow.createCell(8);
        averageIrrCell.setCellStyle(percentageStyle);
        averageIrrCell.setCellValue(
            TQMCHelper.getAverageIrrFromConsensusCaseIds(con, completedConsensusCaseIds));
      }

      // Sheet 3 Data
      for (GttWorkflow completedAbstraction : completedAbstractions) {
        Row row = sheet4.createRow(sheetThreeIndex);
        String recordId =
            TQMCHelper.getRecordIdFromGttWorkFlowId(con, completedAbstraction.getCaseId());
        row.createCell(0).setCellValue(recordId);
        row.createCell(1).setCellValue(TQMCHelper.getMTFNameFromRecordId(con, recordId));
        row.createCell(2).setCellValue(user.getFirstName() + ' ' + user.getLastName());
        LocalDateTime assignedDate =
            TQMCHelper.getWorkflowAssignedDate(con, completedAbstraction, user);

        ZoneId easternZone = ZoneId.of("America/New_York");
        row.createCell(3).setCellValue(assignedDate.atZone(easternZone).toLocalDate().toString());

        row.createCell(4)
            .setCellValue(
                completedAbstraction
                    .getSmssTimestamp()
                    .atZone(easternZone)
                    .toLocalDate()
                    .toString());
        row.createCell(5)
            .setCellValue(
                ConversionUtils.getDaysBetween(
                    assignedDate, completedAbstraction.getSmssTimestamp()));
        row.createCell(6)
            .setCellValue(TQMCHelper.getAbstractionCompletionTime(con, completedAbstraction));
        Pair<Integer, Integer> eventsAndTriggersForAbstraction =
            TQMCHelper.getNumberOfEventsAndTriggersForGttWorkflow(con, completedAbstraction);
        row.createCell(7).setCellValue(eventsAndTriggersForAbstraction.getFirst());
        row.createCell(8).setCellValue(eventsAndTriggersForAbstraction.getSecond());
        String consensusId = TQMCHelper.getConsensusCaseFromAbstraction(con, completedAbstraction);
        double irr = TQMCHelper.getIrrFromConsensusCaseId(con, consensusId);
        Cell irrCell = row.createCell(9);
        if (irr >= 0) {
          irrCell.setCellStyle(percentageStyle);
          irrCell.setCellValue(irr);
        } else {
          irrCell.setCellValue("Waiting on Abstractor 2");
        }
        sheetThreeIndex++;
      }

      // Sheet 2 Data
      HashMap<String, Set<String>> workflowsByPartner = new HashMap<String, Set<String>>();

      for (String consensusId : completedConsensusCaseIds) {
        String partnerId =
            TQMCHelper.getPartnerForCompletedConsensus(con, user.getUserId(), consensusId);
        if (!partnerId.isEmpty() && !selectedUserIds.contains(partnerId)) {
          Set<String> newList;
          if (workflowsByPartner.containsKey(partnerId)) {
            newList = workflowsByPartner.get(partnerId);
            newList.add(consensusId);
            workflowsByPartner.put(partnerId, newList);
          } else {
            newList = new HashSet<String>();
          }
          newList.add(consensusId);
          workflowsByPartner.put(partnerId, newList);
        }
      }
      for (String partnerId : workflowsByPartner.keySet()) {
        Row row = sheet2.createRow(sheetOneIndex);
        row.createCell(0).setCellValue(user.getFirstName() + ' ' + user.getLastName());
        row.createCell(1).setCellValue(TQMCHelper.getNameFromUserName(con, partnerId));
        Cell avgIrrCell = row.createCell(2);
        avgIrrCell.setCellStyle(percentageStyle);
        avgIrrCell.setCellValue(
            TQMCHelper.getAverageIrrFromConsensusCaseIds(con, workflowsByPartner.get(partnerId)));
        row.createCell(3).setCellValue(workflowsByPartner.get(partnerId).size());
        sheetOneIndex++;
      }
      selectedUserIds.add(user.getUserId());
    }

    sheet1.setColumnWidth(0, 25000);

    for (int i = 0; i < ABSTRACTOR_IRR_PAIRING_HEADERS.length; i++) {
      sheet2.autoSizeColumn(i);
    }

    for (int i = 0; i < USER_COMPARISON_HEADERS.length; i++) {
      sheet3.autoSizeColumn(i);
    }

    for (int i = 0; i < MONTHLY_BREAKDOWN_HEADERS.length; i++) {
      sheet4.autoSizeColumn(i);
    }

    // create the file to write to
    String fileDirectory =
        AssetUtility.getRootFolderPath(this.insight, AssetUtility.INSIGHT_SPACE_KEY, true);
    String baseFilename =
        "GTT_AbstractionEfficiency_Month_" + parseInput[0] + "_" + parseInput[1] + ".xlsx";
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
