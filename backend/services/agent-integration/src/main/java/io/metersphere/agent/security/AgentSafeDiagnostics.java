package io.metersphere.agent.security;

import java.sql.SQLException;
import org.slf4j.Logger;

/** Whitelisted diagnostics only: never log a Throwable, SQL text, payload or raw message. */
public final class AgentSafeDiagnostics {
  private AgentSafeDiagnostics() {}

  public static void failure(
      Logger logger, String event, String stage, String trace, String code, Throwable error) {
    String state = "NONE", root = "UNKNOWN";
    int databaseCode = 0;
    Throwable cause = error;
    for (int i = 0; i < 12 && cause != null; i++, cause = cause.getCause()) {
      root = cause.getClass().getSimpleName().replaceAll("[^A-Za-z0-9_$]", "");
      if (cause instanceof SQLException sql) {
        String value = sql.getSQLState();
        state = value != null && value.matches("[A-Z0-9]{5}") ? value : "UNKNOWN";
        databaseCode = sql.getErrorCode();
      }
    }
    if (error instanceof io.metersphere.agent.service.BusinessGovernanceException safe
        && safe.rootType() != null) root = safe.rootType();
    logger.warn(
        "{} traceId={} stage={} category={} rootType={} sqlState={} databaseCode={} time={}",
        event,
        trace,
        stage,
        code,
        root,
        state,
        databaseCode,
        System.currentTimeMillis());
  }
}
