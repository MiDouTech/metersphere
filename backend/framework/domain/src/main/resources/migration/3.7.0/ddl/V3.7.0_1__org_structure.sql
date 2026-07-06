-- set innodb lock wait timeout
SET SESSION innodb_lock_wait_timeout = 7200;

CREATE TABLE IF NOT EXISTS department (
    id                  VARCHAR(50)  NOT NULL COMMENT '部门ID',
    organization_id     VARCHAR(50)  NOT NULL COMMENT '所属MeterSphere组织ID',
    name                VARCHAR(255) NOT NULL COMMENT '部门名称',
    parent_id           VARCHAR(50)           COMMENT '父部门本地ID',
    wecom_dept_id       BIGINT                COMMENT '企微部门ID',
    sort_order          INT          DEFAULT 0 COMMENT '排序',
    dept_status         TINYINT      DEFAULT 1 COMMENT '1启用 0停用',
    sync_status         TINYINT      DEFAULT 0 COMMENT '0未同步 1已同步 2同步失败',
    sync_time           BIGINT                COMMENT '最近同步时间戳',
    leader_wecom_userid VARCHAR(100)          COMMENT '部门负责人企微UserID',
    create_time         BIGINT       NOT NULL,
    update_time         BIGINT       NOT NULL,
    create_user         VARCHAR(50)  NOT NULL,
    update_user         VARCHAR(50)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_wecom_dept (organization_id, wecom_dept_id),
    KEY idx_org_parent (organization_id, parent_id),
    KEY idx_org_status (organization_id, dept_status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '组织部门表';

CREATE TABLE IF NOT EXISTS org_wecom_sync_config (
    id                  VARCHAR(50)  NOT NULL PRIMARY KEY,
    organization_id     VARCHAR(50)  NOT NULL COMMENT 'MS组织ID',
    corp_id             VARCHAR(100) NOT NULL COMMENT '企微CorpID',
    contact_secret      VARCHAR(255) NOT NULL COMMENT '通讯录Secret',
    agent_id            VARCHAR(50)           COMMENT '应用AgentId（可选）',
    schedule_enabled    TINYINT      DEFAULT 0 COMMENT '是否启用定时同步',
    schedule_cron       VARCHAR(50)           COMMENT 'Cron表达式',
    retry_times         INT          DEFAULT 3 COMMENT '失败重试次数',
    last_sync_time      BIGINT                COMMENT '最近同步时间',
    create_time         BIGINT       NOT NULL,
    update_time         BIGINT       NOT NULL,
    create_user         VARCHAR(50)  NOT NULL,
    update_user         VARCHAR(50)  NOT NULL,
    UNIQUE KEY uk_org_config (organization_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '组织企微同步配置';

CREATE TABLE IF NOT EXISTS org_sync_log (
    id                  VARCHAR(50)  NOT NULL PRIMARY KEY,
    organization_id     VARCHAR(50)  NOT NULL,
    sync_mode           VARCHAR(20)  NOT NULL COMMENT 'MANUAL/SCHEDULE/LOGIN',
    sync_status         VARCHAR(20)  NOT NULL COMMENT 'SUCCESS/PARTIAL/FAILED',
    dept_total          INT          DEFAULT 0,
    dept_success        INT          DEFAULT 0,
    dept_failed         INT          DEFAULT 0,
    user_total          INT          DEFAULT 0,
    user_success        INT          DEFAULT 0,
    user_failed         INT          DEFAULT 0,
    duration_ms         BIGINT       DEFAULT 0,
    error_message       TEXT,
    create_time         BIGINT       NOT NULL,
    create_user         VARCHAR(50)  NOT NULL,
    KEY idx_org_time (organization_id, create_time DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '组织同步日志';

-- set innodb lock wait timeout to default
SET SESSION innodb_lock_wait_timeout = DEFAULT;
