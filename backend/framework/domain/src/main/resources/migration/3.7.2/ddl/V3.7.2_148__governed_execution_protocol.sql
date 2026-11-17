-- MSP-32 v1.7: no default grants, no automatic migration/publication, no cleanup job.
-- A trusted human upload has no Agent token; preserve that fact rather than inventing one.
ALTER TABLE agent_case_evidence MODIFY COLUMN token_id VARCHAR(64) NULL,
 ADD COLUMN protocol_instance_id VARCHAR(64) COLLATE utf8mb4_bin NULL,
 ADD COLUMN uploader_person VARCHAR(128) COLLATE utf8mb4_bin NULL;
-- An instance lease is not a Runner lease. Preserve original Runner rows and record new provenance explicitly.
ALTER TABLE agent_case_submission MODIFY COLUMN token_id VARCHAR(64) NULL;
ALTER TABLE ai_execution_step_result MODIFY COLUMN lease_id VARCHAR(50) NULL,
 ADD COLUMN protocol_instance_id VARCHAR(64) COLLATE utf8mb4_bin NULL,
 ADD COLUMN protocol_generation BIGINT NULL;
CREATE TABLE msp_case_contract (
 case_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 version BIGINT NOT NULL,
 project_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 state VARCHAR(16) NOT NULL,
 author_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 author_person VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
 source_version VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 content_hash CHAR(64) NOT NULL,
 payload MEDIUMTEXT NOT NULL,
 create_time BIGINT NOT NULL,
 PRIMARY KEY(case_id,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE msp_protocol_identity (
 user_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 organization_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 person_ref VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
 version BIGINT NOT NULL,
 evidence_ref VARCHAR(255) NOT NULL,
 PRIMARY KEY(user_id,organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE msp_protocol_policy (
 organization_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 environment_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 version BIGINT NOT NULL,
 enabled BOOLEAN NOT NULL,
 payload MEDIUMTEXT NOT NULL,
 PRIMARY KEY(organization_id,environment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE msp_protocol_authorization (
 source_type VARCHAR(16) COLLATE utf8mb4_bin NOT NULL,
 source_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 version BIGINT NOT NULL,
 organization_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 environment_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 expires_at BIGINT NOT NULL,
 payload MEDIUMTEXT NOT NULL,
 PRIMARY KEY(source_type,source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE msp_protocol_subject (
 token_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 organization_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 user_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 agent_ref VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
 enabled BOOLEAN NOT NULL,
 version BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE msp_protocol_grant (
 organization_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 user_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 environment_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 capability VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 expires_at BIGINT NOT NULL,
 version BIGINT NOT NULL,
 PRIMARY KEY(organization_id,user_id,environment_id,capability)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE msp_protocol_session (
 id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 organization_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 project_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 owner_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 agent_ref VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
 source_type VARCHAR(16) NOT NULL,
 source_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 source_execution_id VARCHAR(64) COLLATE utf8mb4_bin NULL,
 environment_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 state VARCHAR(16) NOT NULL,
 version BIGINT NOT NULL,
 context_version BIGINT NOT NULL,
 calls_used BIGINT NOT NULL DEFAULT 0,
 recoveries_used BIGINT NOT NULL DEFAULT 0,
 payload MEDIUMTEXT NOT NULL,
 create_time BIGINT NOT NULL,
 KEY idx_protocol_session_owner(organization_id,owner_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE msp_protocol_instance (
 id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 session_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 case_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 source_case_id VARCHAR(64) COLLATE utf8mb4_bin NULL,
 phase VARCHAR(32) NOT NULL,
 channel VARCHAR(16) NOT NULL,
 verification VARCHAR(32) NOT NULL,
 result VARCHAR(32) NULL,
 version BIGINT NOT NULL,
 result_version BIGINT NOT NULL,
 revision BIGINT NOT NULL,
 generation BIGINT NOT NULL,
 lease_owner VARCHAR(255) COLLATE utf8mb4_bin NULL,
 lease_until BIGINT NOT NULL,
 calls_used BIGINT NOT NULL,
 recoveries_used BIGINT NOT NULL,
 recovery_ms BIGINT NOT NULL,
 recovery_since BIGINT NULL,
 payload MEDIUMTEXT NOT NULL,
 KEY idx_protocol_instance_session(session_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE msp_protocol_operation (
 scope_key CHAR(64) COLLATE utf8mb4_bin NOT NULL,
 request_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
 operation_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 request_hash CHAR(64) NOT NULL,
 status VARCHAR(16) NOT NULL,
 metered BOOLEAN NOT NULL,
 response MEDIUMTEXT NULL,
 error_code VARCHAR(64) NULL,
 create_time BIGINT NOT NULL,
 PRIMARY KEY(scope_key,request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE msp_protocol_event (
 id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 session_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 instance_id VARCHAR(64) COLLATE utf8mb4_bin NULL,
 operation_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 kind VARCHAR(64) NOT NULL,
 actor_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 payload MEDIUMTEXT NOT NULL,
 create_time BIGINT NOT NULL,
 KEY idx_protocol_event_session(session_id,create_time,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE msp_protocol_evidence_hold (
 kind VARCHAR(16) COLLATE utf8mb4_bin NOT NULL,
 evidence_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 owner_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 reason VARCHAR(32) NOT NULL,
 create_time BIGINT NOT NULL,
 PRIMARY KEY(kind,evidence_id,owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE msp_knowledge (
 id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 organization_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 creator_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 state VARCHAR(16) NOT NULL,
 version BIGINT NOT NULL,
 published_version BIGINT NULL,
 safety_generation BIGINT NOT NULL,
 payload MEDIUMTEXT NOT NULL,
 update_time BIGINT NOT NULL,
 KEY idx_knowledge_org(organization_id,state,update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE msp_knowledge_version (
 knowledge_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 version BIGINT NOT NULL,
 state VARCHAR(16) NOT NULL,
 payload MEDIUMTEXT NOT NULL,
 content_hash CHAR(64) NOT NULL,
 author_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 last_verified_at BIGINT NULL,
 create_time BIGINT NOT NULL,
 PRIMARY KEY(knowledge_id,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE msp_knowledge_usage (
 id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 knowledge_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 knowledge_version BIGINT NOT NULL,
 instance_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 step_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
 state VARCHAR(32) NOT NULL,
 result_version BIGINT NULL,
 UNIQUE KEY uk_knowledge_adoption(knowledge_id,knowledge_version,instance_id,step_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE msp_protocol_migration (
 source_key CHAR(64) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 batch_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 knowledge_id VARCHAR(64) COLLATE utf8mb4_bin NULL,
 state VARCHAR(16) NOT NULL,
 error_code VARCHAR(64) NULL,
 first_published_version BIGINT NULL,
 payload MEDIUMTEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE msp_protocol_publication (
 instance_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 result_version BIGINT NOT NULL,
 source_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 create_time BIGINT NOT NULL,
 PRIMARY KEY(instance_id,result_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE msp_protocol_result_revision (
 instance_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 result_version BIGINT NOT NULL,
 submission_revision BIGINT NOT NULL,
 result VARCHAR(32) NOT NULL,
 verification VARCHAR(32) NOT NULL,
 operation_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 payload MEDIUMTEXT NOT NULL,
 create_time BIGINT NOT NULL,
 PRIMARY KEY(instance_id,result_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE msp_protocol_owned_object (
 session_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 entity_type VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
 object_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
 instance_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 action_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 create_time BIGINT NOT NULL,
 PRIMARY KEY(session_id,entity_type,object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
