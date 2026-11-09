package io.metersphere.functional.handoff;

import java.util.List;

/** Whitelisted inspection DTO. No destination, credential, response text or other-plan facts. */
public record HandoffInspection(TimeEvidence timeEvidence, SourceVersion sourceVersion, int functionalCount,
                                String frozenEventId, List<String> pauseReasons) {
    public HandoffInspection { pauseReasons=List.copyOf(pauseReasons); }
    public record TimeEvidence(String reference,String revision,String occurredAt) { }
    public record SourceVersion(String revision,String memberVersion,String ruleRevision,String snapshotHash) { }
    public record Audit(String id,String actor,String action,long revision,long time,String scope) { }
}
