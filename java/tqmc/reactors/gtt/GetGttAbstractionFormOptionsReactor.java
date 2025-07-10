package tqmc.reactors.gtt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import tqmc.domain.base.ErrorCode;
import tqmc.domain.base.TQMCException;
import tqmc.reactors.AbstractTQMCReactor;
import tqmc.util.NaturalOrderComparator;
import tqmc.util.TQMCConstants;

// grab all the dropdown options at once
public class GetGttAbstractionFormOptionsReactor extends AbstractTQMCReactor {

  private static final NaturalOrderComparator COMPARATOR = new NaturalOrderComparator();

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  protected NounMetadata doExecute(Connection con) throws SQLException {
    if (!hasProductPermission(TQMCConstants.GTT)) {
      throw new TQMCException(
          ErrorCode.FORBIDDEN, "User is unauthorized to perform this operation");
    }

    Map<String, Set<Entry>> ops = new HashMap<>();
    ops.put("triggers", new TreeSet<>());
    ops.put("adverse_event_types", new TreeSet<>());
    ops.put("levels_of_harm", new TreeSet<>());

    try (PreparedStatement ps =
        con.prepareStatement(
            "SELECT 'triggers' AS KEY, TRIGGER_TEMPLATE_ID AS TEMPLATE_ID, NAME, BUCKET FROM GTT_TRIGGER_TEMPLATE WHERE IS_CURRENT = 1 "
                + "UNION "
                + "SELECT 'adverse_event_types' AS KEY, EVENT_TYPE_TEMPLATE_ID AS TEMPLATE_ID, NAME, BUCKET FROM GTT_EVENT_TYPE_TEMPLATE WHERE IS_CURRENT = 1 "
                + "UNION "
                + "SELECT 'levels_of_harm' AS KEY, HARM_CATEGORY_TEMPLATE_ID AS TEMPLATE_ID, NAME, null FROM GTT_HARM_CATEGORY_TEMPLATE WHERE IS_CURRENT = 1 ")) {
      if (ps.execute()) {
        ResultSet rs = ps.getResultSet();
        while (rs.next()) {

          String key = rs.getString("KEY");
          Entry e = new Entry();
          e.value = rs.getString("TEMPLATE_ID");
          e.display = rs.getString("NAME");
          e.category = rs.getString("BUCKET");
          e.key = key;

          ops.get(key).add(e);
          e.setKey(null);
        }
      }
    }

    return new NounMetadata(ops, PixelDataType.MAP);
  }

  public static class Entry implements Comparable<Entry> {
    private String display;
    private String value;
    private String category;
    private String key;

    public Entry() {}

    public String getDisplay() {
      return display;
    }

    public void setDisplay(String display) {
      this.display = display;
    }

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }

    public String getCategory() {
      return category;
    }

    public void setCategory(String category) {
      this.category = category;
    }

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

    @Override
    public int compareTo(Entry o) {
      // null category first. display assumed not null
      if (category == null) {
        if (o.category == null) {
          return COMPARATOR.compare(display, o.display);
        } else {
          return -1;
        }
      } else {
        if (o.category == null) {
          return -1;
        } else {
          int p1 =
              TQMCConstants.FORM_ORDER_COMPARATOR_MAP
                  .get(key)
                  .getOrDefault(category, Integer.MAX_VALUE);
          int p2 =
              TQMCConstants.FORM_ORDER_COMPARATOR_MAP
                  .get(key)
                  .getOrDefault(o.category, Integer.MAX_VALUE);

          if (p1 == p2) {
            return COMPARATOR.compare(display, o.display);
          } else {
            return Integer.compare(p1, p2);
          }
        }
      }
    }
  }
}
