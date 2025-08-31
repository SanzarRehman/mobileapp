-- Test schema for H2 database
CREATE TABLE IF NOT EXISTS user_projection (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS saga_entry (
    saga_id VARCHAR(255) NOT NULL,
    saga_type VARCHAR(255) NOT NULL,
    revision VARCHAR(255),
    serialized_saga BLOB,
    PRIMARY KEY (saga_id, saga_type)
);

CREATE TABLE IF NOT EXISTS association_value_entry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    association_key VARCHAR(255) NOT NULL,
    association_value VARCHAR(255) NOT NULL,
    saga_type VARCHAR(255) NOT NULL,
    saga_id VARCHAR(255) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_projection_email ON user_projection(email);
CREATE INDEX IF NOT EXISTS idx_user_projection_status ON user_projection(status);
CREATE INDEX IF NOT EXISTS idx_association_value ON association_value_entry(association_key, association_value);