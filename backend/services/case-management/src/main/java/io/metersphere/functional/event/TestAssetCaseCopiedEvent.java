package io.metersphere.functional.event;

public record TestAssetCaseCopiedEvent(String sourceProjectId, String sourceAssetId,
                                       String targetProjectId, String targetAssetId,
                                       String userId) {
}
