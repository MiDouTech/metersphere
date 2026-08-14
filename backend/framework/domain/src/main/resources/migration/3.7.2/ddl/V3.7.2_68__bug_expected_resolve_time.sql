ALTER TABLE bug
    ADD COLUMN expected_resolve_time BIGINT NULL COMMENT '预计解决时间（毫秒时间戳）' AFTER update_time;
