package examples.reactors;

import examples.domain.base.ErrorCode;
import examples.domain.base.ProjectException;
import examples.util.ProjectProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import prerna.auth.User;
import prerna.engine.impl.rdbms.RDBMSNativeEngine;
import prerna.reactor.AbstractReactor;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.PixelOperationType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.Utility;

public abstract class AbstractAnimalReactor extends AbstractReactor {

  private static final Logger LOGGER = LogManager.getLogger(AbstractAnimalReactor.class);

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

  protected abstract NounMetadata doExecute();
}
