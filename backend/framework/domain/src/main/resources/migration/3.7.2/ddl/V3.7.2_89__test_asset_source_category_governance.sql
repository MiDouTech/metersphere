CREATE TABLE test_asset_category
(
    id              VARCHAR(64)  NOT NULL,
    organization_id VARCHAR(64)  NOT NULL,
    parent_id       VARCHAR(64)  NOT NULL DEFAULT '',
    name            VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    path            VARCHAR(1024) NOT NULL,
    level           INT          NOT NULL,
    sort            BIGINT       NOT NULL DEFAULT 5000,
    deleted         BIT(1)       NOT NULL DEFAULT b'0',
    create_user     VARCHAR(64)  NOT NULL,
    create_time     BIGINT       NOT NULL,
    update_user     VARCHAR(64)  NOT NULL,
    update_time     BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_test_asset_category_name (organization_id, parent_id, normalized_name, deleted),
    KEY idx_test_asset_category_tree (organization_id, parent_id, deleted, sort)
) COMMENT 'Organization test asset business categories';

CREATE TABLE test_asset_metadata
(
    id                    VARCHAR(64) NOT NULL,
    organization_id       VARCHAR(64) NOT NULL,
    project_id            VARCHAR(64) NOT NULL,
    asset_type            VARCHAR(32) NOT NULL,
    asset_id              VARCHAR(64) NOT NULL,
    creation_source       VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    category_id           VARCHAR(64) NULL,
    source_reference_type VARCHAR(32) NULL,
    source_reference_id   VARCHAR(64) NULL,
    created_by_actor_type VARCHAR(16) NOT NULL DEFAULT 'SYSTEM',
    created_by_actor_id   VARCHAR(64) NULL,
    create_time           BIGINT NOT NULL,
    update_user           VARCHAR(64) NULL,
    update_time           BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_test_asset_metadata (organization_id, project_id, asset_type, asset_id),
    KEY idx_test_asset_metadata_source (project_id, asset_type, creation_source),
    KEY idx_test_asset_metadata_category (organization_id, category_id, asset_type),
    CONSTRAINT fk_test_asset_metadata_category FOREIGN KEY (category_id) REFERENCES test_asset_category (id)
) COMMENT 'Stable test asset source and category metadata';

CREATE TABLE test_asset_governance_audit
(
    id              VARCHAR(64) NOT NULL,
    organization_id VARCHAR(64) NOT NULL,
    project_id      VARCHAR(64) NULL,
    event_type      VARCHAR(48) NOT NULL,
    resource_id     VARCHAR(64) NOT NULL,
    before_value    TEXT NULL,
    after_value     TEXT NULL,
    evidence        VARCHAR(500) NULL,
    operator        VARCHAR(64) NOT NULL,
    create_time     BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_test_asset_governance_audit (organization_id, resource_id, create_time)
) COMMENT 'Safe audit trail for test asset governance';

-- Existing rows are deliberately UNKNOWN. New trusted creation paths upsert a stronger source.
INSERT IGNORE INTO test_asset_metadata
(id, organization_id, project_id, asset_type, asset_id, creation_source, created_by_actor_type,
 created_by_actor_id, create_time, update_user, update_time)
SELECT UUID_SHORT(), p.organization_id, v.project_id, v.asset_type, v.asset_id, 'UNKNOWN', 'SYSTEM',
       NULL, COALESCE(MIN(v.created_at), UNIX_TIMESTAMP() * 1000), NULL, UNIX_TIMESTAMP() * 1000
FROM test_asset_version v
JOIN project p ON p.id = v.project_id
GROUP BY p.organization_id, v.project_id, v.asset_type, v.asset_id;

-- Backfill assets that do not yet have a published test_asset_version. This keeps history honest:
-- UNKNOWN is persisted instead of being inferred from names, owners or content.
INSERT IGNORE INTO test_asset_metadata
(id, organization_id, project_id, asset_type, asset_id, creation_source, created_by_actor_type,
 created_by_actor_id, create_time, update_user, update_time)
SELECT UUID_SHORT(), p.organization_id, assets.project_id, assets.asset_type, assets.asset_id,
       'UNKNOWN', 'SYSTEM', NULL, assets.create_time, NULL, UNIX_TIMESTAMP() * 1000
FROM (
    SELECT project_id, 'CASE' asset_type, COALESCE(NULLIF(ref_id, ''), id) asset_id,
           MIN(create_time) create_time FROM functional_case WHERE latest=b'1' AND deleted=b'0'
    GROUP BY project_id, COALESCE(NULLIF(ref_id, ''), id)
    UNION ALL
    SELECT project_id, 'DOCUMENT', id, create_time FROM ai_source_document WHERE deleted=b'0'
    UNION ALL
    SELECT project_id, 'DATASET', COALESCE(NULLIF(ref_id, ''), id), create_time FROM file_metadata
      WHERE latest=b'1' AND UPPER(COALESCE(type, '')) IN ('CSV','JSON','XLS','XLSX')
    UNION ALL
    SELECT project_id, 'ENVIRONMENT', id, create_time FROM environment
    UNION ALL
    SELECT project_id, 'COMMON_STEP', id, create_time FROM custom_function
    UNION ALL
    SELECT project_id, 'API_DEFINITION', COALESCE(NULLIF(ref_id, ''), id), create_time FROM api_definition
      WHERE latest=b'1' AND deleted=b'0'
    UNION ALL
    SELECT t.project_id, 'EVIDENCE', a.id, a.create_time FROM ai_execution_artifact a
      JOIN ai_execution_task t ON t.id=a.task_id
    UNION ALL
    SELECT project_id, 'BUG', id, create_time FROM bug WHERE deleted=b'0'
) assets
JOIN project p ON p.id=assets.project_id;

INSERT INTO test_asset_governance_audit
(id, organization_id, project_id, event_type, resource_id, before_value, after_value, evidence, operator, create_time)
SELECT UUID_SHORT(), organization_id, NULL, 'METADATA_BACKFILL', 'V3.7.2_89', NULL,
       JSON_OBJECT('total', COUNT(1),
                   'unknown', SUM(creation_source='UNKNOWN'),
                   'manual', SUM(creation_source='MANUAL'),
                   'ai', SUM(creation_source='AI'),
                   'import', SUM(creation_source='IMPORT'),
                   'sync', SUM(creation_source='SYNC'),
                   'automation', SUM(creation_source='AUTOMATION'),
                   'failed', 0),
       'Idempotent evidence-only backfill; business asset rows unchanged', 'SYSTEM', UNIX_TIMESTAMP() * 1000
FROM test_asset_metadata
GROUP BY organization_id;
