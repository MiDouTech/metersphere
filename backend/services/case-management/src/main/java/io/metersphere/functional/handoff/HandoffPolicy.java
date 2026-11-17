package io.metersphere.functional.handoff;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Environment-independent rules. A completed review is not necessarily a passed review. */
public final class HandoffPolicy {
    private HandoffPolicy() { }

    public record Member(long epoch, String status) { }
    public record Snapshot(String planState, String reviewState, boolean activeRelation,
                           boolean sourceAuthorized, String generation, String frozenGeneration,
                           long sourceVersion, long appliedVersion, long frozenVersion,
                           boolean pendingConfirmation, Map<String, Long> source,
                           Map<String, Member> current, Map<String, Member> frozen,
                           String opinionFingerprint, String frozenOpinionFingerprint) {
        public Snapshot {
            source = Collections.unmodifiableMap(new TreeMap<>(source));
            current = Collections.unmodifiableMap(new TreeMap<>(current));
            frozen = Collections.unmodifiableMap(new TreeMap<>(frozen));
        }
    }
    public record Eligibility(boolean eligible, String reason, int functionalCount) { }
    /** Execution completion and service availability do not rewrite a previously handed-off functional scope. */
    public static boolean sameScope(Snapshot a,Snapshot b){
        boolean reviewStable=Objects.equals(a.reviewState(),b.reviewState())||Set.of("COMPLETED","ARCHIVED").contains(Objects.toString(a.reviewState(),""))&&Set.of("COMPLETED","ARCHIVED").contains(Objects.toString(b.reviewState(),""));
        return reviewStable&&a.activeRelation()==b.activeRelation()&&Objects.equals(a.generation(),b.generation())
                &&a.sourceVersion()==b.sourceVersion()&&a.source().equals(b.source())&&a.current().equals(b.current())
                &&Objects.equals(a.opinionFingerprint(),b.opinionFingerprint())&&a.pendingConfirmation()==b.pendingConfirmation();
    }
    public static Eligibility evaluate(Snapshot s) {
        String reason = null;
        if (!Set.of("PREPARED", "UNDERWAY").contains(Objects.toString(s.planState(), ""))) reason = "PLAN_NOT_EXECUTABLE";
        else if (!s.activeRelation()) reason = "RELATION_UNAVAILABLE";
        else if (!s.sourceAuthorized()) reason = "SOURCE_AUTHORIZATION_REQUIRED";
        else if (!"COMPLETED".equals(s.reviewState())) reason = "REVIEW_NOT_COMPLETED";
        else if (s.source().isEmpty()) reason = "EMPTY_FUNCTIONAL_SCOPE";
        else if (s.pendingConfirmation() || s.sourceVersion() != s.appliedVersion()) reason = "SCOPE_NOT_CONVERGED";
        else if (s.generation() == null || !s.generation().equals(s.frozenGeneration())
                || s.frozenVersion() != s.sourceVersion()) reason = "FROZEN_SCOPE_CHANGED";
        else if (!s.source().keySet().equals(s.current().keySet()) || !s.current().equals(s.frozen())) reason = "FROZEN_MEMBERS_CHANGED";
        else if (s.source().entrySet().stream().anyMatch(e -> s.current().get(e.getKey()).epoch() != e.getValue())) reason = "MEMBERSHIP_EPOCH_CHANGED";
        else if (s.current().values().stream().anyMatch(m -> !"PASS".equals(m.status()))) reason = "NOT_ALL_PASSED";
        else if (s.opinionFingerprint() == null || !s.opinionFingerprint().equals(s.frozenOpinionFingerprint())) reason = "OPINIONS_CHANGED";
        return new Eligibility(reason == null, reason == null ? "ELIGIBLE" : reason, s.source().size());
    }

    /** Length framing prevents ambiguous tuples; target/review/generation are deliberately absent. */
    public static String businessKey(String environment, String project, String plan) {
        var framed = new StringBuilder("msp-handoff-v1:");
        for (String value : List.of(environment, project, plan)) {
            if (value.isBlank()) throw new IllegalArgumentException("来源标识不能为空");
            framed.append(value.getBytes(StandardCharsets.UTF_8).length).append(':').append(value);
        }
        return hash(framed.toString());
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /** User-controlled text cannot create Markdown links/mentions, HTML or multiline headings. */
    public static String plain(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\p{Cc}\\p{Cf}]", " ").replaceAll("[\\[\\]<>@`*_~#\\\\]", " ").strip();
    }
    public static String title(String number, String name, int maximumCodePoints) {
        String prefix = "[MSP功能测试] " + plain(number) + " ";
        int available = maximumCodePoints - prefix.codePointCount(0, prefix.length());
        if (available < 1) throw new IllegalArgumentException("目标标题上限不足以保留计划编号");
        String text = plain(name);
        int size = Math.min(available, text.codePointCount(0, text.length()));
        return prefix + text.substring(0, text.offsetByCodePoints(0, size));
    }
}
