-- Create user projection table (example projection for main application)
CREATE TABLE user_projection (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create indexes for optimal query performance
CREATE INDEX idx_user_projection_email ON user_projection(email);
CREATE INDEX idx_user_projection_status ON user_projection(status);
CREATE INDEX idx_user_projection_created_at ON user_projection(created_at);

-- Create token store table for Axon tracking tokens
CREATE TABLE token_entry (
    processor_name VARCHAR(255) NOT NULL,
    segment INTEGER NOT NULL,
    token BYTEA,
    token_type VARCHAR(255),
    timestamp VARCHAR(255),
    owner VARCHAR(255),
    PRIMARY KEY (processor_name, segment)
);

-- Create saga store table for Axon sagas
CREATE TABLE saga_entry (
    saga_id VARCHAR(255) NOT NULL,
    saga_type VARCHAR(255),
    revision VARCHAR(255),
    serialized_saga BYTEA,
    PRIMARY KEY (saga_id)
);

CREATE TABLE association_value_entry (
    id BIGSERIAL PRIMARY KEY,
    association_key VARCHAR(255) NOT NULL,
    association_value VARCHAR(255),
    saga_type VARCHAR(255) NOT NULL,
    saga_id VARCHAR(255) NOT NULL
);

CREATE INDEX idx_association_value_entry ON association_value_entry(saga_type, association_key, association_value);
CREATE INDEX idx_association_value_entry_saga_id ON association_value_entry(saga_id);

-- Add comments for documentation
COMMENT ON TABLE user_projection IS 'Read model projection for user data';
COMMENT ON TABLE token_entry IS 'Axon tracking token storage for event processors';
COMMENT ON TABLE saga_entry IS 'Axon saga instance storage';
COMMENT ON TABLE association_value_entry IS 'Axon saga association values for saga lookup';