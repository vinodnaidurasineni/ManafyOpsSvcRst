-- =====================================================================
-- V16 — Helper tags (resident "tag your helper" leads; ported from ManafySvcRst).
-- Backs the admin Helper Tags screen. Matches
-- com.manafy.ops.maidapp.entity.HelperTag exactly (ddl-auto=validate).
-- =====================================================================
CREATE TABLE IF NOT EXISTS maidapp_helper_tag (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    helper_name VARCHAR(150),
    helper_mobile VARCHAR(30),
    customer_name VARCHAR(150),
    customer_mobile VARCHAR(30),
    apartment_name VARCHAR(200),
    flat_number VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);
CREATE INDEX IF NOT EXISTS idx_mhelpertag_status ON maidapp_helper_tag (status);
