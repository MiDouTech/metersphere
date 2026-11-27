package io.metersphere.agent.service;

/** Shared enforced upload/parsed-content limits published to the browser. */
public final class DataAssetLimits {
  public static final int MAX_BYTES = 2_000_000;
  public static final int MAX_ROWS = 10_000;

  private DataAssetLimits() {}
}
