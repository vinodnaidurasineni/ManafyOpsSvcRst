-- =====================================================================
-- V3 — Foundation: organization (region, area) + authoritative area↔FO.
--
-- C-1 / DD-20: area_field_officer is the SINGLE SOURCE OF TRUTH for area↔Field
-- Officer ownership. There are deliberately NO primary/secondary field-officer
-- columns on `area`. Designation lives on area_field_officer, with effective
-- dating for history.
-- =====================================================================

CREATE TABLE region (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(80) NOT NULL DEFAULT 'India',
    timezone VARCHAR(60) NOT NULL DEFAULT 'Asia/Kolkata',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_region_code UNIQUE (code),
    CONSTRAINT ck_region_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE area (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    region_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    city VARCHAR(100),
    state VARCHAR(100),
    postal_codes VARCHAR(500),
    geo_boundary VARCHAR,
    timezone VARCHAR(60) NOT NULL DEFAULT 'Asia/Kolkata',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_area_code UNIQUE (code),
    CONSTRAINT fk_area_region FOREIGN KEY (region_id) REFERENCES region (id),
    CONSTRAINT ck_area_status CHECK (status IN ('ACTIVE','INACTIVE'))
);
CREATE INDEX idx_area_region ON area (region_id);

-- Authoritative area↔FO assignment (C-1/DD-20). field_officer_id references
-- ops_user (a Field Officer is an internal user).
CREATE TABLE area_field_officer (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    area_id UUID NOT NULL,
    field_officer_id UUID NOT NULL,
    designation VARCHAR(20) NOT NULL DEFAULT 'PRIMARY',
    effective_from TIMESTAMP NOT NULL,
    effective_to TIMESTAMP,
    assigned_by UUID,
    CONSTRAINT fk_afo_area FOREIGN KEY (area_id) REFERENCES area (id),
    CONSTRAINT fk_afo_officer FOREIGN KEY (field_officer_id) REFERENCES ops_user (id),
    CONSTRAINT ck_afo_designation CHECK (designation IN ('PRIMARY','SECONDARY'))
);
CREATE INDEX idx_afo_area ON area_field_officer (area_id);
CREATE INDEX idx_afo_officer ON area_field_officer (field_officer_id);
