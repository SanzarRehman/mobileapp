-- Create snapshots table for aggregate snapshots
CREATE TABLE snapshots (
    id BIGSERIAL PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    snapshot_data JSONB NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uk_snapshots_aggregate UNIQUE(aggregate_id)
);

-- Create indexes for optimal query performance
CREATE INDEX idx_snapshots_aggregate_id ON snapshots(aggregate_id);
CREATE INDEX idx_snapshots_aggregate_type ON snapshots(aggregate_type);
CREATE INDEX idx_snapshots_timestamp ON snapshots(timestamp);

-- Add comments for documentation
COMMENT ON TABLE snapshots IS 'Aggregate snapshots table for performance optimization';
COMMENT ON COLUMN snapshots.aggregate_id IS 'Unique identifier of the aggregate instance';
COMMENT ON COLUMN snapshots.aggregate_type IS 'Type/class name of the aggregate';
COMMENT ON COLUMN snapshots.sequence_number IS 'Sequence number at which the snapshot was taken';
COMMENT ON COLUMN snapshots.snapshot_data IS 'Serialized aggregate state in JSON format';