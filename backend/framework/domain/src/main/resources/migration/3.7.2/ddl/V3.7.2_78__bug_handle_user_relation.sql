CREATE TABLE IF NOT EXISTS bug_handle_user_relation
(
    bug_id         VARCHAR(50)  NOT NULL COMMENT '缺陷 ID',
    project_id     VARCHAR(50)  NOT NULL COMMENT '项目 ID',
    platform       VARCHAR(255) NOT NULL COMMENT '缺陷平台',
    handle_user_id VARCHAR(255) NOT NULL COMMENT '处理人 ID',
    create_time    BIGINT       NOT NULL COMMENT '创建时间',
    PRIMARY KEY (bug_id, handle_user_id),
    INDEX idx_bug_handle_user_project_user (project_id, handle_user_id, bug_id),
    INDEX idx_bug_handle_user_platform_user (platform, handle_user_id, bug_id)
) COMMENT = '缺陷当前处理人关系表';

-- 兼容历史单值、逗号分隔值和 JSON 数组值。处理人 ID 本身不允许包含逗号。
INSERT IGNORE INTO bug_handle_user_relation
    (bug_id, project_id, platform, handle_user_id, create_time)
SELECT b.id,
       b.project_id,
       b.platform,
       TRIM(parts.handle_user_id),
       COALESCE(b.update_time, b.create_time, UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000)
FROM bug b
JOIN JSON_TABLE(
    CONCAT(
        '["',
        REPLACE(
            REPLACE(
                REPLACE(
                    REPLACE(TRIM(b.handle_user), '[', ''),
                    ']', ''
                ),
                '"', ''
            ),
            ',', '","'
        ),
        '"]'
    ),
    '$[*]' COLUMNS (handle_user_id VARCHAR(255) PATH '$')
) parts
WHERE b.handle_user IS NOT NULL
  AND TRIM(b.handle_user) <> ''
  AND TRIM(parts.handle_user_id) <> '';
