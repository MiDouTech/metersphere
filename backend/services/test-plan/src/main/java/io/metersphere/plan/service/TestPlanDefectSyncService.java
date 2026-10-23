package io.metersphere.plan.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Adds missing project-case defect links to the current plan; never removes manual/history links. */
@Service
public class TestPlanDefectSyncService {
    private final JdbcTemplate jdbc;
    public TestPlanDefectSyncService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public int sync(String projectId, String planId, String userId, String caseType) {
        // Caller holds the plan row lock. Stable IDs also fence simultaneous replays.
        jdbc.update("""
            DELETE r FROM bug_relation_case r
             JOIN test_plan p ON p.id=r.test_plan_id AND p.status<>'ARCHIVED'
             JOIN functional_case c ON c.id=r.case_id AND c.project_id=p.project_id
             LEFT JOIN bug_relation_case source
               ON source.case_id=r.case_id AND source.bug_id=r.bug_id AND source.case_type=r.case_type
              AND COALESCE(source.test_plan_id,'')='' AND COALESCE(source.test_plan_case_id,'')=''
            WHERE r.test_plan_id=? AND c.project_id=? AND r.sync_origin='SOURCE_SYNC'
              AND source.id IS NULL
            """, planId, projectId);
        return jdbc.update("""
            INSERT INTO bug_relation_case
              (id,case_id,bug_id,case_type,test_plan_id,test_plan_case_id,create_user,create_time,update_time,sync_origin)
            SELECT LEFT(SHA2(CONCAT('plan-source-defect:',r.id,':',source.bug_id),256),48),
                   r.functional_case_id,source.bug_id,?,r.test_plan_id,r.id,?,?,?,'SOURCE_SYNC'
              FROM test_plan_functional_case r
              JOIN functional_case c ON c.id=r.functional_case_id
              JOIN (SELECT DISTINCT case_id,bug_id FROM bug_relation_case
                     WHERE case_type=? AND COALESCE(test_plan_id,'')=''
                       AND COALESCE(test_plan_case_id,'')='') source ON source.case_id=c.id
              JOIN bug b ON b.id=source.bug_id AND b.project_id=c.project_id AND b.deleted=0
             WHERE r.test_plan_id=? AND c.project_id=? AND c.deleted=0
               AND NOT EXISTS (SELECT 1 FROM bug_relation_case existing
                                WHERE existing.test_plan_case_id=r.id AND existing.bug_id=source.bug_id)
            ON DUPLICATE KEY UPDATE id=bug_relation_case.id
            """, caseType,userId,System.currentTimeMillis(),System.currentTimeMillis(),caseType,planId,projectId);
    }
}
