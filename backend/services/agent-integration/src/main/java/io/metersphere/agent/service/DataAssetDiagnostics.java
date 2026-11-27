package io.metersphere.agent.service;

/**
 * Only whitelisted identifiers and diagnostic categories are logged, never payloads or Throwables.
 */
final class DataAssetDiagnostics {
  private DataAssetDiagnostics() {}

  private record Root(String type, String state, int code) {}

  private static Root inspect(Throwable failure) {
    String type = "UnknownFailure", state = "NONE";
    int code = 0;
    Throwable current = failure;
    for (int depth = 0; current != null && depth < 8; depth++) {
      String name = current.getClass().getSimpleName();
      if (name.matches("[A-Za-z0-9_$]{1,100}")) type = name;
      if (current instanceof java.sql.SQLException sql) {
        String value = sql.getSQLState();
        if (value != null && value.matches("[0-9A-Z]{5}")) state = value;
        code = sql.getErrorCode();
      }
      if (current.getCause() == current) break;
      current = current.getCause();
    }
    return new Root(type, state, code);
  }

  private static String id(String value) {
    return value != null && value.matches("[A-Za-z0-9_-]{1,64}") ? value : "NONE";
  }

  static void failure(Class<?> owner, String event, String job, String stage, Throwable failure) {
    var root = inspect(failure);
    org.slf4j.LoggerFactory.getLogger(owner)
        .warn(
            "event={} jobId={} stage={} rootType={} sqlState={} sqlCode={}",
            event,
            id(job),
            stage,
            root.type(),
            root.state(),
            root.code());
  }

  static void storageFailure(
      Class<?> owner, String event, String asset, String version, String stage, Throwable failure) {
    var root = inspect(failure);
    org.slf4j.LoggerFactory.getLogger(owner)
        .warn(
            "event={} assetId={} versionId={} stage={} rootType={} sqlState={} sqlCode={}",
            event,
            id(asset),
            id(version),
            stage,
            root.type(),
            root.state(),
            root.code());
  }
}
