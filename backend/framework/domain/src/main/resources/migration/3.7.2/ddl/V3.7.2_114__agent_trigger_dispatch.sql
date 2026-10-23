CREATE TABLE ai_trigger_dispatch (
 trigger_id VARCHAR(50) NOT NULL,
 scheduled_at BIGINT NOT NULL,
 trigger_json MEDIUMTEXT NOT NULL,
 state VARCHAR(24) NOT NULL DEFAULT 'PENDING',
 attempts INT NOT NULL DEFAULT 0,
 next_attempt BIGINT NOT NULL,
 PRIMARY KEY(trigger_id,scheduled_at),
 KEY ix_trigger_dispatch(state,next_attempt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
