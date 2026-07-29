-- 企微同步：跨用户邮箱冲突待人工处理
CREATE TABLE IF NOT EXISTS org_sync_email_conflict (
    id                   VARCHAR(50)  NOT NULL COMMENT '主键',
    organization_id      VARCHAR(50)  NOT NULL COMMENT '组织ID',
    sync_log_id          VARCHAR(50)  NULL COMMENT '关联同步日志',
    wecom_userid         VARCHAR(100) NOT NULL COMMENT '企微 userid',
    pending_user_id      VARCHAR(50)  NULL COMMENT '已创建/已有本地用户ID',
    wecom_user_name      VARCHAR(255) NULL COMMENT '企微成员姓名',
    conflict_email       VARCHAR(255) NOT NULL COMMENT '企微期望邮箱',
    occupied_user_id     VARCHAR(50)  NOT NULL COMMENT '占用方用户ID',
    occupied_user_name   VARCHAR(255) NULL COMMENT '占用方姓名',
    conflict_scene       VARCHAR(20)  NOT NULL COMMENT 'CREATE|UPDATE',
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|RESOLVED',
    resolution           VARCHAR(20)  NULL COMMENT 'SKIP|OVERWRITE|CREATE',
    resolved_by          VARCHAR(50)  NULL COMMENT '处理人',
    resolved_time        BIGINT       NULL COMMENT '处理时间',
    create_time          BIGINT       NOT NULL COMMENT '创建时间',
    update_time          BIGINT       NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_org_status (organization_id, status),
    KEY idx_org_wecom_email (organization_id, wecom_userid, conflict_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='企微同步邮箱冲突';
