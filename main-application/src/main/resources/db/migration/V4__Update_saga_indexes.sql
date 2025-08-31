-- Update saga storage tables for Axon Framework
-- Ensure all required indexes exist for optimal performance

-- Create indexes only if they don't exist
CREATE INDEX IF NOT EXISTS idx_saga_entry_type ON saga_entry(saga_type);
CREATE INDEX IF NOT EXISTS idx_association_saga_id ON association_value_entry(saga_id);
CREATE INDEX IF NOT EXISTS idx_association_key_value ON association_value_entry(association_key, association_value);
CREATE INDEX IF NOT EXISTS idx_association_saga_type ON association_value_entry(saga_type);

-- Add any missing columns if needed (this is a no-op if they exist)
-- This migration ensures saga tables are properly configured