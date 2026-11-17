-- Notification budgets and receipts are independent of member synchronization.
ALTER TABLE plan_review_relation ADD source_actor VARCHAR(50) NULL;
CREATE TABLE plan_review_fault (
 id VARCHAR(50) NOT NULL PRIMARY KEY,
 project_id VARCHAR(50) NOT NULL,
 relation_id VARCHAR(50) NOT NULL,
 generation VARCHAR(50) NOT NULL,
 reason VARCHAR(64) NOT NULL,
 fault_round BIGINT NOT NULL,
 active_reason VARCHAR(64) NULL,
 detected_at BIGINT NOT NULL,
 recovered_at BIGINT NULL,
 ended_at BIGINT NULL,
 UNIQUE KEY uk_active_fault(relation_id,generation,active_reason),
 UNIQUE KEY uk_fault_round(relation_id,generation,fault_round)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

ALTER TABLE plan_review_notice ADD eligibility VARCHAR(64) NULL;
ALTER TABLE plan_review_notice ADD inbox_id BIGINT NULL;

CREATE TABLE plan_review_notice_preview (
 id VARCHAR(50) NOT NULL PRIMARY KEY,
 project_id VARCHAR(50) NOT NULL,
 fault_id VARCHAR(50) NOT NULL,
 actor VARCHAR(50) NOT NULL,
 fingerprint VARCHAR(64) NOT NULL,
 expires_at BIGINT NOT NULL,
 consumed_at BIGINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
