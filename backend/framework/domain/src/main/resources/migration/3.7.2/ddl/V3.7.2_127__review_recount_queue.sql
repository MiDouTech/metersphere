CREATE TABLE functional_review_recount (
 review_id VARCHAR(50) NOT NULL PRIMARY KEY,
 state VARCHAR(20) NOT NULL DEFAULT 'PENDING',attempts INT NOT NULL DEFAULT 0,
 next_retry_at BIGINT NOT NULL,update_time BIGINT NOT NULL,
 KEY idx_review_recount_due(state,next_retry_at)
);
