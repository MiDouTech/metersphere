package io.metersphere.functional.event;

import io.metersphere.functional.domain.AiSourceDocument;

/**
 * Domain event emitted after a business document becomes available for test-asset publication.
 */
public record TestAssetDocumentPublishedEvent(AiSourceDocument document, String contentSnapshot) {
}
