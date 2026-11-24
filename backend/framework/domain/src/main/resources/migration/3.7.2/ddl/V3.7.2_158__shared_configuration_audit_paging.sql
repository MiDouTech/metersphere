-- Bound audit pagination to a configuration and deterministic timestamp/id order.
CREATE INDEX idx_shared_configuration_audit_page ON shared_configuration_audit(configuration_id, create_time, id);
