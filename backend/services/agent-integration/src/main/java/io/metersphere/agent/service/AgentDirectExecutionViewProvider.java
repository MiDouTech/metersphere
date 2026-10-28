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
        // An organization asset exposes the source execution view, never a fabricated hub submission.
        var bindings=jdbc.queryForList("SELECT DISTINCT f.id,f.project_id,f.last_execute_time,f.last_execute_result,f.last_execute_user FROM case_asset_source_relation r JOIN functional_case f ON BINARY f.project_id=BINARY r.source_project_id AND BINARY COALESCE(NULLIF(f.ref_id,''),f.id)=BINARY r.source_case_id AND f.deleted=0 AND f.latest=1 JOIN project p ON BINARY p.id=BINARY f.project_id AND p.deleted=0 JOIN project a ON a.id=? AND BINARY a.organization_id=BINARY p.organization_id AND a.deleted=0 WHERE r.asset_case_id=?",source.getProjectId(),source.getId());
        if(bindings.size()>1)throw new io.metersphere.sdk.exception.MSException("资产来源映射不唯一，请联系管理员核对");
        if(bindings.size()==1) {
            var binding=bindings.getFirst();
            var effective=new FunctionalCaseDetailDTO();
            effective.setId((String)binding.get("id"));effective.setProjectId((String)binding.get("project_id"));
            effective.setLastExecuteResult((String)binding.get("last_execute_result"));effective.setLastExecuteUser((String)binding.get("last_execute_user"));
            if(binding.get("last_execute_time") instanceof Number time)effective.setLastExecuteTime(time.longValue());
            source=effective;
        }
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
