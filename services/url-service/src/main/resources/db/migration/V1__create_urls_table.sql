-- =============================================================================
-- V1: Create URLs Table & Performance Indexes
-- Flyway Migration for link_db
-- =============================================================================

CREATE TABLE IF NOT EXISTS urls (
    id BIGINT PRIMARY KEY,
    short_code VARCHAR(32) NOT NULL UNIQUE,
    long_url TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    is_custom_alias BOOLEAN NOT NULL DEFAULT FALSE
);

-- B-Tree Index for rapid short_code lookups on cache misses
CREATE UNIQUE INDEX IF NOT EXISTS idx_urls_short_code ON urls(short_code);

-- Index for scheduled cleanup of expired links
CREATE INDEX IF NOT EXISTS idx_urls_expires_at ON urls(expires_at) WHERE expires_at IS NOT NULL;
