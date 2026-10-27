CREATE TABLE case_asset_batch_job (
 id VARCHAR(50) NOT NULL PRIMARY KEY, organization_id VARCHAR(50) NOT NULL,
 catalog_id VARCHAR(50) NOT NULL,user_id VARCHAR(50) NOT NULL,
 request_id VARCHAR(64) NOT NULL,request_hash VARCHAR(64) NOT NULL,request_json MEDIUMTEXT NOT NULL,
 create_time BIGINT NOT NULL,update_time BIGINT NOT NULL,
 UNIQUE KEY uk_asset_batch_request(user_id,organization_id,request_id)
);
CREATE TABLE case_asset_batch_item (
 job_id VARCHAR(50) NOT NULL,case_id VARCHAR(50) NOT NULL,expected_update_time BIGINT NOT NULL,
 state VARCHAR(20) NOT NULL DEFAULT 'PENDING',message VARCHAR(1000),update_time BIGINT NOT NULL,
 PRIMARY KEY(job_id,case_id), KEY idx_asset_batch_pending(job_id,state)
);
