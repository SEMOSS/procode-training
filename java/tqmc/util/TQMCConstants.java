package tqmc.util;

import com.google.common.collect.Sets;
import java.util.Set;

public class TQMCConstants {

  public static final String[] CCS = {"tevanburen@deloitte.com", "tlokeshrao@deloitte.com"};
  public static final String[] DOTCOM_BCCS = {"kermahoney@deloitte.com"};

  // tables for all
  public static final String TABLE_TQMC_USER = "TQMC_USER__";
  public static final String TABLE_TQMC_USER_PRODUCT = "TQMC_USER_PRODUCT__";
  public static final String TABLE_TQMC_PRODUCT = "TQMC_PRODUCT__";

  // tables for GTT
  public static final String TABLE_GTT_ABSTRACTOR_CASE = "GTT_ABSTRACTOR_CASE";
  public static final String TABLE_GTT_CONSENSUS_CASE = "GTT_CONSENSUS_CASE";
  public static final String TABLE_GTT_PHYSICIAN_CASE = "GTT_PHYSICIAN_CASE";
  public static final String TABLE_GTT_CASE_TIME = "GTT_CASE_TIME__";

  // tables for SOC
  public static final String TABLE_SOC_RECORD = "SOC_RECORD";
  public static final String TABLE_SOC_CASE = "SOC_CASE";
  public static final String TABLE_SOC_NURSE_REVIEW = "SOC_NURSE_REVIEW";
  public static final String TABLE_SOC_WORKFLOW = "SOC_WORKFLOW";
  public static final String TABLE_SOC_RECORD_MTF = "SOC_RECORD_MTF";
  public static final String TABLE_SOC_CASE_PROVIDER_EVALUATION = "SOC_CASE_PROVIDER_EVALUATION";
  public static final String TABLE_SOC_RECORD_FILE = "SOC_RECORD_FILE";

  // tables for MN
  public static final String TABLE_MN_CARE_CONTRACTORS = "MN_CARE_CONTRACTOR";
  public static final String TABLE_MN_RECORD = "MN_RECORD";
  public static final String TABLE_MN_CASE = "MN_CASE";
  public static final String TABLE_MN_RECORD_FILE = "MN_RECORD_FILE";
  public static final String TABLE_MN_APPEAL_TYPE = "MN_APPEAL_TYPE";

  // tables for MCSC
  public static final String TABLE_MCSC_CASE = "MCSC_CASE";

  // tables for DP
  public static final String TABLE_DP_CASE = "DP_CASE";

  // tables for QU
  public static final String TABLE_QU_RECORD_FILE = "QU_RECORD_FILE";

  // tables for QU
  public static final String TABLE_QU_RECORD = "QU_RECORD";

  // tables for DP
  public static final String TABLE_DP_QUESTION_TEMPLATE = "DP_QUESTION_TEMPLATE";
  public static final String TABLE_DP_RESPONSE_TEMPLATE = "DP_RESPONSE_TEMPLATE";

  // id prefixes
  public static final String REC = "REC";
  public static final String NR = "NR";
  public static final String CASE = "CASE";

  // reactor arguments
  public static final String USER_ID = "userId";
  public static final String CASE_ID = "caseId";
  public static final String NURSE_REVIEW_ID = "nurseReviewId";
  public static final String NURSE_REVIEW = "socNurseReview";
  public static final String CASE_TYPE = "caseType";
  public static final String PRODUCT = "product";
  public static final String OLD_ASSIGNEE_USER_ID = "oldAssigneeUserId";
  public static final String NEW_ASSIGNEE_USER_ID = "newAssigneeUserId";
  public static final String NEW_ASSIGNEE_USER_ID_2 = "newAssigneeUserId2";
  public static final String RECORD_ID = "recordId";
  public static final String RECORD_FILE_ID = "recordFileId";
  public static final String COMPLETE_STATUS = "complete";
  public static final String NOTE_ID = "noteId";
  public static final String NOTE = "note";
  public static final String IS_EXTERNAL = "isExternal";
  public static final String START_TIMER = "startTimer";
  public static final String TIMER_STATUS = "timerStatus";
  public static final String TIME_SECONDS = "timeSeconds";
  public static final String SORT = "sort";
  public static final String FILTER = "filter";
  public static final String PAGINATION = "pagination";
  public static final String[] LIST_REACTOR_ARGUMENTS = new String[] {SORT, FILTER, PAGINATION};

  // product keys
  public static final String GTT = "gtt"; // global trigger tool
  public static final String ORYX = "oryx";
  public static final String SOC = "soc"; // standard of care
  public static final String MN = "mn"; // medical necessity
  public static final String MCSC = "mcsc"; // managed care support contractor
  public static final String DP = "dp"; // designated provider

  // user roles
  public static final String ABSTRACTOR = "abstractor";
  public static final String MANAGEMENT_LEAD = "management_lead";
  public static final String PHYSICIAN = "physician";
  public static final String NURSE_REVIEWER = "nurse_reviewer";
  public static final String CONTRACTING_LEAD = "contracting_lead";
  public static final String PEER_REVIEWER = "peer_reviewer";
  public static final String QUALITY_REVIEWER = "quality_reviewer";
  public static final String ADMIN = "admin";
  public static final Set<String> USER_ROLES =
      Sets.newHashSet(
          ABSTRACTOR,
          MANAGEMENT_LEAD,
          PHYSICIAN,
          NURSE_REVIEWER,
          CONTRACTING_LEAD,
          PEER_REVIEWER,
          ADMIN);
  public static final Set<String> NEED_NPI =
      Sets.newHashSet(TQMCConstants.MN, TQMCConstants.SOC, TQMCConstants.DP, TQMCConstants.MCSC);

  // timer statuses
  public static final String TIMER_RUNNING = "running";
  public static final String TIMER_PAUSED = "paused";

  // workflow
  public static final String DEFAULT_SYSTEM_USER = "system";

  // gtt case type
  public static final String GTT_CASE_TYPE_ABSTRACTOR = "abstraction";
  public static final String GTT_CASE_TYPE_CONSENSUS = "consensus";
  public static final String GTT_CASE_TYPE_PHYSICIAN = "physician";

  // gtt stage
  public static final String GTT_STAGE_ABSTRACTION = "ABSTRACTION";
  public static final String GTT_STAGE_CONSENSUS = "CONSENSUS";
  public static final String GTT_STAGE_PHYSICIAN_REVIEW = "PHYSICIAN REVIEW";
  public static final String GTT_STAGE_REASSIGNED = "REASSIGNED";
  public static final String GTT_STAGE_COMPLETE = "COMPLETE";

  // soc case type
  public static final String CASE_TYPE_PEER_REVIEW = "peer_review";
  public static final String CASE_TYPE_NURSE_REVIEW = "nurse_review";

  // case step status
  public static final String CASE_STEP_STATUS_UNASSIGNED = "unassigned";
  public static final String CASE_STEP_STATUS_NOT_STARTED = "not_started";
  public static final String CASE_STEP_STATUS_IN_PROGRESS = "in_progress";
  public static final String CASE_STEP_STATUS_COMPLETED = "completed";
  public static final String CASE_STEP_STATUS_REASSIGNED = "reassigned";

  // SOC_CASE_PROVIDER_EVALUATION table columns
  public static final String SOC_RECORD_ID = "RECORD_ID";
  public static final String PROVIDER_NAME = "PROVIDER_NAME";
  public static final String STANDARDS_MET = "STANDARDS_MET";
  public static final String STANDARDS_RATIONALE = "STANDARDS_RATIONALE";
  public static final String DEVIATION_CLAIM = "DEVIATION_CLAIM";
  public static final String DEVIATION_CLAIM_RATIONALE = "DEVIATION_CLAIM_RATIONALE";
  public static final String RECORD_REFERENCES = "RECORD_REFERENCES";

  // SOC_CASE_SYSTEM_EVALUATION table columns
  public static final String CASE_SYSTEM_EVALUATION_ID = "CASE_SYSTEM_EVALUATION_ID";
  public static final String REFERENCES = "REFERENCES";
  public static final String SYSTEM_ISSUE = "SYSTEM_ISSUE";
  public static final String SYSTEM_ISSUE_RATIONALE = "SYSTEM_ISSUE_RATIONALE";
  public static final String SYSTEM_ISSUE_JUSTIFICATION = "SYSTEM_ISSUE_JUSTIFICATION";
  public static final String CREATED_AT = "CREATED_AT";
  public static final String UPDATED_AT = "UPDATED_AT";
  public static final String ATTESTATION_SIGNATURE = "ATTESTATION_SIGNATURE";
  public static final String ATTESTED_AT = "ATTESTED_AT";

  // MN_CARE_CONTRACTOR table columns
  public static final String CARE_CONTRACTOR_NAME = "CARE_CONTRACTOR_NAME";

  // QU_RECORD table columns
  public static final String CASE_LENGTH_DAYS = "CASE_LENGTH_DAYS";
  public static final String RECEIVED_AT = "RECEIVED_AT";
  public static final String REQUESTED_AT = "REQUESTED_AT";
  public static final String QU_RECORD_RECORD_ID = "RECORD_ID";
  public static final String QU_RECORD_UPDATED_AT = "UPDATED_AT";
  public static final String PATIENT_FIRST_NAME = "PATIENT_FIRST_NAME";
  public static final String PATIENT_LAST_NAME = "PATIENT_LAST_NAME";
  public static final String REFERENCE_ID = "REFERENCE_ID";
  public static final String CARE_CONTRACTOR_ID = "CARE_CONTRACTOR_ID";
  public static final String ALIAS_RECORD_ID = "ALIAS_RECORD_ID";
  public static final String CLAIM_NUMBER = "CLAIM_NUMBER";
  public static final String MISSING_RECEIVED_AT = "MISSING_RECEIVED_AT";
  public static final String MISSING_REQUESTED_AT = "MISSING_REQUESTED_AT";

  public static final String DP_QUESTION_TEMPLATE_ID = "QUESTION_TEMPLATE_ID";
  public static final String QUESTION = "QUESTION";
  public static final String DP_QUESTION_VERSION = "VERSION";
  public static final String DP_QUESTION_IS_CURRENT = "IS_CURRENT";
  public static final String DP_QUESTION_TITLE = "TITLE";
  public static final String DP_QUESTION_INFO = "INFO";
  public static final String QUESTION_VERSION = "QUESTION_VERSION"; // calculated column

  // dp_response_template columns
  public static final String DP_RESPONSE_TEMPLATE_ID = "RESPONSE_TEMPLATE_ID";
  public static final String DP_RESPONSE_QUESTION_TEMPLATE_ID = "QUESTION_TEMPLATE_ID";
  public static final String RESPONSE = "RESPONSE";
  public static final String DP_RESPONSE_VERSION = "VERSION";
  public static final String DP_RESPONSE_IS_CURRENT = "IS_CURRENT";
  public static final String RESPONSE_VERSION = "RESPONSE_VERSION"; // calculated column

  // email templates
  public static final String EMAIL_SENDER = "tqmc-donotreply@medqualitymonitor.com";
  public static final String CASE_ASSIGNMENT_EMAIL_TEMPLATE =
      "<html><p>Hello,</p><p>A case of type ${CASE_TYPE} has been ${ACTION_PREFIX}assigned to you for review on the TQMC system. Your review is due on or before ${DUE_DATE}.</p><p>Access the case by navigating to <a href=\"${URL}/#/${PATH}/${CASE_ID}\">${URL}/#/${PATH}/${CASE_ID}</a></p><p><strong>Please Note:</strong> This email was sent from an unmonitored address. For any inquiries or responses, kindly direct your emails to <a href=\"mailto:tqmc@deloitte.com\">tqmc@deloitte.com</a>. Thank you for your understanding.</p></html>";
  public static final String CASE_MN_ASSIGNMENT_EMAIL_TEMPLATE =
      "<html><p>Hello,</p><p>A ${CASE_TYPE} case of type ${CASE_SUBTYPE} has been ${ACTION_PREFIX}assigned to you for review on the TQMC system. Your review is due on or before ${DUE_DATE}.</p><p>Access the case by navigating to <a href=\"${URL}/#/${PATH}/${CASE_ID}\">${URL}/#/${PATH}/${CASE_ID}</a></p><p><strong>Please Note:</strong> This email was sent from an unmonitored address. For any inquiries or responses, kindly direct your emails to <a href=\"mailto:tqmc@deloitte.com\">tqmc@deloitte.com</a>. Thank you for your understanding.</p></html>";
  public static final String TQMC_PASSWORD_CHANGE_TEMPLATE =
      "<html><p>Hello,</p><p>The password for your account on the TQMC system has been successfully changed.</p> <p>If you did not do this operation, please reach out to an administrator immediately.</p> <p><strong>Please Note:</strong> This email was sent from an unmonitored address. For any inquiries or responses, kindly direct your emails to <a href=\"mailto:tqmc@deloitte.com\">tqmc@deloitte.com</a>. Thank you for your understanding.</p></html>";
  public static final String PWD_ACCOUNT_CREATION_MESSAGE_TEMPLATE =
      "<html><p>Hello,</p><p>An account has been created for you on the TQMC system.</p> <ul><li>Username: <strong>${USERNAME}</strong></li><li>Password: ${CRED}</li>"
          + "<li>You will need to setup your OTP token on <a href=\"https://mfa.medqualitymonitor.com/selfservice/login\">https://mfa.medqualitymonitor.com/selfservice/login</a> before accessing the application at <a href=\"${URL}/#/login\">${URL}/#/login</a></li>"
          + "<li>Access the TQMC system by navigating to <a href=\"${URL}\">${URL}</a></li></ul>"
          + "${ATTACHMENT_MESSAGE}"
          + "<p>After logging in, click on the \"Need Help?\" button to access the user guide relevant for your role. Ex: If you are a medical professional providing a peer review on a SOC or MN case, download the document for \"Peer Reviewer User Guide.\"</p>"
          + "<p><strong>Please Note:</strong> This email was sent from an unmonitored address. For any inquiries or responses, kindly direct your emails to <a href=\"mailto:tqmc@deloitte.com\">tqmc@deloitte.com</a>. Thank you for your understanding.</p></html>";
  public static final String CAC_ACCOUNT_CREATION_MESSAGE_TEMPLATE =
      "<html><p>Hello,</p><p>An account has been created for you on the TQMC system.</p> <p>Please use your CAC to access the TQMC system by navigating to <a href=\"${URL}\">${URL}</a></p><p><strong>Please Note:</strong> This email was sent from an unmonitored address. For any inquiries or responses, kindly direct your emails to <a href=\"mailto:tqmc@deloitte.com\">tqmc@deloitte.com</a>. Thank you for your understanding.</p></html>";
  public static final String MANUAL_LINOTP_RESET_TEMPLATE =
      "<html><p>Hello,</p><p>An account has been created for you on the TQMC system. Please follow the steps below to access your account.</p><h4>Password Creation:</h4><ul><li>Navigate to <a href=\"${URL}/#/login\">${URL}/#/login</a></li><li>Click on &ldquo;Forgot Password&rdquo;</li><li>Enter your email address: <strong>${USERNAME}</strong></li><li>Click on &ldquo;Request Password Reset&rdquo;</li><li>You will be emailed a link to a page allowing you to create a password</li><li>Create a new password following the instructions on the page</li><li>Please keep your password in your records</li></ul><h4>OTP Token Creation:</h4><ul><li>After creating your password, you will need to create an OTP token</li><li>Please follow the instructions in the attached document, with the following exception: the password you will use to login is the password you created during the above <strong>Password Creation</strong> section</li></ul><h4>Account Login:</h4><ul><li>After creating your OTP token, you will be able to login to the TQMC system</li><li>Navigate to <a href=\"${URL}/#/login\">${URL}/#/login</a></li><li>Enter your username: <strong>${USERNAME}</strong></li><li>Enter the password that you created during the above <strong>Password Creation</strong> section</li><li>Click &ldquo;Submit&rdquo;</li><li>Enter the OTP code shown in your Authenticator app</li><li>Click &ldquo;Submit&rdquo;</li></ul><p>After logging in, click on the \"Need Help?\" button to access the user guide for completing SOC peer reviews.</p><p><strong>Please Note:</strong> This email was sent from an unmonitored address. For any inquiries or responses, please contact <a href=\"mailto:tevanburen@deloitte.com\">Thomas Van Buren</a> and <a href=\"mailto:tlokeshrao@deloitte.com\">Tejas Lokeshrao</a>, who are also copied on this email. Thank you for your understanding.</p>${ATTACHMENT_MESSAGE}</html>";
  public static final String REMINDER_EMAIL_SUBJECT =
      "TQMC - ${NUM_CASES} open case${S} ${NUM_CASES_SOON}";
  public static final String REMINDER_EMAIL_TEMPLATE =
      "<html>"
          + "<p>Below is an automated summary of all your open cases as of <strong>${TODAY_DATE}</strong>.</p>"
          + "<table border=\"1\" cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse:collapse;\">\r\n"
          + "<thead> <tr> <th>Case ID</th> <th>Type of review</th> <th>Link</th> <th>Due date</th> </tr> </thead>"
          + "<tbody>${CASE_LIST}</tbody>"
          + "</table>"
          + "<br>"
          + "<p>Login to access all cases here: "
          + "<a href=\"${BASE_LINK}/#/login\">${BASE_LINK}/#/login</a>"
          + "</p>"
          + "<p><strong>Please Note:</strong> This email was sent from an unmonitored address. "
          + "For any inquiries, email <a href=\"mailto:tqmc@deloitte.com\">tqmc@deloitte.com</a>."
          + "</p>"
          + "</html>";

  public static final String REMINDER_EMAIL_CASE =
      "<tr><td>${ALIAS_RECORD_ID}</td> <td>${CASE_LABEL}</td>"
          + "<td><a href=\"${BASE_LINK}/#/${CASE_SUPER_TYPE}/${CASE_TYPE}/${CASE_URL_COMPONENT}\">View</a></td>"
          + "<td>${DUE_DATE} ${WARNING_ICON}</td> </tr>";

  // Padding default length
  public static final int PADDING_LENGTH = 6;

  public static final String[] INVALID_EXCEL_CHARACTERS = {":", "/", "\\", "?", "*", "[", "]"};
  public static final int MAX_EXCEL_COLUMN_WIDTH = 255 * 256;
  public static final String[] MONTH_WORDS = {
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

  public static final String NO_ADVERSE_EVENT_ID = "41";
  public static final String NO_TRIGGER_FOUND_ID = "58";
  public static final int MAX_ERROR_LINES = 10000;
  public static final String ERROR_FILE_NAME = "errors.out";
  public static final String ERROR_SEPARATOR = "\n>----------------------------<\n";
}
