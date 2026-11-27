package io.metersphere.agent.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Asset-local bounded JSON reader; other application readers keep their defaults. */
public final class DataAssetJson {
  private DataAssetJson() {}
  public static ObjectMapper mapper() {
    return new ObjectMapper(JsonFactory.builder().streamReadConstraints(
        StreamReadConstraints.builder().maxStringLength(DataAssetLimits.MAX_CONTENT_BYTES).build()).build());
  }
}
