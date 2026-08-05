SET SESSION innodb_lock_wait_timeout = 7200;

ALTER TABLE functional_case_ai_draft
    ADD COLUMN source_references LONGTEXT NULL COMMENT '来源引用 JSON' AFTER custom_fields;

SET SESSION innodb_lock_wait_timeout = DEFAULT;
