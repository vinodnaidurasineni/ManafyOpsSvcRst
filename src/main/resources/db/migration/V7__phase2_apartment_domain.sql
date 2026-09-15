-- =====================================================================
-- V7 — Phase 2: Apartment onboarding & management domain.
--
-- Dialect-neutral (H2 dev/test + PostgreSQL prod), matching Phase 1 conventions
-- (owner decisions Q-F1/Q-F2): application-generated UUIDs, VARCHAR + CHECK for
-- status/enum values, TIMESTAMP (project convention pending TIMESTAMPTZ decision —
-- see docs/05 discrepancy #4; new tables follow the current TIMESTAMP convention
-- consistently rather than introducing a third variant), BIGINT version for
-- optimistic locking, soft-delete columns on master data.
--
-- FK ordering: apartment → (buildings → units), facilities, contacts, documents,
-- onboarding (1:1) → checklist + status history, apartment_service.
-- =====================================================================

-- ---------------------------------------------------------------------
-- apartment — core community/apartment master (Artifact #1 §3).
-- Field Officer ownership is AREA-derived (area_field_officer); the
-- assigned_field_officer_id column is an OPTIONAL override only (DD-46) and never
-- grants cross-area access.
-- ---------------------------------------------------------------------
CREATE TABLE apartment (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    legal_name VARCHAR(200),
    region_id UUID NOT NULL,
    area_id UUID NOT NULL,
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(80) NOT NULL DEFAULT 'India',
    pincode VARCHAR(20),
    latitude NUMERIC(9,6),
    longitude NUMERIC(9,6),
    status VARCHAR(30) NOT NULL DEFAULT 'PROSPECT',
    management_company VARCHAR(200),
    timezone VARCHAR(60) NOT NULL DEFAULT 'Asia/Kolkata',
    assigned_field_officer_id UUID,
    activated_at TIMESTAMP,
    suspended_at TIMESTAMP,
    CONSTRAINT uk_apartment_code UNIQUE (code),
    CONSTRAINT fk_apartment_region FOREIGN KEY (region_id) REFERENCES region (id),
    CONSTRAINT fk_apartment_area FOREIGN KEY (area_id) REFERENCES area (id),
    CONSTRAINT fk_apartment_fo FOREIGN KEY (assigned_field_officer_id) REFERENCES ops_user (id),
    CONSTRAINT ck_apartment_status CHECK (status IN
        ('PROSPECT','ONBOARDING','PENDING_VERIFICATION','READY_FOR_ACTIVATION',
         'ACTIVE','SUSPENDED','INACTIVE','TERMINATED'))
);
CREATE INDEX idx_apartment_area ON apartment (area_id);
CREATE INDEX idx_apartment_region ON apartment (region_id);
CREATE INDEX idx_apartment_status ON apartment (status);
CREATE INDEX idx_apartment_fo ON apartment (assigned_field_officer_id);

-- ---------------------------------------------------------------------
-- apartment_building
-- ---------------------------------------------------------------------
CREATE TABLE apartment_building (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    apartment_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    floors INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_building_apartment FOREIGN KEY (apartment_id) REFERENCES apartment (id),
    CONSTRAINT uk_building_apartment_name UNIQUE (apartment_id, name),
    CONSTRAINT ck_building_status CHECK (status IN ('ACTIVE','INACTIVE'))
);
CREATE INDEX idx_building_apartment ON apartment_building (apartment_id);

-- ---------------------------------------------------------------------
-- apartment_unit (belongs to building; apartment_id denormalized for scope)
-- ---------------------------------------------------------------------
CREATE TABLE apartment_unit (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    building_id UUID NOT NULL,
    apartment_id UUID NOT NULL,
    unit_number VARCHAR(50) NOT NULL,
    floor INTEGER,
    unit_type VARCHAR(40),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_unit_building FOREIGN KEY (building_id) REFERENCES apartment_building (id),
    CONSTRAINT fk_unit_apartment FOREIGN KEY (apartment_id) REFERENCES apartment (id),
    CONSTRAINT uk_unit_building_number UNIQUE (building_id, unit_number),
    CONSTRAINT ck_unit_status CHECK (status IN ('ACTIVE','INACTIVE'))
);
CREATE INDEX idx_unit_building ON apartment_unit (building_id);
CREATE INDEX idx_unit_apartment ON apartment_unit (apartment_id);

-- ---------------------------------------------------------------------
-- apartment_facility (configurable name; scope APARTMENT/BUILDING/COMMON_AREA)
-- ---------------------------------------------------------------------
CREATE TABLE apartment_facility (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    apartment_id UUID NOT NULL,
    building_id UUID,
    facility_scope VARCHAR(20) NOT NULL DEFAULT 'APARTMENT',
    name VARCHAR(100) NOT NULL,
    metadata VARCHAR(2000),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_facility_apartment FOREIGN KEY (apartment_id) REFERENCES apartment (id),
    CONSTRAINT fk_facility_building FOREIGN KEY (building_id) REFERENCES apartment_building (id),
    CONSTRAINT ck_facility_scope CHECK (facility_scope IN ('APARTMENT','BUILDING','COMMON_AREA')),
    CONSTRAINT ck_facility_status CHECK (status IN ('ACTIVE','INACTIVE'))
);
CREATE INDEX idx_facility_apartment ON apartment_facility (apartment_id);

-- ---------------------------------------------------------------------
-- apartment_contact (PII: phone/email — protected by authorization + masking)
-- ---------------------------------------------------------------------
CREATE TABLE apartment_contact (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    apartment_id UUID NOT NULL,
    contact_type VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(255),
    role_title VARCHAR(100),
    CONSTRAINT fk_contact_apartment FOREIGN KEY (apartment_id) REFERENCES apartment (id),
    CONSTRAINT ck_contact_type CHECK (contact_type IN ('MANAGEMENT','EMERGENCY','BILLING','OTHER'))
);
CREATE INDEX idx_contact_apartment ON apartment_contact (apartment_id);

-- ---------------------------------------------------------------------
-- apartment_document (metadata only; file bytes live in object storage —
-- reuses the Phase 1 file-metadata approach; no second storage abstraction).
-- object_key references the storage object; no file bytes in the DB.
-- ---------------------------------------------------------------------
CREATE TABLE apartment_document (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    apartment_id UUID NOT NULL,
    doc_type VARCHAR(50) NOT NULL,
    file_name VARCHAR(255),
    object_key VARCHAR(500),
    content_type VARCHAR(100),
    size_bytes BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    verified_by UUID,
    verified_at TIMESTAMP,
    rejection_reason VARCHAR(500),
    CONSTRAINT fk_document_apartment FOREIGN KEY (apartment_id) REFERENCES apartment (id),
    CONSTRAINT fk_document_verified_by FOREIGN KEY (verified_by) REFERENCES ops_user (id),
    CONSTRAINT ck_document_status CHECK (status IN ('UPLOADED','UNDER_REVIEW','VERIFIED','REJECTED','EXPIRED'))
);
CREATE INDEX idx_document_apartment ON apartment_document (apartment_id);

-- ---------------------------------------------------------------------
-- apartment_onboarding (1:1 with apartment). Lifecycle status machine.
-- ---------------------------------------------------------------------
CREATE TABLE apartment_onboarding (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    apartment_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    started_by UUID,
    submitted_at TIMESTAMP,
    verified_by UUID,
    verified_at TIMESTAMP,
    rejected_by UUID,
    rejected_at TIMESTAMP,
    rejection_reason VARCHAR(1000),
    resubmitted_at TIMESTAMP,
    CONSTRAINT uk_onboarding_apartment UNIQUE (apartment_id),
    CONSTRAINT fk_onboarding_apartment FOREIGN KEY (apartment_id) REFERENCES apartment (id),
    CONSTRAINT fk_onboarding_started_by FOREIGN KEY (started_by) REFERENCES ops_user (id),
    CONSTRAINT fk_onboarding_verified_by FOREIGN KEY (verified_by) REFERENCES ops_user (id),
    CONSTRAINT fk_onboarding_rejected_by FOREIGN KEY (rejected_by) REFERENCES ops_user (id),
    CONSTRAINT ck_onboarding_status CHECK (status IN
        ('DRAFT','SUBMITTED','UNDER_REVIEW','REJECTED','RESUBMITTED','VERIFIED','ACTIVE','SUSPENDED'))
);
CREATE INDEX idx_onboarding_status ON apartment_onboarding (status);

-- ---------------------------------------------------------------------
-- apartment_onboarding_checklist — persisted checklist items gating activation.
-- ---------------------------------------------------------------------
CREATE TABLE apartment_onboarding_checklist (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    onboarding_id UUID NOT NULL,
    item_key VARCHAR(50) NOT NULL,
    label VARCHAR(150) NOT NULL,
    is_mandatory BOOLEAN NOT NULL DEFAULT TRUE,
    is_complete BOOLEAN NOT NULL DEFAULT FALSE,
    completed_by UUID,
    completed_at TIMESTAMP,
    CONSTRAINT fk_checklist_onboarding FOREIGN KEY (onboarding_id) REFERENCES apartment_onboarding (id),
    CONSTRAINT fk_checklist_completed_by FOREIGN KEY (completed_by) REFERENCES ops_user (id),
    CONSTRAINT uk_checklist_onboarding_item UNIQUE (onboarding_id, item_key)
);
CREATE INDEX idx_checklist_onboarding ON apartment_onboarding_checklist (onboarding_id);

-- ---------------------------------------------------------------------
-- apartment_onboarding_status_history — insert-only lifecycle history (§50).
-- No version/soft-delete; append-only.
-- ---------------------------------------------------------------------
CREATE TABLE apartment_onboarding_status_history (
    id UUID NOT NULL PRIMARY KEY,
    onboarding_id UUID NOT NULL,
    apartment_id UUID NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    changed_by UUID,
    reason VARCHAR(1000),
    created_at TIMESTAMP,
    CONSTRAINT fk_onb_hist_onboarding FOREIGN KEY (onboarding_id) REFERENCES apartment_onboarding (id)
);
CREATE INDEX idx_onb_hist_onboarding ON apartment_onboarding_status_history (onboarding_id);

-- ---------------------------------------------------------------------
-- apartment_service — which catalog services an apartment enables (§26).
-- Phase 2 establishes enablement only; NO service-request/dispatch lifecycle.
-- service_id is a soft reference (no service_catalog table until a later phase);
-- kept as UUID + code for forward compatibility, no FK yet.
-- ---------------------------------------------------------------------
CREATE TABLE apartment_service (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    apartment_id UUID NOT NULL,
    service_code VARCHAR(60) NOT NULL,
    service_name VARCHAR(150),
    state VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    CONSTRAINT fk_apt_service_apartment FOREIGN KEY (apartment_id) REFERENCES apartment (id),
    CONSTRAINT uk_apt_service UNIQUE (apartment_id, service_code),
    CONSTRAINT ck_apt_service_state CHECK (state IN ('ENABLED','DISABLED'))
);
CREATE INDEX idx_apt_service_apartment ON apartment_service (apartment_id);
