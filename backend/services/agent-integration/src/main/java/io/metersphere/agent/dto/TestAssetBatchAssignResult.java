package io.metersphere.agent.dto;

public record TestAssetBatchAssignResult(String projectId, String assetType, String assetId,
                                         boolean success, String message) {
}
