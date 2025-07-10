package tqmc.reactors.gtt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import prerna.reactor.database.upload.rdbms.RDBMSEngineCreationHelper;
import prerna.reactor.database.upload.rdbms.csv.RdbmsUploadTableDataReactor;
import prerna.sablecc2.om.NounStore;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.UploadInputUtility;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.domain.user.TQMCUserInfo;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class LoadCSVtoTQMCRecordsReactor extends AbstractTQMCReactor {

  private static final Logger LOGGER = LogManager.getLogger(LoadCSVtoTQMCRecordsReactor.class);

  private LocalDateTime currentTimestamp = ConversionUtils.getUTCFromLocalNow();
  private static final DateTimeFormatter DATE_TIME_INPUT_FORMATTER =
      new DateTimeFormatterBuilder()
          .appendPattern("M/d/yyyy HH:mm")
          .appendFraction(ChronoField.MILLI_OF_SECOND, 0, 3, true)
          .toFormatter();

  // individuals to assign cases to by default
  private static final String ABSTRACTOR_1 = TQMCConstants.ABSTRACTOR + "_1";
  private static final String ABSTRACTOR_2 = TQMCConstants.ABSTRACTOR + "_2";
  //
  private static final String FORCE = "force";
  // default is "fasle" blocks any issues, "true" to removed constaints
  // "filter" only adds people that follow the right critera (adult over a day stay)
  private static final List<String> forceList =
      new ArrayList<>(Arrays.asList("true", "filter", "false"));

  // Columns from the import file that is transfered from the stage table
  private static final Map<String, String> KEY_COLUMNS = createKeyColumnsMap();

  private static Map<String, String> createKeyColumnsMap() {
    Map<String, String> keyColumnsMap = new HashMap<>();
    keyColumnsMap.put("admitdatetime", "Date");
    keyColumnsMap.put("patientdob", "Date");
    keyColumnsMap.put("dischargedatetime", "Date");
    keyColumnsMap.put("financialidnumber", "String");
    keyColumnsMap.put("dmisid", "String");
    keyColumnsMap.put("DHN", "String");
    return keyColumnsMap;
  }

  public LoadCSVtoTQMCRecordsReactor() {
    this.keysToGet =
        new String[] {
          UploadInputUtility.FILE_PATH, ABSTRACTOR_1, ABSTRACTOR_2, TQMCConstants.PHYSICIAN, FORCE
        };
    this.keyRequired = new int[] {1, 0, 0, 0, 0};
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    // Check if user has access
    if (!hasProductManagementPermission(TQMCConstants.GTT)) {
      throw new TQMCException(
          ErrorCode.FORBIDDEN, "User is needs to be a managment lead or admin.");
    }
    // Get file information
    String filePath = this.keyValue.get(UploadInputUtility.FILE_PATH);

    // Get the Abstractor ids and set to default if not found
    String ab1UserId = this.keyValue.get(ABSTRACTOR_1);
    String ab2UserId = this.keyValue.get(ABSTRACTOR_2);
    String physUserId = this.keyValue.get(TQMCConstants.PHYSICIAN);
    String force = this.keyValue.get(FORCE);

    if (force == null || "no keys defined".equals(ab1UserId)) {
      force = "false";
    }

    if (!forceList.contains(force)) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST, "Force type not following: true, filter, false");
    }

    // updates the abstractor user id to default if not passed in
    if ("no keys defined".equals(ab1UserId) || ab1UserId == null) {
      ab1UserId = tqmcProperties.getDefaultAbs1UserId();
    }
    if ("no keys defined".equals(ab2UserId) || ab2UserId == null) {
      ab2UserId = tqmcProperties.getDefaultAbs2UserId();
    }
    if ("no keys defined".equals(physUserId) || physUserId == null) {
      physUserId = tqmcProperties.getDefaultPhysUserId();
    }

    TQMCUserInfo ab1 = TQMCHelper.getTQMCUserInfo(con, ab1UserId);
    TQMCUserInfo ab2 = TQMCHelper.getTQMCUserInfo(con, ab1UserId);
    TQMCUserInfo phys = TQMCHelper.getTQMCUserInfo(con, physUserId);
    // Are both users not found or inactive
    if (ab1 == null || !ab1.getIsActive()) {
      throw new TQMCException(ErrorCode.NOT_FOUND, ab1UserId + " is not found and active.");
    }
    if (ab2 == null || !ab2.getIsActive()) {
      throw new TQMCException(ErrorCode.NOT_FOUND, ab2UserId + " is not found and active.");
    }
    if (phys == null || !phys.getIsActive()) {
      throw new TQMCException(ErrorCode.NOT_FOUND, physUserId + " is not found and active.");
    }

    LOGGER.info("File loading to TQMC(GTT): " + filePath);

    // Bulk insert file into stage table
    NounStore ns = new NounStore("all");
    ns.makeNoun(UploadInputUtility.DATABASE).addLiteral(this.engineId);
    ns.makeNoun(UploadInputUtility.FILE_PATH).addLiteral(filePath);
    ns.makeNoun(UploadInputUtility.DELIMITER).addLiteral(",");
    // Currently doesn't add here and is based on fileName
    ns.makeNoun(UploadInputUtility.TABLE_NAME).addLiteral("TQMC_RECORD_STAGE");
    // Yes to adding to the current DB
    ns.makeNoun(UploadInputUtility.ADD_TO_EXISTING).addBoolean(true);
    // Yes to replacing current table, will roll back if failed
    ns.makeNoun(UploadInputUtility.REPLACE_EXISTING).addBoolean(true);
    RdbmsUploadTableDataReactor getter = new RdbmsUploadTableDataReactor();
    getter.setInsight(insight);
    getter.setNounStore(ns);
    getter.In();
    try {
      getter.execute();
    } catch (Exception e) {
      throw new TQMCException(
          ErrorCode.INTERNAL_SERVER_ERROR, "File upload to database was unsuccessful", e);
    }

    // Extracts the same fileName formating that the reactor does to get the table name
    // Table name not returned after executing reactor
    // Copied from RdbmsUploadTableDataReactor currently line 221ish (8 lines)
    String fileName = FilenameUtils.getBaseName(filePath);
    if (fileName.contains("_____UNIQUE")) {
      // ... yeah, this is not intuitive at all,
      // but I add a timestamp at the end to make sure every file is unique
      // but i want to remove it so things are "pretty"
      fileName = fileName.substring(0, fileName.indexOf("_____UNIQUE"));
    }
    String outputTable = RDBMSEngineCreationHelper.cleanTableName(fileName).toUpperCase();
    // end of copy

    // Transfer information from stage to record table
    // Get records from the Stage table
    LOGGER.info("Getting data from stage table: " + filePath);
    String selectQuery =
        String.format(
            "SELECT DISTINCT %s FROM %s;", String.join(", ", KEY_COLUMNS.keySet()), outputTable);
    List<Map<String, String>> outputRows = new ArrayList<>();
    try (PreparedStatement ps = con.prepareStatement(selectQuery)) {
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {

        Map<String, String> rowMap = new HashMap<String, String>();
        // loop though variables and pull based on type
        for (String columnName : KEY_COLUMNS.keySet()) {
          switch (KEY_COLUMNS.get(columnName)) {
            case "Date":
              rowMap.put(
                  columnName,
                  DATE_TIME_INPUT_FORMATTER.format(
                      ConversionUtils.getLocalDateTimeFromTimestamp(rs.getTimestamp(columnName))));
              break;
            case "Integer":
              rowMap.put(columnName, rs.getString(columnName));
              break;
            case "String":
              rowMap.put(columnName, rs.getString(columnName));
              break;
          }
        }
        outputRows.add(rowMap);
      }
    }

    // Insert data from stage
    LOGGER.info("Insert data into TQMC_RECORDS");
    Map<String, Integer> outMap = new HashMap<>();
    // wrap in try to allow for the staging table to still be dropped if failed
    try {
      String uploadId = UUID.randomUUID().toString();
      String recordsTable = "TQMC_RECORD";
      String insertQuery =
          String.format(
              "INSERT INTO %s "
                  + "(ADMISSION_DATE, ADMISSION_TIME, "
                  + "AGE_ADMIT_DAYS, AGE_ADMIT_YEARS, "
                  + "AGE_DISCHARGE_DAYS, AGE_DISCHARGE_YEARS, "
                  + "DATE_OF_BIRTH, DATE_RECIEVED, DISCHARGE_DATE, "
                  + "DISCHARGE_TIME, DMIS_ID, LENGTH_OF_STAY, DHN, RECORD_ID, "
                  + "UPLOAD_ID) "
                  + "VALUES (?,?,?,?,?, ?,?,?,?,?,?,?,?,?,?)",
              recordsTable);
      Map<String, LocalDateTime> gttEligibles = new HashMap<>();
      try (PreparedStatement ps = con.prepareStatement(insertQuery)) {
        // loop through each stage row output
        // Data manipulation here as well
        for (Map<String, String> row : outputRows) {
          int parameterIndex = 1;
          // Base times
          LocalDateTime admissionTime =
              LocalDateTime.parse(row.get("admitdatetime"), DATE_TIME_INPUT_FORMATTER);
          LocalDateTime dischargeTime =
              LocalDateTime.parse(row.get("dischargedatetime"), DATE_TIME_INPUT_FORMATTER);
          LocalDateTime dob = LocalDateTime.parse(row.get("patientdob"), DATE_TIME_INPUT_FORMATTER);
          // Times including time of day
          //          LocalDateTime admissionTime = addTimeToDate(admissionDate,
          // row.get("TIME_OF_ADMISSION"));
          //          LocalDateTime dischargeTime = addTimeToDate(dischargeDate,
          // row.get("TIME_OF_DISCHARGE"));
          //			Duration duration = Duration.between(dob, admissionDate);
          ps.setObject(parameterIndex++, admissionTime);
          ps.setObject(parameterIndex++, admissionTime);
          // Age admit
          ps.setInt(parameterIndex++, (int) Duration.between(dob, admissionTime).toDays());
          int admissionAge = (int) ChronoUnit.YEARS.between(dob, admissionTime);
          ps.setInt(parameterIndex++, admissionAge);
          // Age discharge
          ps.setInt(parameterIndex++, (int) Duration.between(dob, dischargeTime).toDays());
          ps.setInt(parameterIndex++, (int) ChronoUnit.YEARS.between(dob, dischargeTime));
          // Other dates
          ps.setObject(parameterIndex++, dob.format(ConversionUtils.DATE_FORMATTER));
          ps.setObject(parameterIndex++, currentTimestamp.format(ConversionUtils.DATE_FORMATTER));
          ps.setObject(parameterIndex++, dischargeTime.format(ConversionUtils.DATE_FORMATTER));
          // discharge time
          ps.setObject(parameterIndex++, dischargeTime);
          ps.setString(parameterIndex++, properFormat(row.get("dmisid")));
          // Length of Stay
          int los = (int) Duration.between(admissionTime, dischargeTime).toDays();
          ps.setInt(parameterIndex++, los);
          // DHN
          ps.setString(parameterIndex++, row.get("DHN"));
          // Being saved to RECORD_ID but shown as ENCOUNTER_ID in FE
          String encounterId = properFormat(row.get("dmisid")) + "-" + row.get("financialidnumber");
          ps.setString(parameterIndex++, encounterId);
          ps.setString(parameterIndex++, uploadId);

          // Add list of RECORD_ID that need to be in GTT
          // Adults that stayed 24 hrs or more
          if (force.equalsIgnoreCase("false")) {
            if (admissionAge < 0 || los < 0) {
              throw new TQMCException(
                  ErrorCode.BAD_REQUEST,
                  "Age or length of stay less are negative. Please check the dates to ensure they are logically sound.");
            }
            if (admissionAge < 18 || los < 1) {
              throw new TQMCException(
                  ErrorCode.BAD_REQUEST,
                  "File contains a record with a minor and/or a length of stay less than a day. FORCE = true to override.");
            }
          } else if (force.equalsIgnoreCase("filter")) {
            if (admissionAge >= 18 && los >= 1) {
              gttEligibles.put(encounterId, dischargeTime);
            }
            // left is
          } else {
            gttEligibles.put(encounterId, dischargeTime);
          }
          ps.addBatch();
        }
        ps.executeBatch();
      }
      // Return information on what was created
      outMap.put("records_created", outputRows.size());
      outMap.put("gtt_cases_created", gttEligibles.keySet().size());

      // Generate Relevant Product cases
      LOGGER.info("Create Relevant GTT Cases: " + outMap.get("gtt_cases_created"));
      TQMCHelper.createGttCases(
          con, gttEligibles, ab1UserId, ab2UserId, physUserId, currentTimestamp);

      // Log information regarding insert
      String logQuery =
          "INSERT INTO TQMC_RECORD_FILE_UPLOAD ("
              + "UPLOAD_ID, UPLOAD_TIME, FILE_NAME, USER_ID, RECORD_COUNT) "
              + "VALUES (?,?,?,?,?);";
      try (PreparedStatement ps = con.prepareStatement(logQuery)) {
        int parameterIndex = 1;
        ps.setString(parameterIndex++, uploadId);
        ps.setObject(parameterIndex++, currentTimestamp);
        ps.setString(parameterIndex++, filePath);
        ps.setString(parameterIndex++, this.userId);
        ps.setInt(parameterIndex++, outMap.get("records_created"));
        ps.execute();
      }
    } catch (Exception e) {
      // ensure roll back before dropping staging table
      con.rollback();
      throw e;
    } finally {
      // Drop stage table regardless if the rest fails
      LOGGER.info("Dropping stage table.");
      String deleteQuery = String.format("DROP TABLE PUBLIC.%s;", outputTable);
      try (PreparedStatement ps = con.prepareStatement(deleteQuery)) {
        ps.execute();
      }
    }
    LOGGER.info("File loading complete.");
    // return the counts of uploaded
    return new NounMetadata(outMap, PixelDataType.MAP);
  }

  // Takes a string and adds zeros to ensure size of four
  private static String properFormat(String input) {
    return String.format("%04d", Integer.parseInt(input));
  }
}
