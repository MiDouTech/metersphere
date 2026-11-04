CREATE TABLE plan_review_copy_request (
 id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 project_id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
 request_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
 original_actor VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
 input_hash VARCHAR(64) NOT NULL,
 request_json LONGTEXT NOT NULL,
 create_time BIGINT NOT NULL,
 UNIQUE KEY uk_copy_request(project_id,request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
