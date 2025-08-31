-- Create events table for event store
CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    event_data JSONB NOT NULL,
    metadata JSONB,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uk_events_aggregate_sequence UNIQUE(aggregate_id, sequence_number)
);

-- Create indexes for optimal query performance
CREATE INDEX idx_events_aggregate ON events(aggregate_id, sequence_number);
CREATE INDEX idx_events_timestamp ON events(timestamp);
CREATE INDEX idx_events_type ON events(event_type);
CREATE INDEX idx_events_aggregate_type ON events(aggregate_type);

-- Add comments for documentation
COMMENT ON TABLE events IS 'Event store table containing all domain events';
COMMENT ON COLUMN events.aggregate_id IS 'Unique identifier of the aggregate instance';
COMMENT ON COLUMN events.aggregate_type IS 'Type/class name of the aggregate';
COMMENT ON COLUMN events.sequence_number IS 'Sequential number of the event within the aggregate';
COMMENT ON COLUMN events.event_type IS 'Type/class name of the event';
COMMENT ON COLUMN events.event_data IS 'Serialized event payload in JSON format';
COMMENT ON COLUMN events.metadata IS 'Additional metadata associated with the event';