package io.metersphere.functional.event;

import io.metersphere.functional.domain.FunctionalCase;

/** Publishes a normal (non AI-specific) functional-case content change to the asset catalog. */
public record TestAssetFunctionalCaseChangedEvent(FunctionalCase functionalCase,
                                                   String contentSnapshot,
                                                   String userId) {
}
