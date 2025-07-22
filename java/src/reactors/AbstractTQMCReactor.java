package reactors;

import java.sql.Connection;
import java.sql.SQLException;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import prerna.auth.User;
import prerna.engine.api.IRDBMSEngine;
import prerna.reactor.AbstractReactor;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.PixelOperationType;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.Utility;
import domain.base.ErrorCode;
import domain.base.TQMCException;
import util.TQMCProperties;

public abstract class AbstractTQMCReactor extends AbstractReactor {

  private static final Logger LOGGER = LogManager.getLogger(AbstractTQMCReactor.class);

  protected User user;

  protected String projectId;
  protected TQMCProperties tqmcProperties;

  protected String engineId;

  protected NounMetadata result = null;

  @Override
  public NounMetadata execute() {
    try {
      preExecute();

      IRDBMSEngine engine = (IRDBMSEngine) Utility.getEngine(engineId);
      if (engine == null) {
        throw new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, "Unable to find database");
      }
      Connection con = null;
      try {
        con = engine.makeConnection();

        if (isReadOnly()) {
          result = doExecute(con);
        } else {
          boolean isAutoCommit = con.getAutoCommit();
          con.setAutoCommit(false);
          try {
            result = doExecute(con);
            con.commit();
          } catch (Exception e) {
            con.rollback();
            TQMCException ex = null;
            if (e instanceof TQMCException) {
              ex = (TQMCException) e;
            } else {
              ex = new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
            }
            throw ex;
          } finally {
            con.setAutoCommit(isAutoCommit);
          }
        }
      } finally {
        if (engine.isConnectionPooling() && con != null) {
          con.close();
        }
      }
      return result;
    } catch (Exception e) {
      TQMCException ex = null;
      if (e instanceof TQMCException) {
        ex = (TQMCException) e;
      } else {
        ex = new TQMCException(ErrorCode.INTERNAL_SERVER_ERROR, e);
      }
      LOGGER.error(String.format("Reactor %s threw an error", this.getClass().getSimpleName()), e);

      NounMetadata result =
          new NounMetadata(ex.getAsMap(), PixelDataType.MAP, PixelOperationType.ERROR);
      if (tqmcProperties != null && tqmcProperties.getDebuggingEnabled()) {
        result.addAdditionalReturn(
            new NounMetadata(
                ExceptionUtils.getFullStackTrace(e),
                PixelDataType.CONST_STRING,
                PixelOperationType.ERROR));
      }
      return new NounMetadata(ex.getAsMap(), PixelDataType.MAP, PixelOperationType.ERROR);
    }
  }

  protected void preExecute() {
    projectId = this.insight.getContextProjectId();
    if (projectId == null) {
      projectId = this.insight.getProjectId();
    }

    tqmcProperties = TQMCProperties.getInstance(projectId);
    engineId = tqmcProperties.getEngineId();

    user = this.insight.getUser();

    organizeKeys();
  }

  protected abstract NounMetadata doExecute(Connection con) throws SQLException;

  protected boolean isReadOnly() {
    return false;
  }
}
