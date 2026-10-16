-- =============================================================================
-- ScaleLink Database Initialization Script
-- Executed on initial PostgreSQL container startup to establish database boundaries
-- =============================================================================

-- Create isolated database for Java Link Service
SELECT 'CREATE DATABASE link_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'link_db')\gexec

-- Create isolated database for Python Analytics Service
SELECT 'CREATE DATABASE analytics_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'analytics_db')\gexec

-- Grant privileges to the default user
GRANT ALL PRIVILEGES ON DATABASE link_db TO postgres;
GRANT ALL PRIVILEGES ON DATABASE analytics_db TO postgres;
