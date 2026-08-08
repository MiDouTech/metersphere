CREATE TABLE IF NOT EXISTS permission_resource
(
    id                varchar(64)   NOT NULL COMMENT '主键',
    code              varchar(128)  NOT NULL COMMENT '资源编码',
    name              varchar(255)  NOT NULL COMMENT '展示名称',
    type              varchar(32)   NOT NULL COMMENT 'MENU / PAGE / BUTTON / API',
    scope_type        varchar(32)   NOT NULL COMMENT 'SYSTEM / ORGANIZATION / PROJECT',
    parent_code       varchar(128)           DEFAULT NULL COMMENT '父级资源编码',
    route_name        varchar(128)           DEFAULT NULL COMMENT '页面路由名',
    permission_id     varchar(128)           DEFAULT NULL COMMENT '关联现有操作权限',
    visible_default   bit           NOT NULL DEFAULT b'1' COMMENT '默认是否可见',
    operable_default  bit           NOT NULL DEFAULT b'0' COMMENT '默认是否可操作',
    sort              int           NOT NULL DEFAULT 0 COMMENT '排序',
    enabled           bit           NOT NULL DEFAULT b'1' COMMENT '是否启用',
    description       varchar(1000)          DEFAULT NULL COMMENT '描述',
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_resource_code (code),
    KEY idx_permission_resource_scope_type (scope_type),
    KEY idx_permission_resource_parent_code (parent_code),
    KEY idx_permission_resource_route_name (route_name),
    KEY idx_permission_resource_permission_id (permission_id)
) COMMENT 'UI 权限资源目录';

CREATE TABLE IF NOT EXISTS user_role_ui_permission
(
    id             varchar(64)  NOT NULL COMMENT '主键',
    role_id        varchar(64)  NOT NULL COMMENT '角色 ID',
    resource_code  varchar(128) NOT NULL COMMENT '资源编码',
    visible        bit          NOT NULL DEFAULT b'0' COMMENT '是否可见',
    operable       bit          NOT NULL DEFAULT b'0' COMMENT '是否可操作',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_ui_permission_role_resource (role_id, resource_code),
    KEY idx_role_ui_permission_role_id (role_id),
    KEY idx_role_ui_permission_resource_code (resource_code)
) COMMENT '角色 UI 权限配置';
