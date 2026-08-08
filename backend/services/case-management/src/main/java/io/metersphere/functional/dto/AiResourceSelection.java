package io.metersphere.functional.dto;

public record AiResourceSelection(String resourceType, String resourceId, String modelSourceId,
                                  String agentConnectionId, String provider, boolean supportsTools) {
}
