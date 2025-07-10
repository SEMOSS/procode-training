package tqmc.reactors.qu.mcsc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.TQMCConstants;

public class GetMcscTemplatesReactor extends AbstractTQMCReactor {

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    if (!(hasProductPermission(TQMCConstants.MCSC))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    String responseTemplateQuery =
        "SELECT mrt.QUESTION_TEMPLATE_ID, mqt.QUESTION, mrt.RESPONSE_TEMPLATE_ID, mrt.RESPONSE, mqt.INFO FROM MCSC_RESPONSE_TEMPLATE mrt INNER JOIN MCSC_QUESTION_TEMPLATE mqt ON mrt.QUESTION_TEMPLATE_ID = mqt.QUESTION_TEMPLATE_ID WHERE mrt.IS_CURRENT = 1";
    String triggerQuery =
        "SELECT gtt.BUCKET, gtt.NAME, gtt.TRIGGER_TEMPLATE_ID FROM GTT_TRIGGER_TEMPLATE gtt WHERE gtt.IS_CURRENT = 1";
    String harmCategoryQuery =
        "SELECT ghct.HARM_CATEGORY_TEMPLATE_ID, ghct.NAME FROM GTT_HARM_CATEGORY_TEMPLATE ghct WHERE ghct.IS_CURRENT = 1";

    Map<String, Map<String, Map<String, Object>>> output = new HashMap<>();
    output.put("utilization_templates", new LinkedHashMap<>());
    output.put("triggers", new LinkedHashMap<>());
    output.put("harm_categories", new LinkedHashMap<>());

    try (PreparedStatement responsePs = con.prepareStatement(responseTemplateQuery);
        PreparedStatement triggerPs = con.prepareStatement(triggerQuery);
        PreparedStatement harmPs = con.prepareStatement(harmCategoryQuery)) {

      // Process response templates
      try (ResultSet rs = responsePs.executeQuery()) {
        Map<String, Map<String, Object>> utilizationTemplates = output.get("utilization_templates");
        while (rs.next()) {
          String questionTemplateId = rs.getString("QUESTION_TEMPLATE_ID");
          String question = rs.getString("QUESTION");
          String responseTemplateId = rs.getString("RESPONSE_TEMPLATE_ID");
          String response = rs.getString("RESPONSE");
          String info = rs.getString("INFO");

          utilizationTemplates.putIfAbsent(questionTemplateId, new LinkedHashMap<>());
          Map<String, Object> questionData = utilizationTemplates.get(questionTemplateId);
          questionData.putIfAbsent("question_template_id", questionTemplateId);
          questionData.putIfAbsent("question", question);
          questionData.putIfAbsent("info", info);
          questionData.putIfAbsent("responses", new LinkedHashMap<>());

          @SuppressWarnings("unchecked")
          Map<String, Object> responses = (Map<String, Object>) questionData.get("responses");
          Map<String, String> responseData = new HashMap<>();
          responseData.put("value", responseTemplateId);
          responseData.put("display", response);
          responses.put(responseTemplateId, responseData);
        }
      }

      // Process triggers
      try (ResultSet rs = triggerPs.executeQuery()) {
        Map<String, Map<String, Object>> triggers = output.get("triggers");
        while (rs.next()) {
          String triggerTemplateId = rs.getString("TRIGGER_TEMPLATE_ID");
          String name = rs.getString("NAME");
          String bucket = rs.getString("BUCKET");

          Map<String, Object> triggerData = new HashMap<>();
          triggerData.put("value", triggerTemplateId);
          triggerData.put("display", name);
          triggerData.put("category", bucket);
          triggers.put(triggerTemplateId, triggerData);
        }
      }

      // Process harm categories
      try (ResultSet rs = harmPs.executeQuery()) {
        Map<String, Map<String, Object>> harmCategories = output.get("harm_categories");
        while (rs.next()) {
          String harmCategoryTemplateId = rs.getString("HARM_CATEGORY_TEMPLATE_ID");
          String name = rs.getString("NAME");

          Map<String, Object> harmCategoryData = new HashMap<>();
          harmCategoryData.put("value", harmCategoryTemplateId);
          harmCategoryData.put("display", name);
          harmCategories.put(harmCategoryTemplateId, harmCategoryData);
        }
      }
    }

    return new NounMetadata(output, PixelDataType.MAP);
  }
}
