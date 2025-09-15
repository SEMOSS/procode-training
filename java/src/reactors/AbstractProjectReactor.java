package reactors;

import domain.base.ErrorCode;
import domain.base.ProjectException;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import prerna.auth.User;
import prerna.engine.impl.rdbms.RDBMSNativeEngine;
import prerna.reactor.AbstractReactor;
import prerna.sablecc2.om.GenRowStruct;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.PixelOperationType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.Utility;
import util.ProjectProperties;

public abstract class AbstractProjectReactor extends AbstractReactor {

  private static final Logger LOGGER = LogManager.getLogger(AbstractProjectReactor.class);

  protected User user;
  protected String projectId;
  protected ProjectProperties projectProperties;

  // intialize protected variables you would like your reactors to have access to
  protected String databaseId;
  protected RDBMSNativeEngine database;

  protected NounMetadata result = null;

  @Override
  public NounMetadata execute() {
    try {
      preExecute();
      return doExecute();
    } catch (Exception e) {
      ProjectException ex = null;
      if (e instanceof ProjectException) {
        ex = (ProjectException) e;
      } else {
        ex = new ProjectException(ErrorCode.INTERNAL_SERVER_ERROR, e);
      }
      LOGGER.error(String.format("Reactor %s threw an error", this.getClass().getSimpleName()), e);

      return new NounMetadata(ex.getAsMap(), PixelDataType.MAP, PixelOperationType.ERROR);
    }
  }

  protected void preExecute() {
    projectId = this.insight.getContextProjectId();
    if (projectId == null) {
      projectId = this.insight.getProjectId();
    }

    projectProperties = ProjectProperties.getInstance(projectId);

    // Update protected variables
    databaseId = projectProperties.getDatabaseId();
    if (databaseId != null) {
      database = (RDBMSNativeEngine) Utility.getDatabase(databaseId);
    }

    user = this.insight.getUser();

    organizeKeys();
  }

  protected Map<String, Object> getMap(String paramName) {
    GenRowStruct mapGrs = this.store.getNoun(paramName);
    if (mapGrs != null && !mapGrs.isEmpty()) {
      List<NounMetadata> mapInputs = mapGrs.getNounsOfType(PixelDataType.MAP);
      if (mapInputs != null && !mapInputs.isEmpty()) {
        return (Map<String, Object>) mapInputs.get(0).getValue();
      }
    }
    List<NounMetadata> mapInputs = this.curRow.getNounsOfType(PixelDataType.MAP);
    if (mapInputs != null && !mapInputs.isEmpty()) {
      return (Map<String, Object>) mapInputs.get(0).getValue();
    }
    return null;
  }

  protected abstract NounMetadata doExecute();
}
