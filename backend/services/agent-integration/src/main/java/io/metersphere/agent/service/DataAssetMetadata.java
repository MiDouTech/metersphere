package io.metersphere.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.sdk.exception.MSException;
import java.util.*;

/** Immutable version metadata; historical source classification is never inferred. */
final class DataAssetMetadata {
  private static final ObjectMapper JSON = new ObjectMapper();

  private DataAssetMetadata() {}

  static Map<String, Object> decodeStored(Object value) {
    if (value instanceof java.sql.Clob clob) {
      try {
        return decode(clob.getSubString(1, Math.toIntExact(clob.length())));
      } catch (java.sql.SQLException failure) {
        DataAssetDiagnostics.failure(
            DataAssetMetadata.class,
            "DATA_ASSET_METADATA_DECODE_FAILURE",
            null,
            "metadata-read",
            failure);
        throw new MSException("数据版本元信息暂时无法读取");
      }
    }
    return decode((String) value);
  }

  static String encode(Object name, Object description, Object category, Object format) {
    var value = new LinkedHashMap<String, Object>();
    value.put("name", name);
    value.put("description", description);
    value.put("categoryId", category);
    value.put("format", format);
    try {
      return JSON.writeValueAsString(value);
    } catch (Exception e) {
      DataAssetDiagnostics.failure(
          DataAssetMetadata.class,
          "DATA_ASSET_METADATA_ENCODE_FAILURE",
          null,
          "metadata-encode",
          e);
      throw new MSException("数据版本元信息无效");
    }
  }

  static Map<String, Object> decode(String value) {
    if (value == null) return Map.of();
    try {
      var decoded =
          JSON.readValue(
              value, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
      var result = new LinkedHashMap<String, Object>();
      for (String key : List.of("name", "description", "categoryId", "format"))
        if (decoded.containsKey(key)) result.put(key, decoded.get(key));
      return result;
    } catch (Exception e) {
      DataAssetDiagnostics.failure(
          DataAssetMetadata.class,
          "DATA_ASSET_METADATA_DECODE_FAILURE",
          null,
          "metadata-decode",
          e);
      throw new MSException("数据版本元信息暂时无法读取");
    }
  }
}
