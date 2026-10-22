package io.metersphere.functional.service;

import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Objects;
import java.util.UUID;

/** Shares the comment transaction; stores hashes, never a second copy of deleted comment content. */
@Service
public class FunctionalCaseCommentAudit {
    @Resource private JdbcTemplate jdbc;
    public void record(String commentId, String caseId, String userId, String action, long version, String before, String after) {
        jdbc.update("""
                INSERT INTO functional_case_comment_audit(id,comment_id,case_id,operator_id,action,version,before_hash,after_hash,create_time)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), commentId, caseId, userId, action, version,
                DigestUtils.sha256Hex(Objects.toString(before, "")), DigestUtils.sha256Hex(Objects.toString(after, "")), System.currentTimeMillis());
    }
}
