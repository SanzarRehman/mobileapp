-- Create Axon Framework token store tables for tracking event processors
-- This table stores the tracking tokens for event processors to maintain their position
-- in the event stream

CREATE TABLE IF NOT EXISTS token_entry (
    processor_name VARCHAR(255) NOT NULL,
    segment INTEGER NOT NULL,
    token BYTEA,
    token_type VARCHAR(255),
    timestamp VARCHAR(255),
    PRIMARY KEY (processor_name, segment)
);

-- Create index for better performance on token queries
CREATE INDEX IF NOT EXISTS idx_token_entry_processor ON token_entry(processor_name);

-- Create saga entry tables for Axon Framework saga management
CREATE TABLE IF NOT EXISTS saga_entry (
    saga_id VARCHAR(255) NOT NULL,
    saga_type VARCHAR(255),
    revision VARCHAR(255),
    serialized_saga BYTEA,
    PRIMARY KEY (saga_id)
);

-- Create association value entry table for saga associations
CREATE TABLE IF NOT EXISTS association_value_entry (
    id BIGSERIAL PRIMARY KEY,
    association_key VARCHAR(255),
    association_value VARCHAR(255),
    saga_type VARCHAR(255),
    saga_id VARCHAR(255)
);

-- Create indexes for better performance on saga association queries
CREATE INDEX IF NOT EXISTS idx_association_value_entry_key_value ON association_value_entry(association_key, association_value);
CREATE INDEX IF NOT EXISTS idx_association_value_entry_saga_id ON association_value_entry(saga_id);
CREATE INDEX IF NOT EXISTS idx_association_value_entry_saga_type ON association_value_entry(saga_type);

-- Create domain event entry table for Axon Framework event store
CREATE TABLE IF NOT EXISTS domain_event_entry (
    global_index BIGSERIAL,
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    type VARCHAR(255),
    event_identifier VARCHAR(255) NOT NULL,
    payload_type VARCHAR(255),
    payload_revision VARCHAR(255),
    payload BYTEA,
    meta_data BYTEA,
    time_stamp VARCHAR(255),
    PRIMARY KEY (aggregate_identifier, sequence_number)
);

-- Create unique constraint and indexes for domain event entry
CREATE UNIQUE INDEX IF NOT EXISTS uk_domain_event_entry_global_index ON domain_event_entry(global_index);
CREATE INDEX IF NOT EXISTS idx_domain_event_entry_time_stamp ON domain_event_entry(time_stamp);
CREATE INDEX IF NOT EXISTS idx_domain_event_entry_event_identifier ON domain_event_entry(event_identifier);

-- Create snapshot event entry table for Axon Framework snapshots
CREATE TABLE IF NOT EXISTS snapshot_event_entry (
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    type VARCHAR(255),
    event_identifier VARCHAR(255) NOT NULL,
    payload_type VARCHAR(255),
    payload_revision VARCHAR(255),
    payload BYTEA,
    meta_data BYTEA,
    time_stamp VARCHAR(255),
    PRIMARY KEY (aggregate_identifier, sequence_number)
);

-- Create indexes for snapshot event entry
CREATE INDEX IF NOT EXISTS idx_snapshot_event_entry_time_stamp ON snapshot_event_entry(time_stamp);
CREATE INDEX IF NOT EXISTS idx_snapshot_event_entry_event_identifier ON snapshot_event_entry(event_identifier);