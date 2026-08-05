SET SESSION innodb_lock_wait_timeout = 7200;

CREATE TABLE IF NOT EXISTS functional_case_ai_generation (
    id              VARCHAR(50)  NOT NULL COMMENT '主键',
    project_id      VARCHAR(50)  NOT NULL COMMENT '项目 ID',
    conversation_id VARCHAR(50)           DEFAULT NULL COMMENT 'AI 会话 ID',
    model_source_id VARCHAR(50)           DEFAULT NULL COMMENT 'AI 模型来源 ID',
    prompt          LONGTEXT              DEFAULT NULL COMMENT '生成提示词或用户输入摘要',
    status          VARCHAR(32)  NOT NULL COMMENT '生成任务状态',
    token_usage     BIGINT                DEFAULT NULL COMMENT 'Token 用量',
    duration_ms     BIGINT                DEFAULT NULL COMMENT '执行耗时，毫秒',
    error_message   LONGTEXT              DEFAULT NULL COMMENT '错误信息',
    create_user     VARCHAR(50)  NOT NULL COMMENT '创建人',
    create_time     BIGINT       NOT NULL COMMENT '创建时间',
    update_time     BIGINT       NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_fc_ai_generation_project_user (project_id, create_user),
    KEY idx_fc_ai_generation_conversation (conversation_id),
    KEY idx_fc_ai_generation_status (status),
    KEY idx_fc_ai_generation_create_time (create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '功能用例 AI 生成任务';

CREATE TABLE IF NOT EXISTS ai_source_document (
    id                 VARCHAR(50)  NOT NULL COMMENT '主键',
    project_id         VARCHAR(50)  NOT NULL COMMENT '项目 ID',
    conversation_id    VARCHAR(50)           DEFAULT NULL COMMENT 'AI 会话 ID',
    file_id            VARCHAR(50)  NOT NULL COMMENT '文件服务 ID',
    original_name      VARCHAR(255) NOT NULL COMMENT '原始文件名',
    mime_type          VARCHAR(128)          DEFAULT NULL COMMENT 'MIME 类型',
    file_size          BIGINT                DEFAULT NULL COMMENT '文件大小',
    sha256             VARCHAR(64)           DEFAULT NULL COMMENT '文件 SHA-256',
    duplicate          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否重复文件',
    duplicate_source_document_id VARCHAR(50) DEFAULT NULL COMMENT '重复来源文档 ID',
    parse_status       VARCHAR(32)  NOT NULL COMMENT '解析状态',
    parsed_result_path VARCHAR(512)          DEFAULT NULL COMMENT '解析结果存储路径',
    parser_type        VARCHAR(64)           DEFAULT NULL COMMENT '解析器类型',
    summary            LONGTEXT              DEFAULT NULL COMMENT '解析摘要',
    section_index      LONGTEXT              DEFAULT NULL COMMENT '章节索引 JSON',
    error_message      LONGTEXT              DEFAULT NULL COMMENT '错误信息',
    create_user        VARCHAR(50)  NOT NULL COMMENT '创建人',
    create_time        BIGINT       NOT NULL COMMENT '创建时间',
    update_time        BIGINT       NOT NULL COMMENT '更新时间',
    deleted            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (id),
    KEY idx_ai_source_document_project_user (project_id, create_user),
    KEY idx_ai_source_document_conversation (conversation_id),
    KEY idx_ai_source_document_file (file_id),
    KEY idx_ai_source_document_sha256 (sha256),
    KEY idx_ai_source_document_project_sha256 (project_id, create_user, sha256),
    KEY idx_ai_source_document_parse_status (parse_status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = 'AI 生成用例来源文档';

CREATE TABLE IF NOT EXISTS functional_case_ai_draft (
    id                 VARCHAR(50)  NOT NULL COMMENT '主键',
    generation_id      VARCHAR(50)  NOT NULL COMMENT 'AI 生成任务 ID',
    source_document_id VARCHAR(50)           DEFAULT NULL COMMENT '来源文档 ID',
    project_id         VARCHAR(50)  NOT NULL COMMENT '项目 ID',
    module_id          VARCHAR(50)           DEFAULT NULL COMMENT '模块 ID',
    template_id        VARCHAR(50)           DEFAULT NULL COMMENT '模板 ID',
    name               VARCHAR(255) NOT NULL COMMENT '用例名称',
    case_level         VARCHAR(64)           DEFAULT NULL COMMENT '用例等级',
    edit_type          VARCHAR(50)           DEFAULT NULL COMMENT '编辑模式',
    prerequisite       LONGTEXT              DEFAULT NULL COMMENT '前置条件',
    steps              LONGTEXT              DEFAULT NULL COMMENT '步骤 JSON',
    expected_result    LONGTEXT              DEFAULT NULL COMMENT '预期结果',
    tags               LONGTEXT              DEFAULT NULL COMMENT '标签 JSON',
    custom_fields      LONGTEXT              DEFAULT NULL COMMENT '自定义字段 JSON',
    validation_message LONGTEXT              DEFAULT NULL COMMENT '校验提示信息',
    fingerprint        VARCHAR(64)           DEFAULT NULL COMMENT '重复检测指纹',
    duplicate          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否疑似重复',
    validation_status  VARCHAR(32)  NOT NULL COMMENT '校验状态',
    draft_status       VARCHAR(32)  NOT NULL COMMENT '草稿状态',
    formal_case_id     VARCHAR(50)           DEFAULT NULL COMMENT '正式功能用例 ID',
    deleted            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否删除',
    version            INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_user        VARCHAR(50)  NOT NULL COMMENT '创建人',
    create_time        BIGINT       NOT NULL COMMENT '创建时间',
    update_time        BIGINT       NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_fc_ai_draft_generation (generation_id),
    KEY idx_fc_ai_draft_source_document (source_document_id),
    KEY idx_fc_ai_draft_project_user (project_id, create_user),
    KEY idx_fc_ai_draft_project_user_deleted (project_id, create_user, deleted),
    KEY idx_fc_ai_draft_formal_case (formal_case_id),
    KEY idx_fc_ai_draft_fingerprint (project_id, fingerprint),
    KEY idx_fc_ai_draft_status (draft_status, validation_status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '功能用例 AI 生成草稿';

SET SESSION innodb_lock_wait_timeout = DEFAULT;
