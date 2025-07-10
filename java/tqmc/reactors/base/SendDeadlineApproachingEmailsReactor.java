package tqmc.reactors.base;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.lang.text.StrSubstitutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.Review;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;
import tqmc.util.TQMCProperties;

/** Deadline Approaching Emails for Peer/Nurse Reviewers */
public class SendDeadlineApproachingEmailsReactor extends AbstractTQMCReactor {

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  private static final Logger LOGGER =
      LogManager.getLogger(SendDeadlineApproachingEmailsReactor.class);

  private static int DUE_SOON_DAYS = 5;

  private static final String EMAIL_QUERY =
      "SELECT sr.RECORD_ID, sr.ALIAS_RECORD_ID, snr.NURSE_REVIEW_ID AS CASE_ID, sw.STEP_STATUS, snr.USER_ID, tu.EMAIL, snr.DUE_DATE_REVIEW, sw.case_type, 'SOC' AS CASE_SUPER_TYPE,\n"
          + "CASE WHEN datediff('day', CURRENT_DATE(), snr.DUE_DATE_REVIEW) < "
          + DUE_SOON_DAYS
          + " THEN TRUE ELSE FALSE END AS IS_DUE_SOON, \n"
          + "CASE WHEN snr.DUE_DATE_REVIEW < CURRENT_DATE() THEN TRUE ELSE FALSE END AS IS_PAST_DUE\n"
          + "FROM SOC_NURSE_REVIEW snr\n"
          + "INNER JOIN SOC_RECORD sr ON sr.RECORD_ID = snr.RECORD_ID\n"
          + "INNER JOIN SOC_WORKFLOW sw ON sw.CASE_ID = snr.NURSE_REVIEW_ID AND sw.IS_LATEST AND sw.CASE_TYPE = 'nurse_review'\n"
          + "INNER JOIN TQMC_USER tu ON tu.USER_ID = snr.USER_ID\n"
          + "WHERE sw.STEP_STATUS IN ('not_started', 'in_progress')\n"
          + "AND snr.DELETED_AT IS NULL\n"
          + "\n"
          + "UNION ALL\n"
          + "\n"
          + "SELECT sr.RECORD_ID, sr.ALIAS_RECORD_ID, sc.CASE_ID, sw.STEP_STATUS, sc.USER_ID, tu.EMAIL, sc.DUE_DATE_REVIEW, sw.CASE_TYPE, 'SOC' AS CASE_SUPER_TYPE,\n"
          + "CASE WHEN datediff('day', CURRENT_DATE(), sc.DUE_DATE_REVIEW) < "
          + DUE_SOON_DAYS
          + " THEN TRUE ELSE FALSE END AS IS_DUE_SOON, \n"
          + "CASE WHEN sc.DUE_DATE_REVIEW < CURRENT_DATE() THEN TRUE ELSE FALSE END AS IS_PAST_DUE\n"
          + "FROM SOC_CASE sc\n"
          + "INNER JOIN SOC_RECORD sr ON sr.RECORD_ID = sc.RECORD_ID\n"
          + "INNER JOIN SOC_WORKFLOW sw ON sw.CASE_ID = sc.CASE_ID AND sw.IS_LATEST AND sw.CASE_TYPE = 'peer_review'\n"
          + "INNER JOIN TQMC_USER tu ON tu.USER_ID = sc.USER_ID\n"
          + "WHERE sw.STEP_STATUS IN ('not_started', 'in_progress')\n"
          + "AND sc.DELETED_AT IS NULL\n"
          + "\n"
          + "UNION ALL\n"
          + "\n"
          + "SELECT mnr.RECORD_ID, mnr.ALIAS_RECORD_ID, mc.CASE_ID, mw.STEP_STATUS, mc.USER_ID, tu.EMAIL, mc.DUE_DATE_REVIEW, 'medical necessity' AS CASE_TYPE, 'MN' AS CASE_SUPER_TYPE,\n"
          + "CASE WHEN datediff('day', CURRENT_DATE(), mc.DUE_DATE_REVIEW) < "
          + DUE_SOON_DAYS
          + " THEN TRUE ELSE FALSE END AS IS_DUE_SOON, \n"
          + "CASE WHEN mc.DUE_DATE_REVIEW < CURRENT_DATE() THEN TRUE ELSE FALSE END AS IS_PAST_DUE\n"
          + "FROM MN_CASE mc\n"
          + "INNER JOIN MN_RECORD mnr ON mnr.RECORD_ID = mc.RECORD_ID\n"
          + "INNER JOIN MN_WORKFLOW mw ON mw.CASE_ID = mc.CASE_ID AND mw.IS_LATEST\n"
          + "INNER JOIN TQMC_USER tu ON tu.USER_ID = mc.USER_ID\n"
          + "WHERE mw.STEP_STATUS IN ('not_started', 'in_progress')\n"
          + "AND mc.DELETED_AT IS NULL\n"
          + "ORDER BY DUE_DATE_REVIEW";

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    LocalDateTime nowUTC = ConversionUtils.getUTCFromLocalNow();
    Map<String, List<Review>> cases = new HashMap<>();
    boolean isFriday = nowUTC.getDayOfWeek().equals(DayOfWeek.FRIDAY);

    populateCasesMap(con, EMAIL_QUERY, cases);
    if (isFriday) {
      sendWeeklyEmails(cases);
    } else {
      sendDailyEmails(cases);
    }

    return new NounMetadata(true, PixelDataType.BOOLEAN);
  }

  /** Helper method to populate the cases map by userId from the given query. */
  private void populateCasesMap(Connection con, String query, Map<String, List<Review>> cases)
      throws SQLException {
    try (PreparedStatement ps = con.prepareStatement(query)) {
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        String recordId = rs.getString("RECORD_ID");
        String aliasRecordId = rs.getString("ALIAS_RECORD_ID");
        String caseId = rs.getString("CASE_ID");
        String stepStatus = rs.getString("STEP_STATUS");
        String userId = rs.getString("USER_ID");
        String email = rs.getString("EMAIL");
        LocalDate dueDateReview =
            ConversionUtils.getLocalDateFromDate(rs.getDate("DUE_DATE_REVIEW"));
        Boolean dueSoon = rs.getBoolean("IS_DUE_SOON");
        Boolean pastDue = rs.getBoolean("IS_PAST_DUE");
        String caseType = translateCaseType(rs.getString("CASE_TYPE"));
        String caseSuperType = rs.getString("CASE_SUPER_TYPE");

        cases
            .computeIfAbsent(userId, k -> new ArrayList<>())
            .add(
                new Review(
                    recordId,
                    aliasRecordId,
                    caseId,
                    stepStatus,
                    userId,
                    email,
                    dueDateReview,
                    null,
                    null,
                    dueSoon,
                    pastDue,
                    caseType,
                    caseSuperType,
                    null,
                    null,
                    null,
                    null));
      }
    }
  }

  private void sendDailyEmails(Map<String, List<Review>> cases) {
    for (Entry<String, List<Review>> entry : cases.entrySet()) {

      // only send the email if some are due soon
      if (entry.getValue().get(0).isDueSoon()) {
        String subject = createSubject(entry);
        String message = createMessage(entry);
        String emailAddress = entry.getValue().get(0).getEmail();

        try {
          TQMCHelper.sendEmail(subject, message, emailAddress, null);
        } catch (TQMCException e) {
          LOGGER.warn("Failed to send deadline email to " + emailAddress);
        }
      }
    }
  }

  private void sendWeeklyEmails(Map<String, List<Review>> cases) {
    for (Entry<String, List<Review>> entry : cases.entrySet()) {

      String subject = createSubject(entry);
      String message = createMessage(entry);
      String emailAddress = entry.getValue().get(0).getEmail();

      try {
        TQMCHelper.sendEmail(subject, message, emailAddress, null);
      } catch (TQMCException e) {
        LOGGER.warn("Failed to send password reset success email to " + emailAddress);
      }
    }
  }

  private String createSubject(Entry<String, List<Review>> entry) {
    List<Review> caseEntries = entry.getValue();
    Map<String, String> templateValues = new HashMap<>();
    templateValues.put("NUM_CASES", "" + caseEntries.size());
    templateValues.put("S", caseEntries.size() > 1 ? "s" : "");

    long numCasesSoon = caseEntries.stream().filter(r -> r.isDueSoon()).count();

    templateValues.put(
        "NUM_CASES_SOON", (numCasesSoon == 0) ? "" : "(" + numCasesSoon + " past due or due soon)");

    StrSubstitutor sub = new StrSubstitutor(templateValues);
    return sub.replace(TQMCConstants.REMINDER_EMAIL_SUBJECT);
  }

  private String createMessage(Entry<String, List<Review>> entry) {
    List<Review> caseEntries = entry.getValue();

    String caseString = "";

    for (Review caseEntry : caseEntries) {

      Map<String, String> caseValues = new HashMap<>();
      caseValues.put("ALIAS_RECORD_ID", caseEntry.getAliasRecordId());
      caseValues.put("CASE_LABEL", abbreviationToLabel(caseEntry.getCaseSuperType()));
      caseValues.put(
          "DUE_DATE",
          ConversionUtils.getLocalDateStringFromLocalDateSlashes(caseEntry.getDueDateReview()));
      caseValues.put("BASE_LINK", TQMCProperties.getInstance().getTqmcUrl());
      caseValues.put("CASE_SUPER_TYPE", caseEntry.getCaseSuperType());
      caseValues.put("CASE_TYPE", caseEntry.getCaseType());
      caseValues.put("CASE_URL_COMPONENT", caseEntry.getCaseId());

      // &#x26A0; is the html entity for warning sign
      if (caseEntry.isPastDue()) {
        caseValues.put("WARNING_ICON", "<strong> &#x26A0; PAST DUE</strong>");
      } else if (caseEntry.isDueSoon()) {
        caseValues.put("WARNING_ICON", "<strong> &#x26A0; DUE SOON</strong>");
      } else {
        caseValues.put("WARNING_ICON", "");
      }

      StrSubstitutor sub = new StrSubstitutor(caseValues);
      caseString += sub.replace(TQMCConstants.REMINDER_EMAIL_CASE);
    }

    Map<String, String> templateValues = new HashMap<>();
    templateValues.put("TODAY_DATE", ConversionUtils.getStringUTCFromLocalNow());
    templateValues.put("NUM_CASES", "" + caseEntries.size());
    templateValues.put("S", caseEntries.size() > 1 ? "s" : "");
    templateValues.put("CASE_LIST", caseString);
    templateValues.put("BASE_LINK", TQMCProperties.getInstance().getTqmcUrl());

    StrSubstitutor sub = new StrSubstitutor(templateValues);
    return sub.replace(TQMCConstants.REMINDER_EMAIL_TEMPLATE);
  }

  private String translateCaseType(String type) {
    switch (type) {
      case "peer_review":
        return "review";
      case "nurse_review":
        return "chronology";
      default:
        return null;
    }
  }

  private String abbreviationToLabel(String abbreviation) {

    switch (abbreviation) {
      case "SOC":
        return "Standard of Care (SOC)";
      case "MN":
        return "Medical Necessity (MN)";
      default:
        return null;
    }
  }
}
