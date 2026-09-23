-- =====================================================================
-- V12 — Manual Assignment Request (Field-Officer-coordinated fulfillment).
--
-- The Ops record for resident requests (e.g. Recurring Helpers: maid/cook/etc.)
-- that need a PERSON to fulfill them but are NOT technician dispatch. It is
-- intentionally SEPARATE from service_request/assignment/field_visit:
--   * NO technician FK, NO eligibility/skill/certification, NO field visit.
--   * The assignee is an OPAQUE reference (Community helper/vendor, Ops workforce,
--     or external person) captured as type + ref/name/phone — never a cross-DB FK.
--
-- Cross-service source references (source_system/type/id + community_* ids) are
-- opaque strings (Community stays the source of truth for resident identity);
-- the unique (source_system, source_type, source_id) is the durable idempotency
-- backstop so one Community source produces at most one Ops record.
--
-- Scope: area_id/region_id are NULLABLE. When resolvable they enable Field-Officer
-- area scoping (ScopeService/AREA_RESPONSIBLE); when null the request is visible to
-- GLOBAL-scoped operators only (Community apartment -> Ops area mapping is future
-- work, per the intake-architecture doc's Phase-0 note).
--
-- Dialect-neutral (H2 dev/test + PostgreSQL prod), matching V1..V11 conventions:
-- application-generated UUID pk, audit/soft-delete/version columns, VARCHAR+CHECK
-- enums, named uk_/fk_/idx_, insert-only status-history table.
-- =====================================================================

CREATE TABLE manual_assignment_request (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,

    reference_no VARCHAR(40) NOT NULL,

    source_system VARCHAR(40),
    source_type VARCHAR(60),
    source_id VARCHAR(64),
    community_customer_id VARCHAR(64),
    community_apartment_id VARCHAR(64),
    community_flat_id VARCHAR(64),

    area_id UUID,
    region_id UUID,

    service_type VARCHAR(40),
    service_title VARCHAR(120),
    frequency VARCHAR(20),
    start_date DATE,
    time_slot VARCHAR(20),
    service_address VARCHAR(400),
    resident_name VARCHAR(120),
    contact_number VARCHAR(20),
    notes VARCHAR(2000),
    amount DECIMAL(10,2),
    payment_status VARCHAR(20) DEFAULT 'NONE',
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',

    field_officer_id UUID,

    assignee_type VARCHAR(30),
    assignee_ref VARCHAR(64),
    assignee_name VARCHAR(120),
    assignee_phone VARCHAR(20),
    completion_notes VARCHAR(1000),
    cancel_reason VARCHAR(1000),

    CONSTRAINT uk_manual_request_reference UNIQUE (reference_no),
    CONSTRAINT uk_manual_request_source UNIQUE (source_system, source_type, source_id),
    CONSTRAINT fk_mar_area FOREIGN KEY (area_id) REFERENCES area (id),
    CONSTRAINT fk_mar_region FOREIGN KEY (region_id) REFERENCES region (id),
    CONSTRAINT fk_mar_field_officer FOREIGN KEY (field_officer_id) REFERENCES ops_user (id),
    CONSTRAINT ck_mar_status CHECK (status IN ('OPEN','QUEUED','ACKED_BY_FO','ASSIGNED','IN_PROGRESS','COMPLETED','CANCELLED')),
    CONSTRAINT ck_mar_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')),
    CONSTRAINT ck_mar_assignee_type CHECK (assignee_type IS NULL OR assignee_type IN ('EXTERNAL_PERSON','COMMUNITY_HELPER_REF','COMMUNITY_VENDOR_REF','OPS_WORKFORCE'))
);

-- Indexes for the FO queue (status + area) and reconciliation (source id).
CREATE INDEX idx_mar_status ON manual_assignment_request (status);
CREATE INDEX idx_mar_area ON manual_assignment_request (area_id);
CREATE INDEX idx_mar_field_officer ON manual_assignment_request (field_officer_id);
CREATE INDEX idx_mar_source ON manual_assignment_request (source_system, source_type, source_id);
CREATE INDEX idx_mar_created_at ON manual_assignment_request (created_at);

-- NOTE: state-transition history is recorded via the shared AuditService
-- (audit_log + activity_log), matching how service_request records its lifecycle.
-- No dedicated per-entity history table is introduced.
