CREATE TABLE shared_configuration (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    system_key VARCHAR(128) NOT NULL,
    environment_key VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    current_version INT NOT NULL,
    UNIQUE KEY uk_shared_configuration (project_id,system_key,environment_key,name)
);
CREATE TABLE shared_configuration_version (
    configuration_id VARCHAR(64) NOT NULL,
    version_no INT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    encrypted_value MEDIUMTEXT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    parse_status VARCHAR(24) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    create_time BIGINT NOT NULL,
    PRIMARY KEY (configuration_id,version_no)
);
CREATE TABLE shared_configuration_audit (
    id VARCHAR(64) PRIMARY KEY,
    configuration_id VARCHAR(64) NOT NULL,
    version_no INT NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    create_time BIGINT NOT NULL
);
CREATE TABLE external_executor_configuration (
    session_id VARCHAR(64) NOT NULL,
    configuration_id VARCHAR(64) NOT NULL,
    version_no INT NOT NULL,
    PRIMARY KEY(session_id,configuration_id)
);
INSERT IGNORE INTO user_role_permission (id,role_id,permission_id)
SELECT UUID(),role_id,'SHARED_CONFIGURATION:READ' FROM user_role_permission WHERE permission_id='PROJECT_ENVIRONMENT:READ';
INSERT IGNORE INTO user_role_permission (id,role_id,permission_id)
SELECT UUID(),role_id,'SHARED_CONFIGURATION:WRITE' FROM user_role_permission WHERE permission_id='PROJECT_ENVIRONMENT:READ+UPDATE';
