-- Add version columns for optimistic locking

-- Add version column to events table
ALTER TABLE events ADD COLUMN version BIGINT DEFAULT 0;

-- Add version column to snapshots table  
ALTER TABLE snapshots ADD COLUMN version BIGINT DEFAULT 0;

-- Create index on version columns for performance
CREATE INDEX idx_events_version ON events(version);
CREATE INDEX idx_snapshots_version ON snapshots(version);

-- Update existing records to have version 0
UPDATE events SET version = 0 WHERE version IS NULL;
UPDATE snapshots SET version = 0 WHERE version IS NULL;

-- Make version columns NOT NULL
ALTER TABLE events ALTER COLUMN version SET NOT NULL;
ALTER TABLE snapshots ALTER COLUMN version SET NOT NULL;