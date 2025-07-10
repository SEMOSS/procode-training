package tqmc.reactors.qu.dp;

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

public class GetDpTemplatesReactor extends AbstractTQMCReactor {

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {

    if (!(hasProductPermission(TQMCConstants.DP))) {
      throw new TQMCException(ErrorCode.FORBIDDEN);
    }

    Map<String, Map<String, Object>> output = new LinkedHashMap<>();
    Map<String, Map<String, Map<String, Object>>> outerResponseMap = new LinkedHashMap<>();

    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT dqt."
                + TQMCConstants.DP_QUESTION_TEMPLATE_ID
                + ", dqt."
                + TQMCConstants.QUESTION
                + ", dqt."
                + TQMCConstants.DP_QUESTION_TITLE
                + ", dqt."
                + TQMCConstants.DP_QUESTION_INFO
                + ", drt."
                + TQMCConstants.DP_RESPONSE_TEMPLATE_ID
                + ", drt."
                + TQMCConstants.RESPONSE
                + " FROM "
                + TQMCConstants.TABLE_DP_QUESTION_TEMPLATE
                + " dqt INNER JOIN "
                + TQMCConstants.TABLE_DP_RESPONSE_TEMPLATE
                + " drt ON dqt."
                + TQMCConstants.DP_QUESTION_TEMPLATE_ID
                + " = drt."
                + TQMCConstants.DP_RESPONSE_QUESTION_TEMPLATE_ID
                + " WHERE dqt."
                + TQMCConstants.DP_QUESTION_IS_CURRENT
                + " = 1 AND drt."
                + TQMCConstants.DP_RESPONSE_IS_CURRENT
                + " = 1 ORDER BY dqt."
                + TQMCConstants.DP_QUESTION_TEMPLATE_ID
                + ", drt."
                + TQMCConstants.DP_RESPONSE_TEMPLATE_ID
                + ";")) {
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {

        String questionTemplateId = rs.getString(TQMCConstants.DP_QUESTION_TEMPLATE_ID);
        String question = rs.getString(TQMCConstants.QUESTION);
        String title = rs.getString(TQMCConstants.DP_QUESTION_TITLE);
        String info = rs.getString(TQMCConstants.DP_QUESTION_INFO);
        String responseTemplateId = rs.getString(TQMCConstants.DP_RESPONSE_TEMPLATE_ID);
        String response = rs.getString(TQMCConstants.RESPONSE);

        if (!output.containsKey(questionTemplateId)) {
          Map<String, Object> innerMap = new HashMap<>();
          innerMap.put("question_template_id", questionTemplateId);
          innerMap.put("question", question);
          innerMap.put("title", title);
          innerMap.put("info", info);
          output.put(questionTemplateId, innerMap);
        }
        if (!outerResponseMap.containsKey(questionTemplateId)) {
          Map<String, Map<String, Object>> responseMap = new LinkedHashMap<>();
          outerResponseMap.put(questionTemplateId, responseMap);
        }
        if (!outerResponseMap.get(questionTemplateId).containsKey(responseTemplateId)) {
          Map<String, Object> innerResponseMap = new HashMap<>();
          outerResponseMap.get(questionTemplateId).put(responseTemplateId, innerResponseMap);
        }
        outerResponseMap.get(questionTemplateId).get(responseTemplateId).put("display", response);
        outerResponseMap
            .get(questionTemplateId)
            .get(responseTemplateId)
            .put("value", responseTemplateId);
      }

      for (String key : output.keySet()) {
        output.get(key).put("responses", outerResponseMap.get(key));
      }
    }

    return new NounMetadata(output, PixelDataType.MAP);
  }
}
