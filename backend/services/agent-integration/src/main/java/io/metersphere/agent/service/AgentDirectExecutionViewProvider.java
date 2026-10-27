package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentFactCipher;
import io.metersphere.agent.security.AgentSensitiveDataSanitizer;
import io.metersphere.functional.dto.FunctionalCaseDetailDTO;
import io.metersphere.functional.service.FunctionalExecutionViewProvider;
import io.metersphere.sdk.util.JSON;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Optional;

/** Reads only the submission belonging to the source's current execution, never an older fallback. */
@Component
public class AgentDirectExecutionViewProvider implements FunctionalExecutionViewProvider {
    private final JdbcTemplate jdbc;
    private final AgentFactCipher cipher;

    public AgentDirectExecutionViewProvider(JdbcTemplate jdbc, AgentFactCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<View> current(FunctionalCaseDetailDTO source) {
        if (source.getLastExecuteTime() == null) return Optional.empty();
        var references=jdbc.queryForList("SELECT submission_id FROM agent_case_current_execution WHERE case_id=?",String.class,source.getId());
        if(!references.isEmpty() && (references.getFirst()==null || references.getFirst().isBlank()))return Optional.empty();
        var rows = jdbc.queryForList("""
                SELECT s.id,s.facts FROM agent_case_current_execution r
                JOIN agent_case_submission s ON s.id=r.submission_id AND s.case_id=r.case_id
                WHERE r.case_id=? AND s.project_id=? AND r.execute_time=? AND s.gate_status='ACCEPTED' AND s.result=? AND s.user_id=?
                """, source.getId(), source.getProjectId(), source.getLastExecuteTime(),source.getLastExecuteResult(),source.getLastExecuteUser());
        if (rows.isEmpty() && references.isEmpty()) rows = jdbc.queryForList("""
                SELECT id, facts FROM agent_case_submission
                 WHERE case_id=? AND project_id=? AND create_time=? AND gate_status='ACCEPTED' AND result=? AND user_id=?
                 LIMIT 2
                """, source.getId(), source.getProjectId(), source.getLastExecuteTime(),source.getLastExecuteResult(),source.getLastExecuteUser());
        if (rows.size()!=1) return Optional.empty();
        var row = rows.getFirst();
        Map<String, Object> facts = JSON.parseObject(
                AgentSensitiveDataSanitizer.sanitizeJson(cipher.reveal((String) row.get("facts"))), Map.class);
        return Optional.of(new View("DIRECT_SUBMISSION", (String) row.get("id"), facts));
    }
}
