-- Preserve historical evidence without reactivating the retired execution protocol.
CREATE TABLE historical_evidence_retention (
 kind VARCHAR(16) COLLATE utf8mb4_bin NOT NULL,
 evidence_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 owner_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 reason VARCHAR(32) NOT NULL,
 create_time BIGINT NOT NULL,
 PRIMARY KEY(kind,evidence_id,owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO historical_evidence_retention(kind,evidence_id,owner_id,reason,create_time)
SELECT kind,evidence_id,owner_id,reason,create_time FROM retired_msp_protocol_evidence_hold;
