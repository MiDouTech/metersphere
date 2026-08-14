ALTER TABLE workflow_definition
    DROP INDEX uk_workflow_definition_code_scope,
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '流程版本' AFTER description,
    ADD COLUMN lifecycle VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ARCHIVED' AFTER version,
    ADD COLUMN published_time BIGINT NULL COMMENT '发布时间' AFTER lifecycle,
    ADD COLUMN published_by VARCHAR(64) NULL COMMENT '发布人' AFTER published_time,
    ADD COLUMN source_flow_id VARCHAR(64) NULL COMMENT '复制或升级来源流程' AFTER published_by,
    ADD COLUMN published_default_scene VARCHAR(32)
        GENERATED ALWAYS AS (CASE WHEN lifecycle = 'PUBLISHED' AND default_flow = b'1' THEN scene ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_workflow_definition_code_scope_version (code, scope_type, scope_id, version),
    ADD UNIQUE KEY uk_workflow_single_published_default (published_default_scene),
    ADD KEY idx_workflow_definition_source (source_flow_id);

UPDATE workflow_definition SET lifecycle = 'DRAFT', published_time = NULL;
UPDATE workflow_definition
SET lifecycle = 'PUBLISHED', published_time = update_time
WHERE id = (
    SELECT selected.id FROM (
        SELECT id FROM workflow_definition
        WHERE scene = 'BUG' AND default_flow = b'1' AND enabled = b'1'
        ORDER BY update_time DESC, create_time DESC LIMIT 1
    ) selected
);

ALTER TABLE status_item
    ADD COLUMN status_code VARCHAR(128) NULL COMMENT '流程版本内稳定状态编码' AFTER flow_id,
    ADD COLUMN initial_status BIT NOT NULL DEFAULT b'0' COMMENT '是否初始状态' AFTER status_code,
    ADD COLUMN terminal_status BIT NOT NULL DEFAULT b'0' COMMENT '是否结束状态' AFTER initial_status,
    ADD COLUMN enabled BIT NOT NULL DEFAULT b'1' COMMENT '是否启用' AFTER terminal_status,
    ADD UNIQUE KEY uk_status_item_flow_code (flow_id, status_code);

UPDATE status_item
SET status_code = CONCAT('LEGACY_', id)
WHERE flow_id IS NOT NULL AND (status_code IS NULL OR status_code = '');

UPDATE status_item si
JOIN status_definition sd ON sd.status_id = si.id AND sd.definition_id = 'START'
SET si.initial_status = b'1'
WHERE si.flow_id IS NOT NULL;

UPDATE status_item si
JOIN status_definition sd ON sd.status_id = si.id AND sd.definition_id = 'END'
SET si.terminal_status = b'1'
WHERE si.flow_id IS NOT NULL;

ALTER TABLE status_flow
    ADD COLUMN enabled BIT NOT NULL DEFAULT b'1' COMMENT '是否启用' AFTER to_id,
    ADD UNIQUE KEY uk_status_flow_flow_edge (flow_id, from_id, to_id);

ALTER TABLE bug
    ADD COLUMN workflow_id VARCHAR(64) NULL COMMENT '创建时绑定的流程 ID' AFTER `status`,
    ADD COLUMN workflow_version INT NULL COMMENT '创建时绑定的流程版本' AFTER workflow_id,
    ADD KEY idx_bug_workflow (workflow_id, workflow_version);

CREATE TABLE IF NOT EXISTS bug_status_transition_history
(
    id               VARCHAR(64) NOT NULL,
    bug_id           VARCHAR(64) NOT NULL,
    workflow_id      VARCHAR(64) NOT NULL,
    workflow_version INT NOT NULL,
    transition_id    VARCHAR(64) NOT NULL,
    from_status_id   VARCHAR(64) NOT NULL,
    to_status_id     VARCHAR(64) NOT NULL,
    operator         VARCHAR(64) NOT NULL,
    matched_role_ids VARCHAR(2000) NULL,
    comment          VARCHAR(2000) NULL,
    override_role    BIT NOT NULL DEFAULT b'0',
    override_reason  VARCHAR(2000) NULL,
    create_time      BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_bug_transition_history_bug_time (bug_id, create_time),
    KEY idx_bug_transition_history_workflow (workflow_id, workflow_version)
) COMMENT '缺陷状态流转历史';

CREATE TABLE IF NOT EXISTS workflow_migration_batch
(
    id                VARCHAR(64) NOT NULL,
    source_flow_id    VARCHAR(64) NULL,
    target_flow_id    VARCHAR(64) NOT NULL,
    dry_run           BIT NOT NULL DEFAULT b'1',
    status            VARCHAR(32) NOT NULL,
    mapping_snapshot  LONGTEXT NULL,
    total_count       BIGINT NOT NULL DEFAULT 0,
    success_count     BIGINT NOT NULL DEFAULT 0,
    skipped_count     BIGINT NOT NULL DEFAULT 0,
    failed_count      BIGINT NOT NULL DEFAULT 0,
    create_user       VARCHAR(64) NOT NULL,
    create_time       BIGINT NOT NULL,
    finish_time       BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_workflow_migration_target_status (target_flow_id, status)
) COMMENT '缺陷流程迁移批次';

CREATE TABLE IF NOT EXISTS workflow_migration_exception
(
    id              VARCHAR(64) NOT NULL,
    batch_id        VARCHAR(64) NOT NULL,
    bug_id          VARCHAR(64) NULL,
    source_status   VARCHAR(64) NULL,
    failure_code    VARCHAR(64) NOT NULL,
    failure_reason  VARCHAR(2000) NOT NULL,
    create_time     BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_workflow_migration_exception_batch (batch_id)
) COMMENT '缺陷流程迁移异常';
