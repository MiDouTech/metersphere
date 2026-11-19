-- Preserve every retired protocol row as historical evidence. No runtime reads these tables.
-- Existing Flyway migrations 148/149 must remain byte-for-byte unchanged.
RENAME TABLE msp_case_contract TO retired_msp_case_contract,
 msp_protocol_identity TO retired_msp_protocol_identity,
 msp_protocol_policy TO retired_msp_protocol_policy,
 msp_protocol_authorization TO retired_msp_protocol_authorization,
 msp_protocol_subject TO retired_msp_protocol_subject,
 msp_protocol_grant TO retired_msp_protocol_grant,
 msp_protocol_session TO retired_msp_protocol_session,
 msp_protocol_instance TO retired_msp_protocol_instance,
 msp_protocol_operation TO retired_msp_protocol_operation,
 msp_protocol_event TO retired_msp_protocol_event,
 msp_protocol_evidence_hold TO retired_msp_protocol_evidence_hold,
 msp_knowledge_usage TO retired_msp_knowledge_usage,
 msp_protocol_migration TO retired_msp_protocol_migration,
 msp_protocol_publication TO retired_msp_protocol_publication,
 msp_protocol_result_revision TO retired_msp_protocol_result_revision,
 msp_protocol_owned_object TO retired_msp_protocol_owned_object,
 ai_execution_knowledge_usage TO retired_ai_execution_knowledge_usage;

-- Knowledge versions retain exact source snapshots. Never infer a project for an org-scoped
-- knowledge item without a source binding; such items remain in the immutable legacy archive.
-- Mapped knowledge is creator-only until deliberately merged into a project business asset.
INSERT INTO business_asset(id,project_id,identity_key,current_version_id,revision,archived,create_time,update_time)
SELECT DISTINCT SHA2(CONCAT(k.id,':',source.project_id),256),source.project_id,
 SHA2(CONCAT('legacy:',k.id,':',source.project_id),256),NULL,0,FALSE,k.update_time,k.update_time
FROM msp_knowledge k
JOIN JSON_TABLE(k.payload,'$.sources[*]' COLUMNS(project_id VARCHAR(64) PATH '$.projectId')) source
JOIN project p ON p.id=source.project_id COLLATE utf8mb4_general_ci AND p.organization_id=k.organization_id AND p.deleted=0;

INSERT INTO business_asset_reader(asset_id,user_id)
SELECT DISTINCT SHA2(CONCAT(k.id,':',source.project_id),256),k.creator_id
FROM msp_knowledge k
JOIN JSON_TABLE(k.payload,'$.sources[*]' COLUMNS(project_id VARCHAR(64) PATH '$.projectId')) source
JOIN business_asset a ON a.id=SHA2(CONCAT(k.id,':',source.project_id),256);

INSERT INTO business_asset_version(id,asset_id,project_id,version_no,base_version,title,system_key,module_key,flow_key,page_keys_json,snapshot_json,content_hash,branch_count,status,actor_id,create_time)
SELECT SHA2(CONCAT(a.id,':',v.version),256),a.id,a.project_id,v.version,0,
 LEFT(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(v.payload,'$.title')),'历史业务理解'),255),'','','','[]',
 JSON_OBJECT('projectId',a.project_id,'schemaVersion','1.0','operation','UPDATE','assetId',a.id,'baseVersion',0,
  'idempotencyKey',CONCAT('legacy:',v.knowledge_id,':',v.version),
  'scope',JSON_OBJECT('systemKey','','moduleKey','','pageKeys',JSON_ARRAY(),'flowKey',''),
  'title',LEFT(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(v.payload,'$.title')),'历史业务理解'),255),
  'document',JSON_OBJECT('format','markdown','content',COALESCE(JSON_UNQUOTE(JSON_EXTRACT(v.payload,'$.body')),'')),
  'branches',JSON_ARRAY(),'changeSummary','历史知识迁移，保留原快照，待业务归类',
  'legacyPayload',CAST(v.payload AS JSON)),v.content_hash,0,'UNCLASSIFIED',v.author_id,v.create_time
FROM msp_knowledge k JOIN msp_knowledge_version v ON v.knowledge_id=k.id
JOIN business_asset a ON a.id=SHA2(CONCAT(k.id,':',a.project_id),256);

RENAME TABLE msp_knowledge TO retired_msp_knowledge,
 msp_knowledge_version TO retired_msp_knowledge_version;

-- Remove the now-unused dedicated flags and grants; legacy evidence is retained above.
DELETE FROM user_role_permission WHERE permission_id LIKE 'PROTOCOL:%' OR permission_id LIKE 'KNOWLEDGE:%';
