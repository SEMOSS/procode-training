package tqmc.reactors.base;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.Review;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.ConversionUtils;
import tqmc.util.TQMCHelper;
import tqmc.util.TQMCProperties;

public class SendDailyDigestEmailReactor extends AbstractTQMCReactor {

  private static final Logger LOGGER = LogManager.getLogger(SendDailyDigestEmailReactor.class);
  private static int DUE_SOON_DAYS = 5;

  private static final String EMAIL_QUERY =
      "SELECT tu.EMAIL\n"
          + "FROM tqmc_user tu\n"
          + "WHERE tu.ROLE = 'management_lead'"
          + "AND tu.IS_ACTIVE = 1";

  // TODO: include completed cases past deadlines? yes or no?
  private static final String COMPLETED_QUERY =
      "SELECT * FROM \r\n"
          + "(SELECT sr.RECORD_ID, sr.ALIAS_RECORD_ID, snr.NURSE_REVIEW_id AS CASE_ID, sw.STEP_STATUS, snr.USER_ID, tu.EMAIL, tu.FIRST_NAME AS REVIEWER_FIRST_NAME, tu.LAST_NAME AS REVIEWER_LAST_NAME, snr.DUE_DATE_REVIEW, snr.DUE_DATE_DHA, snr.UPDATED_AT AS COMPLETED_DATE, sw.case_type, 'SOC' AS CASE_SUPER_TYPE, 'N/A' AS SPECIALTY, 'None' AS SUBSPECIALTY, snr.DELETED_AT,\r\n"
          + "CASE WHEN datediff('day', CURRENT_DATE(), snr.DUE_DATE_DHA) < 5 THEN TRUE ELSE FALSE END AS IS_DUE_SOON, \r\n"
          + "CASE WHEN snr.DUE_DATE_DHA < CURRENT_DATE() THEN TRUE ELSE FALSE END AS IS_PAST_DUE\r\n"
          + "FROM SOC_NURSE_REVIEW snr\r\n"
          + "INNER JOIN SOC_RECORD sr ON sr.RECORD_ID = snr.RECORD_ID\r\n"
          + "INNER JOIN SOC_WORKFLOW sw ON sw.CASE_ID = snr.NURSE_REVIEW_ID AND sw.IS_LATEST AND sw.CASE_TYPE = 'nurse_review'\r\n"
          + "LEFT JOIN TQMC_USER tu ON tu.USER_ID = snr.USER_ID\r\n"
          + "UNION ALL\r\n"
          + "SELECT sr.RECORD_ID, sr.ALIAS_RECORD_ID, sc.CASE_ID, sw.STEP_STATUS, sc.USER_ID, tu.EMAIL, tu.FIRST_NAME AS REVIEWER_FIRST_NAME, tu.LAST_NAME AS REVIEWER_LAST_NAME, sc.DUE_DATE_REVIEW, sc.DUE_DATE_DHA, sc.UPDATED_AT AS COMPLETED_DATE, sw.CASE_TYPE, 'SOC' AS CASE_SUPER_TYPE, tqs.SPECIALTY_NAME AS SPECIALTY, tqs.SUBSPECIALTY_NAME AS SUBSPECIALTY, sc.DELETED_AT,\r\n"
          + "CASE WHEN datediff('day', CURRENT_DATE(), sc.DUE_DATE_DHA) < 5 THEN TRUE ELSE FALSE END AS IS_DUE_SOON, \r\n"
          + "CASE WHEN sc.DUE_DATE_DHA < CURRENT_DATE() THEN TRUE ELSE FALSE END AS IS_PAST_DUE\r\n"
          + "FROM SOC_CASE sc\r\n"
          + "INNER JOIN SOC_RECORD sr ON sr.RECORD_ID = sc.RECORD_ID\r\n"
          + "INNER JOIN SOC_WORKFLOW sw ON sw.CASE_ID = sc.CASE_ID AND sw.IS_LATEST AND sw.CASE_TYPE = 'peer_review'\r\n"
          + "LEFT JOIN TQMC_USER tu ON tu.USER_ID = sc.USER_ID\r\n"
          + "INNER JOIN TQMC_SPECIALTY tqs on tqs.SPECIALTY_ID = sc.SPECIALTY_ID\n"
          + "UNION ALL\r\n"
          + "SELECT mnr.RECORD_ID, mnr.ALIAS_RECORD_ID, mc.CASE_ID, mw.STEP_STATUS, mc.USER_ID, tu.EMAIL, tu.FIRST_NAME AS REVIEWER_FIRST_NAME, tu.LAST_NAME AS REVIEWER_LAST_NAME, mc.DUE_DATE_REVIEW, mc.DUE_DATE_DHA, mc.UPDATED_AT AS COMPLETED_DATE, mat.APPEAL_TYPE_NAME AS CASE_TYPE, 'MN' AS CASE_SUPER_TYPE, tqs.SPECIALTY_NAME AS SPECIALTY, tqs.SUBSPECIALTY_NAME AS SUBSPECIALTY, mc.DELETED_AT,\r\n"
          + "CASE WHEN datediff('day', CURRENT_DATE(), mc.DUE_DATE_DHA) < 5 THEN TRUE ELSE FALSE END AS IS_DUE_SOON, \r\n"
          + "CASE WHEN mc.DUE_DATE_DHA < CURRENT_DATE() THEN TRUE ELSE FALSE END AS IS_PAST_DUE\r\n"
          + "FROM MN_CASE mc\r\n"
          + "INNER JOIN MN_RECORD mnr ON mnr.RECORD_ID = mc.RECORD_ID\r\n"
          + "INNER JOIN MN_WORKFLOW mw ON mw.CASE_ID = mc.CASE_ID AND mw.IS_LATEST\r\n"
          + "LEFT JOIN TQMC_USER tu ON tu.USER_ID = mc.USER_ID\r\n"
          + "INNER JOIN TQMC_SPECIALTY tqs on tqs.SPECIALTY_ID = mc.SPECIALTY_ID\n"
          + "INNER JOIN MN_APPEAL_TYPE mat ON mat.APPEAL_TYPE_ID = mc.APPEAL_TYPE_ID)\r\n"
          + "WHERE   STEP_STATUS IN ('completed')\r\n"
          + "AND ABS(datediff('day', CURRENT_DATE(), COMPLETED_DATE)) <= 3\r\n"
          + "ORDER BY DUE_DATE_DHA";

  private static final String IN_PROGRESS_QUERY =
      "SELECT * FROM \r\n"
          + "(SELECT sr.RECORD_ID, sr.ALIAS_RECORD_ID, snr.NURSE_REVIEW_ID AS CASE_ID, sw.STEP_STATUS, snr.USER_ID, tu.EMAIL, tu.FIRST_NAME AS REVIEWER_FIRST_NAME, tu.LAST_NAME AS REVIEWER_LAST_NAME, snr.DUE_DATE_REVIEW, snr.DUE_DATE_DHA, snr.UPDATED_AT AS COMPLETED_DATE, sw.case_type, 'SOC' AS CASE_SUPER_TYPE, 'N/A' AS SPECIALTY, 'None' AS SUBSPECIALTY, snr.DELETED_AT,\r\n"
          + "CASE WHEN datediff('day', CURRENT_DATE(), snr.DUE_DATE_REVIEW) < 5 THEN TRUE ELSE FALSE END AS IS_DUE_SOON, \r\n"
          + "CASE WHEN snr.DUE_DATE_REVIEW < CURRENT_DATE() THEN TRUE ELSE FALSE END AS IS_PAST_DUE\r\n"
          + "FROM SOC_NURSE_REVIEW snr\r\n"
          + "INNER JOIN SOC_RECORD sr ON sr.RECORD_ID = snr.RECORD_ID\r\n"
          + "INNER JOIN SOC_WORKFLOW sw ON sw.CASE_ID = snr.NURSE_REVIEW_ID AND sw.IS_LATEST AND sw.CASE_TYPE = 'nurse_review'\r\n"
          + "LEFT JOIN TQMC_USER tu ON tu.USER_ID = snr.USER_ID\r\n"
          + "UNION ALL\r\n"
          + "SELECT sr.RECORD_ID, sr.ALIAS_RECORD_ID, sc.CASE_ID, sw.STEP_STATUS, sc.USER_ID, tu.EMAIL, tu.FIRST_NAME AS REVIEWER_FIRST_NAME, tu.LAST_NAME AS REVIEWER_LAST_NAME, sc.DUE_DATE_REVIEW, sc.DUE_DATE_DHA, sc.UPDATED_AT AS COMPLETED_DATE, sw.CASE_TYPE, 'SOC' AS CASE_SUPER_TYPE, tqs.SPECIALTY_NAME AS SPECIALTY, tqs.SUBSPECIALTY_NAME AS SUBSPECIALTY, sc.DELETED_AT,\r\n"
          + "CASE WHEN datediff('day', CURRENT_DATE(), sc.DUE_DATE_REVIEW) < 5 THEN TRUE ELSE FALSE END AS IS_DUE_SOON, \r\n"
          + "CASE WHEN sc.DUE_DATE_REVIEW < CURRENT_DATE() THEN TRUE ELSE FALSE END AS IS_PAST_DUE\r\n"
          + "FROM SOC_CASE sc\r\n"
          + "INNER JOIN SOC_RECORD sr ON sr.RECORD_ID = sc.RECORD_ID\r\n"
          + "INNER JOIN SOC_WORKFLOW sw ON sw.CASE_ID = sc.CASE_ID AND sw.IS_LATEST AND sw.CASE_TYPE = 'peer_review'\r\n"
          + "LEFT JOIN TQMC_USER tu ON tu.USER_ID = sc.USER_ID\r\n"
          + "INNER JOIN TQMC_SPECIALTY tqs on tqs.SPECIALTY_ID = sc.SPECIALTY_ID\n"
          + "UNION ALL\r\n"
          + "SELECT mnr.RECORD_ID, mnr.ALIAS_RECORD_ID, mc.CASE_ID, mw.STEP_STATUS, mc.USER_ID, tu.EMAIL, tu.FIRST_NAME AS REVIEWER_FIRST_NAME, tu.LAST_NAME AS REVIEWER_LAST_NAME, mc.DUE_DATE_REVIEW, mc.DUE_DATE_DHA, mc.UPDATED_AT AS COMPLETED_DATE, mat.APPEAL_TYPE_NAME AS CASE_TYPE, 'MN' AS CASE_SUPER_TYPE, tqs.SPECIALTY_NAME AS SPECIALTY, tqs.SUBSPECIALTY_NAME AS SUBSPECIALTY, mc.DELETED_AT,\r\n"
          + "CASE WHEN datediff('day', CURRENT_DATE(), mc.DUE_DATE_REVIEW) < 5 THEN TRUE ELSE FALSE END AS IS_DUE_SOON, \r\n"
          + "CASE WHEN mc.DUE_DATE_REVIEW < CURRENT_DATE() THEN TRUE ELSE FALSE END AS IS_PAST_DUE\r\n"
          + "FROM MN_CASE mc\r\n"
          + "INNER JOIN MN_RECORD mnr ON mnr.RECORD_ID = mc.RECORD_ID\r\n"
          + "INNER JOIN MN_WORKFLOW mw ON mw.CASE_ID = mc.CASE_ID AND mw.IS_LATEST\r\n"
          + "LEFT JOIN TQMC_USER tu ON tu.USER_ID = mc.USER_ID\r\n"
          + "INNER JOIN TQMC_SPECIALTY tqs on tqs.SPECIALTY_ID = mc.SPECIALTY_ID\n"
          + "INNER JOIN MN_APPEAL_TYPE mat ON mat.APPEAL_TYPE_ID = mc.APPEAL_TYPE_ID)\r\n"
          + "WHERE   STEP_STATUS IN ('unassigned','not_started','in_progress')\r\n"
          + "    AND (\r\n"
          + "           /* --- CASE-TYPE-SPECIFIC WINDOWS --- */\r\n"
          + "           (CASE_TYPE = 'Routine External Review'\r\n"
          + "                AND DATEDIFF('day', CURRENT_DATE(), DUE_DATE_DHA) <=  6)\r\n"
          + "           OR (CASE_TYPE = 'Urgent External Review'\r\n"
          + "                AND DATEDIFF('day', CURRENT_DATE(), DUE_DATE_DHA) <=  6)\r\n"
          + "           OR (CASE_TYPE = 'Second Level Appeal - Concurrent Review Denial'\r\n"
          + "                AND DATEDIFF('day', CURRENT_DATE(), DUE_DATE_DHA) <=  1)\r\n"
          + "           OR (CASE_TYPE = 'Second Level Appeal - Expedited Pre-Admission or Pre-Procedure Denial'\r\n"
          + "                AND DATEDIFF('day', CURRENT_DATE(), DUE_DATE_DHA) <=  1)\r\n"
          + "           OR (CASE_TYPE = 'Second Level Appeal - Non-Expedited'\r\n"
          + "                AND DATEDIFF('day', CURRENT_DATE(), DUE_DATE_DHA) <= 10)\r\n"
          + "           OR (CASE_SUPER_TYPE = 'SOC'\r\n"
          + "                AND DATEDIFF('day', CURRENT_DATE(), DUE_DATE_DHA) <= 10)\r\n"
          + "		)\r\n"
          + "ORDER BY DUE_DATE_DHA";

  private static final String NOT_STARTED_QUERY =
      "SELECT * FROM \r\n"
          + "(SELECT sr.RECORD_ID, sr.ALIAS_RECORD_ID, snr.NURSE_REVIEW_ID AS CASE_ID, sw.STEP_STATUS, snr.USER_ID, tu.EMAIL, tu.FIRST_NAME AS REVIEWER_FIRST_NAME, tu.LAST_NAME AS REVIEWER_LAST_NAME, snr.DUE_DATE_REVIEW, snr.DUE_DATE_DHA, snr.UPDATED_AT AS COMPLETED_DATE, sw.case_type, 'SOC' AS CASE_SUPER_TYPE, 'N/A' AS SPECIALTY, 'None' AS SUBSPECIALTY, snr.DELETED_AT, \r\n"
          + "CASE WHEN datediff('day', CURRENT_DATE(), snr.DUE_DATE_REVIEW) < 5 THEN TRUE ELSE FALSE END AS IS_DUE_SOON, \r\n"
          + "CASE WHEN snr.DUE_DATE_REVIEW < CURRENT_DATE() THEN TRUE ELSE FALSE END AS IS_PAST_DUE\r\n"
          + "FROM SOC_NURSE_REVIEW snr\r\n"
          + "INNER JOIN SOC_RECORD sr ON sr.RECORD_ID = snr.RECORD_ID\r\n"
          + "INNER JOIN SOC_WORKFLOW sw ON sw.CASE_ID = snr.NURSE_REVIEW_ID AND sw.IS_LATEST AND sw.CASE_TYPE = 'nurse_review'\r\n"
          + "LEFT JOIN TQMC_USER tu ON tu.USER_ID = snr.USER_ID\r\n"
          + "UNION ALL\r\n"
          + "SELECT sr.RECORD_ID, sr.ALIAS_RECORD_ID, sc.CASE_ID, sw.STEP_STATUS, sc.USER_ID, tu.EMAIL, tu.FIRST_NAME AS REVIEWER_FIRST_NAME, tu.LAST_NAME AS REVIEWER_LAST_NAME, sc.DUE_DATE_REVIEW, sc.DUE_DATE_DHA, sc.UPDATED_AT AS COMPLETED_DATE, sw.CASE_TYPE, 'SOC' AS CASE_SUPER_TYPE, tqs.SPECIALTY_NAME AS SPECIALTY, tqs.SUBSPECIALTY_NAME AS SUBSPECIALTY, sc.DELETED_AT,\r\n"
          + "CASE WHEN datediff('day', CURRENT_DATE(), sc.DUE_DATE_REVIEW) < 5 THEN TRUE ELSE FALSE END AS IS_DUE_SOON, \r\n"
          + "CASE WHEN sc.DUE_DATE_REVIEW < CURRENT_DATE() THEN TRUE ELSE FALSE END AS IS_PAST_DUE\r\n"
          + "FROM SOC_CASE sc\r\n"
          + "INNER JOIN SOC_RECORD sr ON sr.RECORD_ID = sc.RECORD_ID\r\n"
          + "INNER JOIN SOC_WORKFLOW sw ON sw.CASE_ID = sc.CASE_ID AND sw.IS_LATEST AND sw.CASE_TYPE = 'peer_review'\r\n"
          + "LEFT JOIN TQMC_USER tu ON tu.USER_ID = sc.USER_ID\r\n"
          + "INNER JOIN TQMC_SPECIALTY tqs on tqs.SPECIALTY_ID = sc.SPECIALTY_ID\n"
          + "UNION ALL\r\n"
          + "SELECT mnr.RECORD_ID, mnr.ALIAS_RECORD_ID, mc.CASE_ID, mw.STEP_STATUS, mc.USER_ID, tu.EMAIL, tu.FIRST_NAME AS REVIEWER_FIRST_NAME, tu.LAST_NAME AS REVIEWER_LAST_NAME, mc.DUE_DATE_REVIEW, mc.DUE_DATE_DHA, mc.UPDATED_AT AS COMPLETED_DATE, mat.APPEAL_TYPE_NAME AS CASE_TYPE, 'MN' AS CASE_SUPER_TYPE, tqs.SPECIALTY_NAME AS SPECIALTY, tqs.SUBSPECIALTY_NAME AS SUBSPECIALTY, mc.DELETED_AT,\r\n"
          + "CASE WHEN datediff('day', CURRENT_DATE(), mc.DUE_DATE_REVIEW) < 5 THEN TRUE ELSE FALSE END AS IS_DUE_SOON, \r\n"
          + "CASE WHEN mc.DUE_DATE_REVIEW < CURRENT_DATE() THEN TRUE ELSE FALSE END AS IS_PAST_DUE\r\n"
          + "FROM MN_CASE mc\r\n"
          + "INNER JOIN MN_RECORD mnr ON mnr.RECORD_ID = mc.RECORD_ID\r\n"
          + "INNER JOIN MN_WORKFLOW mw ON mw.CASE_ID = mc.CASE_ID AND mw.IS_LATEST\r\n"
          + "LEFT JOIN TQMC_USER tu ON tu.USER_ID = mc.USER_ID\r\n"
          + "INNER JOIN TQMC_SPECIALTY tqs on tqs.SPECIALTY_ID = mc.SPECIALTY_ID\n"
          + "INNER JOIN MN_APPEAL_TYPE mat ON mat.APPEAL_TYPE_ID = mc.APPEAL_TYPE_ID)\r\n"
          + "WHERE   STEP_STATUS IN ('unassigned','not_started')\r\n"
          + "    AND DELETED_AT IS NULL"
          + "    AND (\r\n"
          + "           /* --- CASE-TYPE-SPECIFIC WINDOWS --- */\r\n"
          + "           (CASE_TYPE = 'Routine External Review'\r\n"
          + "                AND DATEDIFF('day', CURRENT_DATE(), DUE_DATE_DHA) >  6\r\n"
          + "                AND DATEDIFF('day', CURRENT_DATE(), DUE_DATE_DHA) <= 10)\r\n"
          + "           OR (CASE_TYPE = 'Urgent External Review'\r\n"
          + "                AND DATEDIFF('day', CURRENT_DATE(), DUE_DATE_DHA) >  6)\r\n"
          + "           OR (CASE_TYPE = 'Second Level Appeal - Concurrent Review Denial'\r\n"
          + "                AND DATEDIFF('day', CURRENT_DATE(), DUE_DATE_DHA) >  1\r\n"
          + "                AND DATEDIFF('day', CURRENT_DATE(), DUE_DATE_DHA) <= 2)\r\n"
          + "           OR (CASE_TYPE = 'Second Level Appeal - Expedited Pre-Admission or Pre-Procedure Denial'\r\n"
          + "                AND DATEDIFF('day', CURRENT_DATE(), DUE_DATE_DHA) >  1\r\n"
          + "                AND DATEDIFF('day', CURRENT_DATE(), DUE_DATE_DHA) <= 2)\r\n"
          + "           OR (CASE_TYPE = 'Second Level Appeal - Non-Expedited'\r\n"
          + "                AND DATEDIFF('day', CURRENT_DATE(), DUE_DATE_DHA) > 10\r\n"
          + "                AND DATEDIFF('day', CURRENT_DATE(), DUE_DATE_DHA) <= 21)\r\n"
          + "           OR (CASE_SUPER_TYPE = 'SOC'\r\n"
          + "                AND DATEDIFF('day', CURRENT_DATE(), DUE_DATE_DHA) > 10\r\n"
          + "                AND DATEDIFF('day', CURRENT_DATE(), DUE_DATE_DHA) <= 21)\r\n"
          + "		)\r\n"
          + "ORDER BY DUE_DATE_DHA";

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    /** Daily Digest information for Management Leads */
    List<Review> completedCases = populateCases(con, COMPLETED_QUERY);
    List<Review> inProgressCases = populateCases(con, IN_PROGRESS_QUERY);
    List<Review> NotStartedCases = populateCases(con, NOT_STARTED_QUERY);

    String subject = createEmailSubject();
    String message = createEmailMessage(completedCases, inProgressCases, NotStartedCases);

    List<String> emailAddresses = createEmailList(con);
    emailAddresses
        .parallelStream()
        .forEach(
            address -> {
              try {
                TQMCHelper.sendEmail(subject, message, address, null);
              } catch (TQMCException e) {
                LOGGER.warn("Failed to send digest email to " + address);
              }
            });

    return new NounMetadata(true, PixelDataType.BOOLEAN);
  }

  private String createEmailSubject() {
    return "TQMC - Daily Review Digest";
  }

  private String createEmailMessage(
      List<Review> completedCases, List<Review> inProgressCases, List<Review> notStartedCases) {

    StringBuilder sb = new StringBuilder();
    sb.append("<html>");
    sb.append("<p>Hello,</p>");
    sb.append("<p>Here is today's automated status update on upcoming TQMC reviews.</p>");
    sb.append("<br>");
    sb.append("<p><strong>Recently Completed Reviews: </strong></p>");
    sb.append(
        "<p>The following table contains all reviews which have been completed within the last three days.</p>");

    if (completedCases.size() == 0) {
      sb.append("<p><strong>No reviews have been completed in the last three days.</strong></p>");
    } else {
      sb.append(
          "<table border=\"1\" cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse:collapse;\">");
      sb.append("<thead>");
      sb.append("<tr>");
      sb.append("<th>Case ID</th>"); // Alias Record ID
      sb.append("<th>Type of review</th>");
      sb.append("<th>Attested Specialty, Subspecialty</th>");
      sb.append("<th>Reviewer name</th>");
      sb.append("<th>Review due date</th>");
      sb.append("<th>DHA due date</th>");
      sb.append("<th>Completed date</th>");
      sb.append("<th>Link</th>");
      sb.append("</tr>");
      sb.append("</thead>");

      sb.append("<tbody>");
      for (Review completedCase : completedCases) {
        sb.append("<tr>");
        sb.append("<td>" + completedCase.getAliasRecordId() + "</td>");
        sb.append(
            "<td>"
                + completedCase.getCaseSuperType()
                + " - "
                + convertCaseType(completedCase.getCaseType())
                + "</td>");
        String subspecialtyString =
            completedCase.getSubspecialty() == "" ? "" : ": " + completedCase.getSubspecialty();
        sb.append("<td>" + completedCase.getSpecialty() + subspecialtyString + "</td>");
        sb.append(
            "<td>"
                + completedCase.getReviewerLastName()
                + ", "
                + completedCase.getReviewerFirstName()
                + "</td>");
        sb.append(
            "<td>"
                + ConversionUtils.getLocalDateStringFromLocalDateSlashes(
                    completedCase.getDueDateReview())
                + "</td>");
        sb.append(
            "<td>"
                + ConversionUtils.getLocalDateStringFromLocalDateSlashes(
                    completedCase.getDueDateDha())
                + "</td>");
        sb.append(
            "<td>"
                + ConversionUtils.getLocalDateStringFromLocalDateSlashes(
                    completedCase.getCompletedDate())
                + "</td>");
        sb.append("<td>" + createCaseLink(completedCase) + "</td>");
        sb.append("</tr>");
      }
      sb.append("</tbody>");
      sb.append("</table>");
    }
    sb.append("<br>");

    sb.append("<p><strong>Time Sensitive Reviews: </strong></p>");
    sb.append(
        "<p>The following table contains all unfinished reviews which are due soon. This includes</p>");
    sb.append("<ul>");
    sb.append("<li>SOC reviews within 10 days of the DHA due date</li>");
    sb.append("<li>MN second level non-expedited reviews within 10 days of the DHA due date</li>");
    sb.append("<li>MN external reviews within 6 days of the DHA due date</li>");
    sb.append(
        "<li>MN second level expedited or concurrent reviews within 1 day of the DHA due date</li>");
    sb.append("</ul>");

    if (inProgressCases.size() == 0) {
      sb.append("<p><strong>There are no time sensitive reviews.</strong></p>");
    } else {
      sb.append(
          "<table border=\"1\" cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse:collapse;\">");
      sb.append("<thead>");
      sb.append("<tr>");
      sb.append("<th>Case ID</th>");
      sb.append("<th>Type of review</th>");
      sb.append("<th>Specialty, Subspecialty</th>");
      sb.append("<th>Reviewer name</th>");
      sb.append("<th>Review due date</th>");
      sb.append("<th>DHA due date</th>");
      sb.append("<th>Status</th>");
      sb.append("<th>Link</th>");
      sb.append("</tr>");
      sb.append("</thead>");

      sb.append("<tbody>");
      for (Review inProgressCase : inProgressCases) {
        sb.append("<tr>");
        sb.append("<td>" + inProgressCase.getAliasRecordId() + "</td>");
        sb.append(
            "<td>"
                + inProgressCase.getCaseSuperType()
                + " - "
                + convertCaseType(inProgressCase.getCaseType())
                + "</td>");
        String subspecialtyString =
            inProgressCase.getSubspecialty() == "" ? "" : ": " + inProgressCase.getSubspecialty();
        sb.append("<td>" + inProgressCase.getSpecialty() + subspecialtyString + "</td>");
        sb.append(
            "<td>"
                + ((inProgressCase.getReviewerLastName() == null)
                    ? ""
                    : inProgressCase.getReviewerLastName()
                        + ", "
                        + inProgressCase.getReviewerFirstName())
                + "</td>");
        sb.append(
            "<td>"
                + ConversionUtils.getLocalDateStringFromLocalDateSlashes(
                    inProgressCase.getDueDateReview())
                + "</td>");
        sb.append(
            "<td>"
                + ConversionUtils.getLocalDateStringFromLocalDateSlashes(
                    inProgressCase.getDueDateDha())
                + "</td>");
        sb.append("<td>" + convertCaseStatus(inProgressCase.getStepStatus()) + "</td>");
        sb.append("<td>" + createCaseLink(inProgressCase) + "</td>");
        sb.append("</tr>");
      }
      sb.append("</tbody>");
      sb.append("</table>");
    }
    sb.append("<br>");

    sb.append("<p><strong>Upcoming Reviews: </strong></p>");
    sb.append(
        "<p>The following table contains all reviews that are not yet started and have an approaching due date. This includes</p>");
    sb.append("<ul>");
    sb.append("<li>SOC reviews due within 21 days of the DHA due date</li>");
    sb.append("<li>MN second level non-expedited reviews within 21 days of the DHA due date</li>");
    sb.append("<li>MN external reviews within 10 days of the DHA due date</li>");
    sb.append(
        "<li>MN second level expedited or concurrent reviews within 2 days of the DHA due date</li>");
    sb.append("</ul>");
    if (notStartedCases.size() == 0) {
      sb.append("<p><strong>There are no upcoming reviews.</strong></p>");
    } else {
      sb.append(
          "<table border=\"1\" cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse:collapse;\">");
      sb.append("<thead>");
      sb.append("<tr>");
      sb.append("<th>Case ID</th>");
      sb.append("<th>Type of review</th>");
      sb.append("<th>Specialty, Subspecialty</th>");
      sb.append("<th>Reviewer name</th>");
      sb.append("<th>Review due date</th>");
      sb.append("<th>DHA due date</th>");
      sb.append("<th>Status</th>");
      sb.append("<th>Link</th>");
      sb.append("</tr>");
      sb.append("</thead>");

      sb.append("<tbody>");
      for (Review notStartedCase : notStartedCases) {
        sb.append("<tr>");
        sb.append("<td>" + notStartedCase.getAliasRecordId() + "</td>");
        sb.append(
            "<td>"
                + notStartedCase.getCaseSuperType()
                + " - "
                + convertCaseType(notStartedCase.getCaseType())
                + "</td>");
        String subspecialtyString =
            notStartedCase.getSubspecialty() == "" ? "" : ": " + notStartedCase.getSubspecialty();
        sb.append("<td>" + notStartedCase.getSpecialty() + subspecialtyString + "</td>");
        sb.append(
            "<td>"
                + ((notStartedCase.getReviewerLastName() == null)
                    ? ""
                    : notStartedCase.getReviewerLastName()
                        + ", "
                        + notStartedCase.getReviewerFirstName())
                + "</td>");
        sb.append(
            "<td>"
                + ConversionUtils.getLocalDateStringFromLocalDateSlashes(
                    notStartedCase.getDueDateReview())
                + "</td>");
        sb.append(
            "<td>"
                + ConversionUtils.getLocalDateStringFromLocalDateSlashes(
                    notStartedCase.getDueDateDha())
                + "</td>");
        sb.append("<td>" + convertCaseStatus(notStartedCase.getStepStatus()) + "</td>");
        sb.append("<td>" + createCaseLink(notStartedCase) + "</td>");
        sb.append("</tr>");
      }
      sb.append("</tbody>");
      sb.append("</table>");
    }

    // closing
    sb.append("<br>");
    sb.append(
        "<p><strong>Please Note:</strong> This email was sent from an unmonitored address. For any inquiries or responses, please direct your emails to <a href=\"mailto:tqmc@deloitte.com\">tqmc@deloitte.com</a>. Thank you for your understanding.</p>");
    sb.append("</html>");

    return sb.toString();
  }

  private String convertCaseType(String caseType) {
    if (caseType.equals("peer_review")) {
      return "Peer Review";
    }
    if (caseType.equals("nurse_review")) {
      return "Nurse Review";
    }
    return caseType;
  }

  private String convertCaseStatus(String status) {
    if (status.equals("not_started")) {
      return "Not Started";
    }
    if (status.equals("in_progress")) {
      return "In Progress";
    }
    if (status.equals("completed")) {
      return "Completed";
    }
    if (status.equals("unassigned")) {
      return "Unassigned";
    }
    return status;
  }

  private String createCaseLink(Review theCase) {

    String linkSegment1 = TQMCProperties.getInstance().getTqmcUrl();
    String linkSegment2 = theCase.getCaseSuperType();
    String linkSegment3 = "management";
    String linkSegment4 = theCase.getRecordId();

    return "<a href=\""
        + linkSegment1
        + "/#/"
        + linkSegment2
        + "/"
        + linkSegment3
        + "/"
        + linkSegment4
        + "\">Link</a>";
  }

  private List<String> createEmailList(Connection con) throws SQLException {

    List<String> addresses = new ArrayList<>();
    try (PreparedStatement ps = con.prepareStatement(EMAIL_QUERY)) {
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        addresses.add(rs.getString("EMAIL"));
      }
      return addresses;
    }
  }

  private List<Review> populateCases(Connection con, String query) throws SQLException {
    LinkedList<Review> cases = new LinkedList<>();
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
        LocalDate dueDateDha = ConversionUtils.getLocalDateFromDate(rs.getDate("DUE_DATE_DHA"));
        LocalDate completedDate = rs.getTimestamp("COMPLETED_DATE").toLocalDateTime().toLocalDate();
        Boolean dueSoon = rs.getBoolean("IS_DUE_SOON");
        Boolean pastDue = rs.getBoolean("IS_PAST_DUE");
        String caseType = rs.getString("CASE_TYPE");
        String caseSuperType = rs.getString("CASE_SUPER_TYPE");
        String reviewerFirstName = rs.getString("REVIEWER_FIRST_NAME");
        String reviewerLastName = rs.getString("REVIEWER_LAST_NAME");
        String specialty = rs.getString("SPECIALTY") == null ? "N/A" : rs.getString("SPECIALTY");
        String subspecialty = rs.getString("SUBSPECIALTY");
        if (subspecialty == null || subspecialty.equals("None")) {
          subspecialty = "";
        }

        cases.add(
            new Review(
                recordId,
                aliasRecordId,
                caseId,
                stepStatus,
                userId,
                email,
                dueDateReview,
                dueDateDha,
                completedDate,
                dueSoon,
                pastDue,
                caseType,
                caseSuperType,
                reviewerFirstName,
                reviewerLastName,
                specialty,
                subspecialty));
      }
    }
    return cases;
  }
}
