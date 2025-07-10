package tqmc.reactors.base;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.PixelOperationType;
import prerna.sablecc2.om.ReactorKeysEnum;
import prerna.sablecc2.om.execptions.SemossPixelException;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.AssetUtility;
import prerna.util.Utility;
import tqmc.reactors.AbstractTQMCReactor;

public class GetTqmcVersionReactor extends AbstractTQMCReactor {

  private static final String VER_FILE_NAME = "ver.txt";
  private static final String VERSION_KEY = "version";
  private static final String DATETIME_KEY = "datetime";

  private static Map<String, String> VERSION_MAP;

  public GetTqmcVersionReactor() {
    this.keysToGet = new String[] {ReactorKeysEnum.RELOAD.getKey()};
  }

  @Override
  protected boolean isReadOnly() {
    return true;
  }

  @Override
  public NounMetadata doExecute(Connection con) throws SQLException {
    organizeKeys();
    String reloadStr = this.keyValue.get(this.keysToGet[0]);
    boolean reload = Boolean.parseBoolean(reloadStr);
    return new NounMetadata(getVersionMap(reload), PixelDataType.MAP, PixelOperationType.VERSION);
  }

  private Map<String, String> getVersionMap(boolean reload) {
    if (reload || VERSION_MAP == null) {
      synchronized (GetTqmcVersionReactor.class) {
        String verPath = AssetUtility.getProjectAssetsFolder(projectId) + "/" + VER_FILE_NAME;

        Properties props = Utility.loadProperties(verPath);
        if (props == null) {
          NounMetadata noun =
              new NounMetadata(
                  "Failed to parse the version",
                  PixelDataType.CONST_STRING,
                  PixelOperationType.ERROR);
          SemossPixelException err = new SemossPixelException(noun);
          err.setContinueThreadOfExecution(false);
          throw err;
        }

        VERSION_MAP = new HashMap<>();
        VERSION_MAP.put(VERSION_KEY, props.getProperty(VERSION_KEY));
        VERSION_MAP.put(DATETIME_KEY, props.getProperty(DATETIME_KEY));
      }
    }
    return VERSION_MAP;
  }
}
