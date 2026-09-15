-- =====================================================================
-- V8 — Phase 3: Workforce & HR domain.
--
-- Dialect-neutral (H2 dev/test + PostgreSQL prod), matching Phase 1/2 conventions
-- (Q-F1/Q-F2): application-generated UUIDs, VARCHAR + CHECK for status/enum,
-- TIMESTAMP, BIGINT version, soft-delete columns on master data.
--
-- FK ordering: skill; employee; vendor → technician/helper (technician/helper may
-- reference vendor); vendor_staff → vendor(+technician/helper); workforce_skill →
-- skill; workforce_area → area; certification; workforce_availability;
-- workforce_document; workforce_status_history (append-only).
--
-- Field Officer note (§15): area↔FO ownership remains ONLY in area_field_officer
-- (Phase 1). This migration does NOT create a second FO↔area source of truth. A
-- Field Officer is an ops_user (identity) optionally described by an employee row.
-- =====================================================================

-- ---------------------------------------------------------------------
-- skill — reusable master data (referenced by workforce_skill).
-- ---------------------------------------------------------------------
CREATE TABLE skill (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(150) NOT NULL,
    category VARCHAR(80),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_skill_code UNIQUE (code),
    CONSTRAINT ck_skill_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

-- ---------------------------------------------------------------------
-- employee — internal Manafy staff master (HR-owned). A Field Officer is an
-- employee linked to an ops_user identity (user_id).
-- ---------------------------------------------------------------------
CREATE TABLE employee (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    employee_code VARCHAR(50) NOT NULL,
    user_id UUID,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(30),
    designation VARCHAR(100),
    employee_type VARCHAR(30) NOT NULL DEFAULT 'STAFF',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    CONSTRAINT uk_employee_code UNIQUE (employee_code),
    CONSTRAINT fk_employee_user FOREIGN KEY (user_id) REFERENCES ops_user (id),
    CONSTRAINT ck_employee_type CHECK (employee_type IN ('STAFF','FIELD_OFFICER','MANAGER')),
    CONSTRAINT ck_employee_status CHECK (status IN ('DRAFT','ACTIVE','INACTIVE','SUSPENDED','TERMINATED'))
);
CREATE INDEX idx_employee_user ON employee (user_id);
CREATE INDEX idx_employee_type ON employee (employee_type);

-- ---------------------------------------------------------------------
-- vendor — external vendor organization (referenced by technician/helper/staff).
-- ---------------------------------------------------------------------
CREATE TABLE vendor (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    code VARCHAR(50) NOT NULL,
    legal_name VARCHAR(200) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    registration_no VARCHAR(100),
    email VARCHAR(255),
    phone VARCHAR(30),
    address VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    CONSTRAINT uk_vendor_code UNIQUE (code),
    CONSTRAINT ck_vendor_status CHECK (status IN ('DRAFT','ACTIVE','INACTIVE','SUSPENDED','TERMINATED'))
);
CREATE INDEX idx_vendor_status ON vendor (status);

-- ---------------------------------------------------------------------
-- technician — direct Manafy technician OR vendor-associated (vendor_id).
-- Employment status vs availability are separate axes.
-- ---------------------------------------------------------------------
CREATE TABLE technician (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    code VARCHAR(50) NOT NULL,
    vendor_id UUID,
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(255),
    date_of_birth DATE,
    region_id UUID,
    service_radius_km NUMERIC(6,2),
    max_concurrent_jobs INTEGER NOT NULL DEFAULT 1,
    max_daily_jobs INTEGER,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    availability_status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    CONSTRAINT uk_technician_code UNIQUE (code),
    CONSTRAINT fk_technician_vendor FOREIGN KEY (vendor_id) REFERENCES vendor (id),
    CONSTRAINT fk_technician_region FOREIGN KEY (region_id) REFERENCES region (id),
    CONSTRAINT ck_technician_status CHECK (status IN ('DRAFT','ACTIVE','INACTIVE','SUSPENDED','TERMINATED')),
    CONSTRAINT ck_technician_avail CHECK (availability_status IN ('AVAILABLE','BUSY','OFFLINE','ON_LEAVE'))
);
CREATE INDEX idx_technician_vendor ON technician (vendor_id);
CREATE INDEX idx_technician_status ON technician (status);

-- ---------------------------------------------------------------------
-- helper — Manafy / technician / vendor relationship (explicit).
-- ---------------------------------------------------------------------
CREATE TABLE helper (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(30),
    relationship VARCHAR(20) NOT NULL DEFAULT 'MANAFY',
    technician_id UUID,
    vendor_id UUID,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    CONSTRAINT uk_helper_code UNIQUE (code),
    CONSTRAINT fk_helper_technician FOREIGN KEY (technician_id) REFERENCES technician (id),
    CONSTRAINT fk_helper_vendor FOREIGN KEY (vendor_id) REFERENCES vendor (id),
    CONSTRAINT ck_helper_relationship CHECK (relationship IN ('MANAFY','TECHNICIAN','VENDOR')),
    CONSTRAINT ck_helper_status CHECK (status IN ('DRAFT','ACTIVE','INACTIVE','SUSPENDED','TERMINATED'))
);
CREATE INDEX idx_helper_vendor ON helper (vendor_id);
CREATE INDEX idx_helper_status ON helper (status);

-- ---------------------------------------------------------------------
-- vendor_staff — people associated with a vendor (historical linkage).
-- ---------------------------------------------------------------------
CREATE TABLE vendor_staff (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    vendor_id UUID NOT NULL,
    technician_id UUID,
    helper_id UUID,
    staff_name VARCHAR(150),
    role_title VARCHAR(100),
    joined_at TIMESTAMP,
    left_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_vstaff_vendor FOREIGN KEY (vendor_id) REFERENCES vendor (id),
    CONSTRAINT fk_vstaff_technician FOREIGN KEY (technician_id) REFERENCES technician (id),
    CONSTRAINT fk_vstaff_helper FOREIGN KEY (helper_id) REFERENCES helper (id),
    CONSTRAINT ck_vstaff_status CHECK (status IN ('ACTIVE','INACTIVE'))
);
CREATE INDEX idx_vstaff_vendor ON vendor_staff (vendor_id);

-- ---------------------------------------------------------------------
-- workforce_skill — skill assignment (polymorphic workforce ref, constrained).
-- workforce_kind ∈ TECHNICIAN|HELPER; exactly the matching id is set.
-- ---------------------------------------------------------------------
CREATE TABLE workforce_skill (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    workforce_kind VARCHAR(20) NOT NULL,
    technician_id UUID,
    helper_id UUID,
    skill_id UUID NOT NULL,
    skill_level VARCHAR(20) NOT NULL DEFAULT 'INTERMEDIATE',
    CONSTRAINT fk_wfskill_technician FOREIGN KEY (technician_id) REFERENCES technician (id),
    CONSTRAINT fk_wfskill_helper FOREIGN KEY (helper_id) REFERENCES helper (id),
    CONSTRAINT fk_wfskill_skill FOREIGN KEY (skill_id) REFERENCES skill (id),
    CONSTRAINT ck_wfskill_kind CHECK (workforce_kind IN ('TECHNICIAN','HELPER')),
    CONSTRAINT ck_wfskill_level CHECK (skill_level IN ('BEGINNER','INTERMEDIATE','EXPERT')),
    CONSTRAINT ck_wfskill_ref CHECK (
        (workforce_kind = 'TECHNICIAN' AND technician_id IS NOT NULL AND helper_id IS NULL) OR
        (workforce_kind = 'HELPER'     AND helper_id IS NOT NULL AND technician_id IS NULL))
);
CREATE INDEX idx_wfskill_tech ON workforce_skill (technician_id);
CREATE INDEX idx_wfskill_helper ON workforce_skill (helper_id);
CREATE INDEX idx_wfskill_skill ON workforce_skill (skill_id);
CREATE UNIQUE INDEX uk_wfskill_tech ON workforce_skill (technician_id, skill_id);
CREATE UNIQUE INDEX uk_wfskill_helper ON workforce_skill (helper_id, skill_id);

-- ---------------------------------------------------------------------
-- workforce_area — area coverage (technician or vendor).
-- ---------------------------------------------------------------------
CREATE TABLE workforce_area (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    workforce_kind VARCHAR(20) NOT NULL,
    technician_id UUID,
    helper_id UUID,
    vendor_id UUID,
    area_id UUID NOT NULL,
    CONSTRAINT fk_wfarea_technician FOREIGN KEY (technician_id) REFERENCES technician (id),
    CONSTRAINT fk_wfarea_helper FOREIGN KEY (helper_id) REFERENCES helper (id),
    CONSTRAINT fk_wfarea_vendor FOREIGN KEY (vendor_id) REFERENCES vendor (id),
    CONSTRAINT fk_wfarea_area FOREIGN KEY (area_id) REFERENCES area (id),
    CONSTRAINT ck_wfarea_kind CHECK (workforce_kind IN ('TECHNICIAN','HELPER','VENDOR')),
    CONSTRAINT ck_wfarea_ref CHECK (
        (workforce_kind = 'TECHNICIAN' AND technician_id IS NOT NULL) OR
        (workforce_kind = 'HELPER'     AND helper_id IS NOT NULL) OR
        (workforce_kind = 'VENDOR'     AND vendor_id IS NOT NULL))
);
CREATE INDEX idx_wfarea_area ON workforce_area (area_id);
CREATE INDEX idx_wfarea_tech ON workforce_area (technician_id);
CREATE UNIQUE INDEX uk_wfarea_tech ON workforce_area (technician_id, area_id);
CREATE UNIQUE INDEX uk_wfarea_vendor ON workforce_area (vendor_id, area_id);

-- ---------------------------------------------------------------------
-- certification — workforce certification records.
-- ---------------------------------------------------------------------
CREATE TABLE certification (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    workforce_kind VARCHAR(20) NOT NULL,
    technician_id UUID,
    helper_id UUID,
    cert_type VARCHAR(100) NOT NULL,
    issuing_authority VARCHAR(150),
    reference_no VARCHAR(100),
    issued_date DATE,
    expiry_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_cert_technician FOREIGN KEY (technician_id) REFERENCES technician (id),
    CONSTRAINT fk_cert_helper FOREIGN KEY (helper_id) REFERENCES helper (id),
    CONSTRAINT ck_cert_kind CHECK (workforce_kind IN ('TECHNICIAN','HELPER')),
    CONSTRAINT ck_cert_status CHECK (status IN ('ACTIVE','EXPIRED','REVOKED'))
);
CREATE INDEX idx_cert_tech ON certification (technician_id);
CREATE INDEX idx_cert_expiry ON certification (expiry_date);

-- ---------------------------------------------------------------------
-- workforce_availability — basic availability (windows / leave).
-- ---------------------------------------------------------------------
CREATE TABLE workforce_availability (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    workforce_kind VARCHAR(20) NOT NULL,
    technician_id UUID,
    helper_id UUID,
    day_of_week SMALLINT,
    specific_date DATE,
    start_time VARCHAR(8),
    end_time VARCHAR(8),
    is_leave BOOLEAN NOT NULL DEFAULT FALSE,
    note VARCHAR(255),
    CONSTRAINT fk_wfavail_technician FOREIGN KEY (technician_id) REFERENCES technician (id),
    CONSTRAINT fk_wfavail_helper FOREIGN KEY (helper_id) REFERENCES helper (id),
    CONSTRAINT ck_wfavail_kind CHECK (workforce_kind IN ('TECHNICIAN','HELPER'))
);
CREATE INDEX idx_wfavail_tech ON workforce_availability (technician_id);

-- ---------------------------------------------------------------------
-- workforce_document — KYC/employment/vendor docs (metadata; object storage).
-- doc_category distinguishes KYC vs GENERAL vs FINANCE for authorization.
-- ---------------------------------------------------------------------
CREATE TABLE workforce_document (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    workforce_kind VARCHAR(20) NOT NULL,
    technician_id UUID,
    helper_id UUID,
    vendor_id UUID,
    employee_id UUID,
    doc_type VARCHAR(50) NOT NULL,
    doc_category VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    file_name VARCHAR(255),
    object_key VARCHAR(500),
    content_type VARCHAR(100),
    size_bytes BIGINT,
    reference_no VARCHAR(100),
    issued_date DATE,
    expiry_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    verified_by UUID,
    verified_at TIMESTAMP,
    rejection_reason VARCHAR(500),
    CONSTRAINT fk_wfdoc_technician FOREIGN KEY (technician_id) REFERENCES technician (id),
    CONSTRAINT fk_wfdoc_helper FOREIGN KEY (helper_id) REFERENCES helper (id),
    CONSTRAINT fk_wfdoc_vendor FOREIGN KEY (vendor_id) REFERENCES vendor (id),
    CONSTRAINT fk_wfdoc_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT fk_wfdoc_verified_by FOREIGN KEY (verified_by) REFERENCES ops_user (id),
    CONSTRAINT ck_wfdoc_kind CHECK (workforce_kind IN ('TECHNICIAN','HELPER','VENDOR','EMPLOYEE')),
    CONSTRAINT ck_wfdoc_category CHECK (doc_category IN ('GENERAL','KYC','FINANCE')),
    CONSTRAINT ck_wfdoc_status CHECK (status IN ('UPLOADED','UNDER_REVIEW','VERIFIED','REJECTED','EXPIRED'))
);
CREATE INDEX idx_wfdoc_tech ON workforce_document (technician_id);
CREATE INDEX idx_wfdoc_category ON workforce_document (doc_category);

-- ---------------------------------------------------------------------
-- workforce_status_history — insert-only lifecycle history (§4). Never updated.
-- ---------------------------------------------------------------------
CREATE TABLE workforce_status_history (
    id UUID NOT NULL PRIMARY KEY,
    workforce_kind VARCHAR(20) NOT NULL,
    workforce_id UUID NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    changed_by UUID,
    reason VARCHAR(1000),
    created_at TIMESTAMP,
    CONSTRAINT ck_wfhist_kind CHECK (workforce_kind IN ('TECHNICIAN','HELPER','VENDOR','EMPLOYEE'))
);
CREATE INDEX idx_wfhist_workforce ON workforce_status_history (workforce_kind, workforce_id);
