package io.metersphere.agent.service;

import java.util.*;

/** JDBC drivers may fold aliases; expose one stable JSON contract regardless of driver casing. */
final class BusinessAssetRows {
  private BusinessAssetRows() {}

  static List<Map<String, Object>> canonical(List<Map<String, Object>> rows, String... fields) {
    return rows.stream()
        .map(
            row -> {
              Map<String, Object> result = new LinkedHashMap<>();
              for (String field : fields) result.put(field, row.get(field));
              return result;
            })
        .toList();
  }
}
