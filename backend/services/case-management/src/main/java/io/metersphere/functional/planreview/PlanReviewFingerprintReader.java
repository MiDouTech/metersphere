package io.metersphere.functional.planreview;

import io.metersphere.sdk.util.JSON;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Preserves both historical wire formats; callers already own the project/source locks. */
public final class PlanReviewFingerprintReader {
    public enum ReadMode { LOCKED, CURRENT }
    public enum Format { COMPLETION_V1, APPLICATION_V1 }
    private final JdbcTemplate jdbc;

    public PlanReviewFingerprintReader(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public String read(String review, ReadMode mode, Format format) {
        String lock = mode == ReadMode.LOCKED ? " FOR UPDATE" : "";
        // Do not rename body, re-encode BLOBs or reorder columns: persisted v1 hashes depend on them.
        String order = format == Format.APPLICATION_V1 ? "case_id" : "id";
        String content = format == Format.APPLICATION_V1 ? "content body" : "content";
        var members = jdbc.queryForList("SELECT id,case_id,status,update_time FROM case_review_functional_case WHERE review_id=? ORDER BY " + order + lock, review);
        var opinions = jdbc.queryForList("SELECT id,case_id,status,deleted,abandoned," + content + " FROM case_review_history WHERE review_id=? ORDER BY id" + lock, review);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JSON.toJSONString(List.of(members, opinions)).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Only an exact current-data match to a known historical algorithm can validate an old snapshot. */
    public String currentForSnapshot(String review, String frozenFingerprint) {
        String current = read(review, ReadMode.CURRENT, Format.COMPLETION_V1);
        if (current.equals(frozenFingerprint)) return current;
        String legacy = read(review, ReadMode.CURRENT, Format.APPLICATION_V1);
        return legacy.equals(frozenFingerprint) ? legacy : current;
    }
}
