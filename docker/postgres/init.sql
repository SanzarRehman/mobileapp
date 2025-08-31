-- Create databases for main application, custom axon server, and keycloak
CREATE DATABASE axon_main;
CREATE DATABASE axon_server;
CREATE DATABASE keycloak;

-- Grant permissions
GRANT ALL PRIVILEGES ON DATABASE axon_main TO axon_user;
GRANT ALL PRIVILEGES ON DATABASE axon_server TO axon_user;
GRANT ALL PRIVILEGES ON DATABASE keycloak TO axon_user;