package io.metersphere.functional.event;

import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.domain.FunctionalCaseAiDraft;

/**
 * Domain event emitted only after a reviewed AI draft has been persisted as a formal case.
 */
public record TestAssetCasePublishedEvent(FunctionalCaseAiDraft draft,
                                          FunctionalCase functionalCase,
                                          String contentSnapshot,
                                          String userId) {
}
