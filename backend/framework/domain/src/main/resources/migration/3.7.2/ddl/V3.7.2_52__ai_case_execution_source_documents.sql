ALTER TABLE ai_case_execution
    ADD COLUMN source_document_ids LONGTEXT DEFAULT NULL COMMENT 'JSON array of validated source document ids' AFTER retry_of_request_id;
