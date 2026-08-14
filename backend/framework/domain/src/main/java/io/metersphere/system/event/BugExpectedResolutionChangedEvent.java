package io.metersphere.system.event;

public record BugExpectedResolutionChangedEvent(String bugId, long resourceVersion) {
}
