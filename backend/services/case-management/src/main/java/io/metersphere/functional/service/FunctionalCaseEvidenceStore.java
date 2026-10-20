package io.metersphere.functional.service;

import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Approved MCP evidence shares the ordinary attachment UI; pending bytes are never exposed. */
@Service
public class FunctionalCaseEvidenceStore {
    @Resource private JdbcTemplate jdbc;
    public byte[] read(String projectId,String caseId,String fileId) {
        var rows=jdbc.query("SELECT e.payload FROM agent_case_evidence e JOIN functional_case c ON c.id=e.case_id JOIN functional_case_attachment a ON a.file_id=e.id AND a.case_id=c.id WHERE e.id=? AND e.case_id=? AND e.project_id=? AND c.project_id=? AND c.deleted=0 AND e.submission_id IS NOT NULL",
                (rs,n)->rs.getBytes(1),fileId,caseId,projectId,projectId);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public boolean retained(String fileId,String caseId) {
        return !jdbc.queryForList("SELECT id FROM agent_case_evidence WHERE id=? AND case_id=? AND submission_id IS NOT NULL",String.class,fileId,caseId).isEmpty();
    }
}
