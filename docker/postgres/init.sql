-- Create databases for main application and custom axon server
CREATE DATABASE axon_main;
CREATE DATABASE axon_server;

-- Grant permissions
GRANT ALL PRIVILEGES ON DATABASE axon_main TO axon_user;
GRANT ALL PRIVILEGES ON DATABASE axon_server TO axon_user;