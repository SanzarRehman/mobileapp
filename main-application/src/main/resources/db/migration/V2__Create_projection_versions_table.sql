-- Create projection versions table for tracking projection schema versions
CREATE TABLE projection_versions (
    projection_name VARCHAR(255) PRIMARY KEY,
    version INTEGER NOT NULL,
    description VARCHAR(500),
    applied_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    migration_script VARCHAR(1000)
);

-- Create index for performance
CREATE INDEX idx_projection_versions_applied_at ON projection_versions(applied_at);

-- Add comments for documentation
COMMENT ON TABLE projection_versions IS 'Tracks projection schema versions and migrations';
COMMENT ON COLUMN projection_versions.projection_name IS 'Name of the projection (e.g., user-projections)';
COMMENT ON COLUMN projection_versions.version IS 'Current version number of the projection';
COMMENT ON COLUMN projection_versions.description IS 'Description of what changed in this version';
COMMENT ON COLUMN projection_versions.applied_at IS 'When this version was applied';
COMMENT ON COLUMN projection_versions.migration_script IS 'SQL script that was executed for this migration';

-- Initialize user projection version
INSERT INTO projection_versions (projection_name, version, description, migration_script)
VALUES ('user-projections', 1, 'Initial user projection schema', 'Initial schema created in V1__Create_projection_tables.sql');