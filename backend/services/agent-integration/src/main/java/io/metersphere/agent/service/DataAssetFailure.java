package io.metersphere.agent.service;

/** Safe data-only failure with the exact diagnostic trace returned by the native transport. */
public final class DataAssetFailure extends BusinessGovernanceException {
  private final String traceId;
  private final int httpStatus;

  public DataAssetFailure(DataAssetErrors.Safe safe, String traceId, Throwable cause) {
    super(safe.code(), safe.message(), cause);
    this.traceId = traceId;
    this.httpStatus = safe.status();
  }

  public String traceId() {
    return traceId;
  }

  public int httpStatus() {
    return httpStatus;
  }
}
