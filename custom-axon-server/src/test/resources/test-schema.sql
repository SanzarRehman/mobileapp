-- Test schema for H2 database
CREATE TABLE IF NOT EXISTS events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    event_data TEXT NOT NULL,
    metadata TEXT,
    timestamp TIMESTAMP NOT NULL,
    version BIGINT DEFAULT 0,
    UNIQUE(aggregate_id, sequence_number)
);

CREATE TABLE IF NOT EXISTS snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    snapshot_data TEXT NOT NULL,
    metadata TEXT,
    timestamp TIMESTAMP NOT NULL,
    version BIGINT DEFAULT 0,
    UNIQUE(aggregate_id)
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

CREATE INDEX IF NOT EXISTS idx_events_aggregate ON events(aggregate_id);
CREATE INDEX IF NOT EXISTS idx_events_timestamp ON events(timestamp);
CREATE INDEX IF NOT EXISTS idx_snapshots_aggregate ON snapshots(aggregate_id);
CREATE INDEX IF NOT EXISTS idx_association_value ON association_value_entry(association_key, association_value);