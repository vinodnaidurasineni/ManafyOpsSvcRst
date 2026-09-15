-- =====================================================================
-- V1 — Foundation: identity + RBAC core.
--
-- Dialect-neutral SQL (H2 dev/test + PostgreSQL prod) per owner decision Q-F1:
--   * VARCHAR + CHECK for status/enum values (no PostgreSQL-native ENUM types)
--   * application-generated UUIDs (no gen_random_uuid()/pgcrypto defaults)
--   * no CITEXT (case-insensitive handling is at the application layer)
--
-- Authorization model:
--   ops_user -> user_role -> role -> role_permission -> permission
--   ops_user -> user_scope (region/area/apartment/vendor scope)
-- =====================================================================

-- ---------------------------------------------------------------------
-- ops_user — internal Manafy Operations user (Cognito-linked identity).
-- ---------------------------------------------------------------------
CREATE TABLE ops_user (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    cognito_sub VARCHAR(255),
    email VARCHAR(255),
    mobile VARCHAR(30),
    display_name VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_super_admin BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMP,
    CONSTRAINT uk_ops_user_cognito_sub UNIQUE (cognito_sub),
    CONSTRAINT uk_ops_user_email UNIQUE (email),
    CONSTRAINT uk_ops_user_mobile UNIQUE (mobile),
    CONSTRAINT ck_ops_user_status CHECK (status IN ('ACTIVE','DISABLED'))
);
CREATE INDEX idx_ops_user_status ON ops_user (status);

-- ---------------------------------------------------------------------
-- role
-- ---------------------------------------------------------------------
CREATE TABLE role (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    is_system BOOLEAN NOT NULL DEFAULT TRUE,
    assignable_by_min_role VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_role_code UNIQUE (code)
);

-- ---------------------------------------------------------------------
-- permission (DOMAIN_RESOURCE_ACTION)
-- ---------------------------------------------------------------------
CREATE TABLE permission (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(100) NOT NULL,
    domain VARCHAR(50),
    resource VARCHAR(50),
    action VARCHAR(50),
    description VARCHAR(255),
    is_sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_permission_code UNIQUE (code)
);

-- ---------------------------------------------------------------------
-- role_permission
-- ---------------------------------------------------------------------
CREATE TABLE role_permission (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES role (id),
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permission (id)
);
CREATE INDEX idx_rp_role ON role_permission (role_id);
CREATE INDEX idx_rp_permission ON role_permission (permission_id);

-- ---------------------------------------------------------------------
-- user_role
-- ---------------------------------------------------------------------
CREATE TABLE user_role (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    assigned_by UUID,
    assigned_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES ops_user (id),
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES role (id),
    CONSTRAINT ck_user_role_status CHECK (status IN ('ACTIVE','REVOKED'))
);
CREATE INDEX idx_ur_user ON user_role (user_id);
CREATE INDEX idx_ur_role ON user_role (role_id);
