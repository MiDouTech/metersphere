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
        var rows = jdbc.queryForList("""
                SELECT id, facts FROM agent_case_submission
                 WHERE case_id=? AND project_id=? AND create_time=? AND gate_status='ACCEPTED'
                 ORDER BY id LIMIT 1
                """, source.getId(), source.getProjectId(), source.getLastExecuteTime());
        if (rows.isEmpty()) return Optional.empty();
        var row = rows.getFirst();
        Map<String, Object> facts = JSON.parseObject(
                AgentSensitiveDataSanitizer.sanitize(cipher.reveal((String) row.get("facts"))), Map.class);
        return Optional.of(new View("DIRECT_SUBMISSION", (String) row.get("id"), facts));
    }
}
