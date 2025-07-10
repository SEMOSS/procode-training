package tqmc.reactors.gtt;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import prerna.sablecc2.om.NounStore;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.Filter;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;
import tqmc.util.TQMCHelper;

public class BulkReassignGttCasesReactor extends AbstractTQMCReactor {

  private static final String PHYSICIAN = "physician";
  private static final String ABSTRACTION = "abstraction";
  private static final String CASE_TYPE = "case_type";
  private static final String DISCHARGE_DATE = "discharge_date_query";

  public BulkReassignGttCasesReactor() {
    this.keysToGet = new String[] {"assigneeOne", TQMCConstants.FILTER, "assigneeTwo"};
    this.keyRequired = new int[] {1, 1, 0};
  }

  /**
   * This reactor consists of the entire workflow to bulk reassign GTT cases. We first get the
   * requested cases by case type, assignee information, DMIS id, and other allowed filters. Then,
   * we systematically call either record-wise or case-wise reassignment based on the number of
   * reassignees passed in to reassign all of the cases possible.
   */
  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    Payload payload = TQMCHelper.getPayloadObject(store, keysToGet, Payload.class);

    /** Making sure that the filters passed in are valid */
    String caseType = checkFilters(payload);

    /**
     * Call the GetGttCases reactor and return a map of record and case information. For each record
     * we get a list of cases, and each case contains info about its case_id, assigned user id, and
     * paired case user id (i.e. the person assigned to the other case on the record)
     */
    GetGttCasesReactor getGttCasesReactor = new GetGttCasesReactor();
    getGttCasesReactor.setInsight(this.insight);
    NounStore nsGetGttCases = new NounStore("nsGetGttCases");
    nsGetGttCases.makeNoun(TQMCConstants.FILTER).add(payload.getFilter(), PixelDataType.MAP);
    getGttCasesReactor.setNounStore(nsGetGttCases);

    Map<String, List<Map<String, String>>> casesMap = null;

    try {
      NounMetadata getCasesOutput = getGttCasesReactor.execute();
      Object value = getCasesOutput.getValue();

      /**
       * Since the getCasesOutput reactor catches the error and returns it as noun metadata, we have
       * to check the noun metadata fields to make sure it's not a tqmc error, otherwise we would
       * have issues with casting. If it is an error, we need to throw and catch it and send it back
       * up the call stack.
       */
      TQMCHelper.checkIfTqmcException(value);

      casesMap = (Map<String, List<Map<String, String>>>) value;

      if (casesMap == null || casesMap.isEmpty()) {
        throw new TQMCException(ErrorCode.BAD_REQUEST, "No cases meet these parameters");
      }

    } catch (TQMCException te) {
      throw te;
    } catch (ClassCastException e) {
      throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, "Unexpected data format", e);
    } catch (Exception e) {
      throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
    }

    NounMetadata output = null;

    Set<String> caseIdsToReassign;
    AbstractTQMCReactor bulkAssignReactor;
    NounStore nsBulkAssign = new NounStore("nsBulkAssign");

    if (payload.getAssigneeTwo() != null) {

      /**
       * * In this situation, if we get instances of record_ids with more than one case associated,
       * then add those records to the list to pass
       *
       * <p>If we get instances of record ids with singleton cases, we need to check whether
       * paired_abstraction_user_id is equal to one of the two reassignees
       *
       * <p>If neither of the above, we need to split reassign the remaining cases by user
       */

      /**
       * Firstly, we are getting the map of record ids to case ids and filtering out records with
       * singleton cases that are not already assigned to one of the users
       */
      Map<String, Set<String>> recordIdToCaseIds =
          casesMap.entrySet().stream()
              .filter(
                  entry -> {
                    List<Map<String, String>> cases = entry.getValue();
                    return cases.size() > 1
                        || cases.stream()
                            .anyMatch(
                                parameters -> {
                                  String pairedAbstractionUserId =
                                      parameters.get("paired_abstraction_user_id");
                                  return payload.getAssigneeOne().equals(pairedAbstractionUserId)
                                      || payload.getAssigneeTwo().equals(pairedAbstractionUserId);
                                });
                  })
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey, // Collecting record_ids
                      entry ->
                          entry.getValue().stream()
                              .map(parameters -> parameters.get("case_id"))
                              .collect(Collectors.toSet()) // Collecting associated case_ids
                      ));

      /** Set of valid record ids that we will bulk reassign both of the given users to */
      Set<String> recordIdsToReassign = recordIdToCaseIds.keySet(); // Set of record_ids

      /**
       * Now we want to get the remaining set of case ids that don't match the above criteria that
       * we're going to reassign individually to each user. To this end, we will collect all of the
       * caseids (using flatmap and collecting in a set caseIdsToExclude), then take the set
       * difference of allCaseIds and caseIdsToExclude to get caseIdsToReassign.
       */
      Set<String> caseIdsToExclude =
          recordIdToCaseIds.values().stream().flatMap(Set::stream).collect(Collectors.toSet());

      Set<String> allCaseIds =
          casesMap.values().stream()
              .flatMap(List::stream)
              .map(parameters -> parameters.get("case_id"))
              .collect(Collectors.toSet());

      caseIdsToReassign = new HashSet<>(allCaseIds);
      caseIdsToReassign.removeAll(caseIdsToExclude); // Performing set difference

      /**
       * Now `recordIdsToReassign` contains the record_ids to reassigned to each user individually
       * `caseIdsToExclude` contains the case_ids that are not to be reassigned to each user
       * individually
       */
      bulkAssignReactor = new BulkReassignGttAbstractionCasesByRecordIdReactor();
      nsBulkAssign.makeNoun("recordIds").add(recordIdsToReassign, PixelDataType.MAP);
      nsBulkAssign
          .makeNoun(TQMCConstants.NEW_ASSIGNEE_USER_ID_2)
          .add(payload.getAssigneeTwo(), PixelDataType.CONST_STRING);

      try {

        /**
         * For cases being assigned to each user individually, we will split them 50/50 by each user
         * more or less
         */
        List<String> caseIdsList = new ArrayList<>(caseIdsToReassign);

        int midpoint = caseIdsList.size() / 2;

        Set<String> firstHalf = new HashSet<>(caseIdsList.subList(0, midpoint));
        Set<String> secondHalf = new HashSet<>(caseIdsList.subList(midpoint, caseIdsList.size()));

        List<UserCaseAssignment> userCaseMaps =
            Arrays.asList(
                new UserCaseAssignment(payload.getAssigneeOne(), firstHalf),
                new UserCaseAssignment(payload.getAssigneeTwo(), secondHalf));

        /**
         * Now we'll iterate on each user and their case ids to be assigned, and assign them
         * successfully or catch any errors the assignment reactors pick up
         */
        for (UserCaseAssignment userCase : userCaseMaps) {
          BulkReassignGttCasesByCaseIdReactor indivCaseAssignReactor =
              new BulkReassignGttCasesByCaseIdReactor();
          indivCaseAssignReactor.setInsight(this.insight);
          NounStore indivCasesNs = new NounStore("indivCasesNs");
          indivCasesNs.makeNoun("caseIds").add(userCase.getCaseIds(), PixelDataType.MAP);
          indivCasesNs.makeNoun(TQMCConstants.CASE_TYPE).add(caseType, PixelDataType.CONST_STRING);
          indivCasesNs
              .makeNoun(TQMCConstants.NEW_ASSIGNEE_USER_ID)
              .add(userCase.getUser(), PixelDataType.CONST_STRING);
          indivCaseAssignReactor.setNounStore(indivCasesNs);

          NounMetadata indivOutput = indivCaseAssignReactor.execute();

          TQMCHelper.checkIfTqmcException(indivOutput);
        }

      } catch (TQMCException te) {
        throw te;
      } catch (ClassCastException e) {
        throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, "Unexpected data format", e);
      } catch (Exception e) {
        throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
      }

    } else {
      /**
       * In the case that we're assigning to one user only, we just have to get all the requisite
       * case ids and reassign
       */
      caseIdsToReassign =
          casesMap.values().stream()
              .flatMap(List::stream)
              .map(parameters -> parameters.get("case_id"))
              .collect(Collectors.toSet());
      bulkAssignReactor = new BulkReassignGttCasesByCaseIdReactor();
      nsBulkAssign.makeNoun("caseIds").add(caseIdsToReassign, PixelDataType.MAP);
      nsBulkAssign.makeNoun(TQMCConstants.CASE_TYPE).add(caseType, PixelDataType.CONST_STRING);
    }

    /** Shared logic */
    bulkAssignReactor.setInsight(this.insight);
    nsBulkAssign
        .makeNoun(TQMCConstants.NEW_ASSIGNEE_USER_ID)
        .add(payload.getAssigneeOne(), PixelDataType.CONST_STRING);
    bulkAssignReactor.setNounStore(nsBulkAssign);

    try {
      output = bulkAssignReactor.execute();
    } catch (Exception e) {
      throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
    }

    return output;
  }

  protected String checkFilters(Payload payload) {

    /**
     * * Need to check the following:
     *
     * <p>1. If payload.getFilterList doesn't contain any filters for field "case_type", throw an
     * error
     *
     * <p>2. If there are any operators other than '=' for "discharge_date", and '=' or '!=' for
     * fields "case_type", throw an error
     *
     * <p>3. Check that there's no more than one filter on "case_type", else throw an error
     *
     * <p>4. Check that filters for field "case_type" contains only either "abstraction" or
     * "physician", else throw an error
     *
     * <p>5. If there's a filter for "case_type" = physician, but payload.getAssigneeTwo() is not
     * null, then throw an error
     *
     * <p>6. Save the case_type determined and apply it below in the next todo
     */
    List<Filter> filters = payload.getFilter();
    boolean hasCaseTypeFilter = false;
    String caseType = null;
    boolean abstractionIncluded = false;
    boolean physicianIncluded = false;

    if (filters != null) {
      for (Filter filter : filters) {
        String field = filter.getField();
        String value = filter.getValue();
        Filter.Comparison operator = filter.getOperator();

        if (DISCHARGE_DATE.equals(field)) {
          if (operator != Filter.Comparison.EQUAL) {
            throw new TQMCException(
                ErrorCode.BAD_REQUEST, "Only '=' operator is allowed for discharge date.");
          }
        }

        if (CASE_TYPE.equals(field)) {
          hasCaseTypeFilter = true;

          if (operator != Filter.Comparison.EQUAL && operator != Filter.Comparison.NOT_EQUAL) {
            throw new TQMCException(
                ErrorCode.BAD_REQUEST, "Only '=' or '!=' operators are allowed for case type.");
          }

          if (ABSTRACTION.equals(value)) {
            if (operator == Filter.Comparison.EQUAL) {
              abstractionIncluded = true;
            } else if (operator == Filter.Comparison.NOT_EQUAL) {
              physicianIncluded = true;
            }
          } else if (PHYSICIAN.equals(value)) {
            if (operator == Filter.Comparison.EQUAL) {
              physicianIncluded = true;
            } else if (operator == Filter.Comparison.NOT_EQUAL) {
              abstractionIncluded = true;
            }
          } else {
            throw new TQMCException(ErrorCode.BAD_REQUEST, "Invalid value for case type.");
          }
        }
      }
    }

    if (!hasCaseTypeFilter) {
      throw new TQMCException(ErrorCode.BAD_REQUEST, "Filters for case type are required.");
    }

    /**
     * If you get this error, you can't pass in filters that allow the GetGttCasesReactor to return
     * both abstraction and physician cases, or neither (enforced above technically)
     */
    if (abstractionIncluded == physicianIncluded) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST,
          "Filters must evaluate to select either 'abstraction' or 'physician' cases only.");
    }

    caseType = abstractionIncluded ? ABSTRACTION : PHYSICIAN;

    /** Cannot reassign physician cases to more than one individual, so throws an error */
    if (PHYSICIAN.equals(caseType) && payload.getAssigneeTwo() != null) {
      throw new TQMCException(
          ErrorCode.BAD_REQUEST, "Cannot have a second assignee for 'physician' cases");
    }
    return caseType;
  }

  private static class UserCaseAssignment {
    private String user;
    private Set<String> caseIds;

    public UserCaseAssignment(String user, Set<String> caseIds) {
      this.user = user;
      this.caseIds = caseIds;
    }

    public String getUser() {
      return user;
    }

    public Set<String> getCaseIds() {
      return caseIds;
    }
  }

  public static class Payload {

    private List<Filter> filter;
    private String assigneeOne;
    private String assigneeTwo;

    public List<Filter> getFilter() {
      return filter;
    }

    public String getAssigneeOne() {
      return assigneeOne;
    }

    public String getAssigneeTwo() {
      return assigneeTwo;
    }
  }
}
