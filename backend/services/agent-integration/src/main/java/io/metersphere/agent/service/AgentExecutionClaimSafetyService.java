package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Recheck revocable resources at claim time without changing the frozen case content. */
@Service
public class AgentExecutionClaimSafetyService {
    private final JdbcTemplate jdbc;
    public AgentExecutionClaimSafetyService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void requireClaimable(AgentExecutionTaskDTO task) {
        require("SELECT COUNT(*) FROM project WHERE id=? AND deleted=0 AND enable=1",
                "QUALITY_CLAIM_PROJECT_UNAVAILABLE", task.getProjectId());
        require("""
                SELECT COUNT(*) FROM user_role_relation rel
                JOIN user_role role ON role.id=rel.role_id AND role.enabled=1
                JOIN project p ON p.id=?
                WHERE rel.user_id=? AND ((role.type='PROJECT' AND rel.source_id=p.id)
                     OR (role.type='ORGANIZATION' AND rel.source_id=p.organization_id))
                """, "QUALITY_CLAIM_ACTOR_REVOKED", task.getProjectId(), task.getCreateUser());
        require("""
                SELECT COUNT(*) FROM ai_execution_case ec JOIN functional_case c ON c.id=ec.case_id
                WHERE ec.task_id=? AND c.project_id=? AND c.deleted=0
                HAVING COUNT(*)=(SELECT COUNT(*) FROM ai_execution_case WHERE task_id=?)
                """, "QUALITY_CLAIM_CASE_UNAVAILABLE", task.getId(), task.getProjectId(), task.getId());
        if (StringUtils.isNotBlank(task.getEnvironmentProfileId())) {
            require("""
                    SELECT COUNT(*) FROM ai_environment_execution_profile
                    WHERE id=? AND project_id=? AND enabled=1 AND environment_type<>'PRODUCTION'
                    """, "QUALITY_CLAIM_ENVIRONMENT_UNAVAILABLE", task.getEnvironmentProfileId(), task.getProjectId());
        }
        if (StringUtils.isNotBlank(task.getCredentialReferenceId())) {
            require("""
                    SELECT COUNT(*) FROM ai_credential_reference c
                    JOIN ai_environment_execution_profile e ON e.id=? AND e.environment_id=c.environment_id
                    WHERE c.id=? AND c.project_id=? AND c.enabled=1 AND c.status='ACTIVE'
                      AND c.last_verify_status='PASSED' AND (c.expires_at IS NULL OR c.expires_at>?)
                    """, "QUALITY_CLAIM_CREDENTIAL_UNAVAILABLE", task.getEnvironmentProfileId(),
                    task.getCredentialReferenceId(), task.getProjectId(), System.currentTimeMillis());
        }
    }

    private void require(String sql, String code, Object... args) {
        var counts = jdbc.query(sql, (rs, row) -> rs.getLong(1), args);
        if (counts.isEmpty() || counts.getFirst() == 0) throw new MSException(code);
    }
}
