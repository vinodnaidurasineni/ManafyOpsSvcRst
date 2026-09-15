-- =====================================================================
-- V5 — Foundation: audit log, activity log, system config, feature flags,
-- idempotency (Artifact #1 §8; design DD-09/DD-14/DD-28).
--
-- audit_log and activity_log are APPEND-ONLY (no version, no soft-delete, no
-- updated_at). DB-level immutability of audit_log (REVOKE UPDATE/DELETE) is a
-- PostgreSQL production-profile concern and is NOT applied in the shared/H2 path.
-- =====================================================================

-- Immutable security/compliance audit trail.
CREATE TABLE audit_log (
    id UUID NOT NULL PRIMARY KEY,
    actor_user_id UUID,
    actor_role VARCHAR(100),
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(60),
    resource_id UUID,
    before_state VARCHAR(4000),
    after_state VARCHAR(4000),
    reason VARCHAR(500),
    source VARCHAR(20),
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    correlation_id VARCHAR(64),
    created_at TIMESTAMP
);
CREATE INDEX idx_audit_actor ON audit_log (actor_user_id, created_at);
CREATE INDEX idx_audit_action ON audit_log (action, created_at);
CREATE INDEX idx_audit_resource ON audit_log (resource_type, resource_id);

-- Operational activity timeline (distinct from audit).
CREATE TABLE activity_log (
    id UUID NOT NULL PRIMARY KEY,
    resource_type VARCHAR(60) NOT NULL,
    resource_id UUID NOT NULL,
    actor_user_id UUID,
    event VARCHAR(80) NOT NULL,
    message VARCHAR(500),
    correlation_id VARCHAR(64),
    created_at TIMESTAMP
);
CREATE INDEX idx_activity_resource ON activity_log (resource_type, resource_id);

-- System configuration (sensitive keys require Super Admin to change).
CREATE TABLE system_configuration (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    config_key VARCHAR(100) NOT NULL,
    config_value VARCHAR(4000),
    is_sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_sysconfig_key UNIQUE (config_key)
);

-- Feature flags (NOT an authorization mechanism, DD-28).
CREATE TABLE feature_flag (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    flag_key VARCHAR(100) NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    description VARCHAR(500),
    CONSTRAINT uk_feature_flag_key UNIQUE (flag_key)
);

-- Idempotency keys (spec §67). Append-only claim records.
CREATE TABLE idempotency_key (
    id UUID NOT NULL PRIMARY KEY,
    idem_key VARCHAR(128) NOT NULL,
    endpoint VARCHAR(120) NOT NULL,
    user_id UUID,
    created_at TIMESTAMP,
    CONSTRAINT uk_idempotency_key UNIQUE (idem_key)
);
