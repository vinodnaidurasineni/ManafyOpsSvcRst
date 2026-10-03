-- =====================================================================
-- V15 — Helper Tag (ManafyOps compatibility).
--
-- A "helper tag" is a lead: a resident flags an existing household helper
-- (maid/cook/etc.) so an ops user can follow up and onboard them into the
-- managed workforce. Backs the ported admin "Helper Tags" screen
-- (/api/v1/admin/helper-tags).
--
-- Opaque cross-service references only (community_customer_id / _apartment_id),
-- never FKs — matching V11/V12/V14 conventions. Dialect-neutral (H2 + Postgres),
-- application-generated UUID pk, audit/soft-delete/version columns.
-- =====================================================================

CREATE TABLE helper_tag (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,

    helper_name VARCHAR(150) NOT NULL,
    helper_mobile VARCHAR(20),
    helper_type VARCHAR(40),
    flat_number VARCHAR(60),
    apartment_name VARCHAR(200),

    community_apartment_id VARCHAR(64),
    community_customer_id VARCHAR(64),
    customer_name VARCHAR(150),
    customer_mobile VARCHAR(20),

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes VARCHAR(500),

    CONSTRAINT ck_helper_tag_status CHECK (status IN ('PENDING','CONTACTED','ONBOARDED','DISMISSED'))
);

CREATE INDEX idx_helper_tag_status ON helper_tag (status);
CREATE INDEX idx_helper_tag_created_at ON helper_tag (created_at);
