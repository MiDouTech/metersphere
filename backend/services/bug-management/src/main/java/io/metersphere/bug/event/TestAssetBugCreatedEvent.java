package io.metersphere.bug.event;

import io.metersphere.bug.domain.Bug;

/** Trusted backend signal emitted only after a bug creation transaction succeeds. */
public record TestAssetBugCreatedEvent(Bug bug, String creationSource, String actorId, String sourceReferenceId) {
}
