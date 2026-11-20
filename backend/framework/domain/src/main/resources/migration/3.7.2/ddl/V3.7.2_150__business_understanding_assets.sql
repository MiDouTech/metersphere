CREATE TABLE business_asset (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    identity_key VARCHAR(64) NOT NULL,
    current_version_id VARCHAR(64) NULL,
    revision INT NOT NULL DEFAULT 0,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    UNIQUE KEY uk_business_asset_identity (project_id, identity_key)
);
CREATE TABLE business_asset_version (
    id VARCHAR(64) PRIMARY KEY,
    asset_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    version_no INT NOT NULL,
    base_version INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    system_key VARCHAR(128) NOT NULL,
    module_key VARCHAR(128) NOT NULL,
    flow_key VARCHAR(128) NOT NULL,
    page_keys_json TEXT NOT NULL,
    snapshot_json MEDIUMTEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    branch_count INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    create_time BIGINT NOT NULL,
    UNIQUE KEY uk_business_asset_version (asset_id, version_no),
    KEY idx_business_version_project (project_id, asset_id)
);
CREATE TABLE business_asset_submission (
    project_id VARCHAR(64) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    result_json MEDIUMTEXT NOT NULL,
    PRIMARY KEY (project_id, actor_id, request_key)
);
CREATE TABLE business_asset_reader (
    asset_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    PRIMARY KEY(asset_id,user_id)
);
CREATE TABLE business_asset_relation (
    project_id VARCHAR(64) NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    create_time BIGINT NOT NULL,
    PRIMARY KEY(project_id,source_id,target_id,relation_type)
);
CREATE TABLE business_asset_audit (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    asset_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    version_id VARCHAR(64) NULL,
    reason VARCHAR(500) NOT NULL,
    create_time BIGINT NOT NULL,
    KEY idx_business_asset_audit (project_id, asset_id, create_time)
);

-- Independent governance permissions; no dependency on AI_EXECUTION permissions.
INSERT IGNORE INTO user_role_permission (id,role_id, permission_id)
SELECT UUID(),role_id, 'BUSINESS_ASSET:READ' FROM user_role_permission
WHERE permission_id='FUNCTIONAL_CASE_AI:READ';
INSERT IGNORE INTO user_role_permission (id,role_id, permission_id)
SELECT UUID(),role_id, 'BUSINESS_ASSET:WRITE' FROM user_role_permission
WHERE permission_id='FUNCTIONAL_CASE_AI:SAVE';
INSERT IGNORE INTO user_role_permission(id,role_id,permission_id)
SELECT UUID(),role_id,'TEST_ASSET_VERSION:MANAGE' FROM user_role_permission WHERE permission_id='AI_EXECUTION:RUN';
