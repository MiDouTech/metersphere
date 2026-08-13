ALTER TABLE user_role
    ADD COLUMN enabled bit NOT NULL DEFAULT b'1' COMMENT '是否启用' AFTER scope_id;

ALTER TABLE permission_resource
    MODIFY COLUMN type varchar(32) NOT NULL COMMENT 'MENU / PAGE / TAB / BUTTON / API';

ALTER TABLE status_flow
    ADD COLUMN flow_id varchar(64) DEFAULT NULL COMMENT '所属流程 ID' AFTER id,
    ADD KEY idx_status_flow_flow_id (flow_id);

ALTER TABLE status_item
    ADD COLUMN flow_id varchar(64) DEFAULT NULL COMMENT '所属流程 ID' AFTER id,
    ADD KEY idx_status_item_flow_id (flow_id);

CREATE TABLE IF NOT EXISTS role_assignment_rule
(
    id              varchar(64) NOT NULL COMMENT '主键',
    role_id         varchar(64) NOT NULL COMMENT '角色 ID',
    organization_id varchar(64) NOT NULL COMMENT '组织 ID',
    department_id   varchar(64)          DEFAULT NULL COMMENT '部门 ID',
    position_id     varchar(255)         DEFAULT NULL COMMENT '岗位',
    enabled         bit         NOT NULL DEFAULT b'1' COMMENT '是否启用',
    sync_mode       varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL / AUTO',
    create_time     bigint              DEFAULT NULL COMMENT '创建时间',
    update_time     bigint              DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_role_assignment_rule_role_id (role_id),
    KEY idx_role_assignment_rule_org_dept_position (organization_id, department_id, position_id)
) COMMENT '按组织岗位分配角色规则';

CREATE TABLE IF NOT EXISTS workflow_definition
(
    id           varchar(64)  NOT NULL COMMENT '主键',
    code         varchar(128) NOT NULL COMMENT '流程编码',
    name         varchar(255) NOT NULL COMMENT '流程名称',
    scene        varchar(32)  NOT NULL COMMENT '业务场景，例如 BUG',
    scope_type   varchar(32)  NOT NULL COMMENT 'SYSTEM / ORGANIZATION / PROJECT',
    scope_id     varchar(64)  NOT NULL COMMENT '作用范围 ID',
    default_flow bit          NOT NULL DEFAULT b'0' COMMENT '是否默认流程',
    enabled      bit          NOT NULL DEFAULT b'1' COMMENT '是否启用',
    description  varchar(1000)         DEFAULT NULL COMMENT '说明',
    create_time  bigint               DEFAULT NULL COMMENT '创建时间',
    update_time  bigint               DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_definition_code_scope (code, scope_type, scope_id),
    KEY idx_workflow_definition_scene_scope (scene, scope_type, scope_id)
) COMMENT '流程定义';

CREATE TABLE IF NOT EXISTS workflow_role
(
    id          varchar(64)  NOT NULL COMMENT '主键',
    flow_id     varchar(64)  NOT NULL COMMENT '流程 ID',
    code        varchar(128) NOT NULL COMMENT '流程角色编码',
    name        varchar(255) NOT NULL COMMENT '流程角色名称',
    role_type   varchar(32)  NOT NULL COMMENT 'SYSTEM_ROLE / FIELD_USER',
    role_id     varchar(64)           DEFAULT NULL COMMENT '系统角色 ID',
    field_key   varchar(64)           DEFAULT NULL COMMENT '业务字段 key',
    enabled     bit          NOT NULL DEFAULT b'1' COMMENT '是否启用',
    create_time bigint               DEFAULT NULL COMMENT '创建时间',
    update_time bigint               DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_role_flow_code (flow_id, code),
    KEY idx_workflow_role_flow_id (flow_id),
    KEY idx_workflow_role_role_id (role_id)
) COMMENT '流程角色';

CREATE TABLE IF NOT EXISTS status_flow_role_permission
(
    id               varchar(64) NOT NULL COMMENT '主键',
    flow_id          varchar(64) NOT NULL COMMENT '流程 ID',
    status_flow_id   varchar(64) NOT NULL COMMENT '状态流 ID',
    workflow_role_id varchar(64) NOT NULL COMMENT '流程角色 ID',
    visible          bit         NOT NULL DEFAULT b'1' COMMENT '是否可见',
    operable         bit         NOT NULL DEFAULT b'0' COMMENT '是否可执行',
    enabled          bit         NOT NULL DEFAULT b'1' COMMENT '是否启用',
    create_time      bigint              DEFAULT NULL COMMENT '创建时间',
    update_time      bigint              DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_status_flow_role_permission (status_flow_id, workflow_role_id),
    KEY idx_status_flow_role_permission_flow_id (flow_id),
    KEY idx_status_flow_role_permission_status_flow_id (status_flow_id),
    KEY idx_status_flow_role_permission_workflow_role_id (workflow_role_id)
) COMMENT '状态流转流程角色授权';
