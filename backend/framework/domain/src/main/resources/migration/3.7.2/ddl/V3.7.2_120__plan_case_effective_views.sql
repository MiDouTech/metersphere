-- Preserve plan membership, assignment and history; active plans read the source execution summary.
CREATE SQL SECURITY INVOKER VIEW plan_functional_case_effective AS
SELECT r.id, r.test_plan_id, r.functional_case_id, r.create_time, r.create_user,
       r.execute_user, r.pos, r.test_plan_collection_id,
       CASE WHEN p.status='ARCHIVED' THEN r.last_exec_time ELSE c.last_execute_time END AS last_exec_time,
       CASE WHEN p.status='ARCHIVED' THEN r.last_exec_result
            ELSE COALESCE(NULLIF(NULLIF(c.last_execute_result,''),'-'),'PENDING') END AS last_exec_result
FROM test_plan_functional_case r
JOIN test_plan p ON p.id=r.test_plan_id
JOIN functional_case c ON c.id=r.functional_case_id;

-- Historical links are deliberately LEGACY: only links created by the new synchronizer may be pruned.
ALTER TABLE bug_relation_case ADD COLUMN sync_origin VARCHAR(16) NOT NULL DEFAULT 'LEGACY';
CREATE INDEX idx_bug_relation_source ON bug_relation_case(case_id,case_type,test_plan_id,test_plan_case_id);
CREATE INDEX idx_plan_bug_source ON bug_relation_case(test_plan_case_id,bug_id,sync_origin,id);

CREATE SQL SECURITY INVOKER VIEW plan_case_effective_bug_relation AS
SELECT r.id,r.case_id,r.bug_id,r.case_type,r.test_plan_id,r.test_plan_case_id,
       r.create_user,r.create_time,r.update_time,r.sync_origin
FROM bug_relation_case r
WHERE (r.sync_origin<>'SOURCE_SYNC'
   OR EXISTS (SELECT 1 FROM test_plan p WHERE p.id=r.test_plan_id AND p.status='ARCHIVED')
   OR EXISTS (SELECT 1 FROM bug_relation_case s
              WHERE s.case_id=r.case_id AND s.bug_id=r.bug_id AND s.case_type=r.case_type
                AND COALESCE(s.test_plan_id,'')='' AND COALESCE(s.test_plan_case_id,'')=''))
 AND NOT EXISTS (
    SELECT 1 FROM bug_relation_case preferred
     WHERE preferred.test_plan_case_id=r.test_plan_case_id AND preferred.bug_id=r.bug_id
       AND preferred.case_type=r.case_type
       AND ((preferred.sync_origin<>'SOURCE_SYNC' AND r.sync_origin='SOURCE_SYNC')
          OR (preferred.sync_origin=r.sync_origin AND preferred.id<r.id))
 )
UNION ALL
SELECT LEFT(SHA2(CONCAT('effective-source:',pc.id,':',s.bug_id),256),48),
       pc.functional_case_id,s.bug_id,s.case_type,pc.test_plan_id,pc.id,
       MIN(s.create_user),MIN(s.create_time),MAX(s.update_time),'SOURCE_SYNC'
FROM test_plan_functional_case pc
JOIN test_plan p ON p.id=pc.test_plan_id AND p.status<>'ARCHIVED'
JOIN functional_case c ON c.id=pc.functional_case_id AND c.deleted=0 AND c.project_id=p.project_id
JOIN bug_relation_case s ON s.case_id=c.id AND s.case_type='FUNCTIONAL'
 AND COALESCE(s.test_plan_id,'')='' AND COALESCE(s.test_plan_case_id,'')=''
JOIN bug b ON b.id=s.bug_id AND b.project_id=c.project_id AND b.deleted=0
WHERE NOT EXISTS (SELECT 1 FROM bug_relation_case existing
                   WHERE existing.test_plan_case_id=pc.id AND existing.bug_id=s.bug_id)
GROUP BY pc.functional_case_id,s.bug_id,s.case_type,pc.test_plan_id,pc.id;
