-- =====================================================================
-- V14 — Ops Complaint (operational complaint visible to Ops users).
--
-- The Ops-side record of an operational complaint (e.g. a maid no-show, work
-- not done, damage) that an Ops user views and works. It is DELIBERATELY
-- SEPARATE from the technician dispatch (service_request/assignment) and from
-- the manual_assignment_request queue:
--   * NO technician FK, NO eligibility/skill/dispatch.
--   * The assignee/maid is captured as opaque name/phone (never a cross-DB FK),
--     matching the manual_assignment_request assignee convention.
--
-- Community stays the source of truth for resident-facing society complaints
-- (society_complaint). This Ops table holds only the operational projection an
-- Ops user acts on, referencing Community by opaque source ids — mirroring
-- V11/V12 (ops_user.community_customer_id, manual_assignment_request source_*).
--
-- Dialect-neutral (H2 dev/test + PostgreSQL prod), matching V1..V13 conventions:
-- application-generated UUID pk, audit/soft-delete/version columns, VARCHAR+CHECK
-- enums, named uk_/idx_.
-- =====================================================================

CREATE TABLE ops_complaint (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,

    reference_no VARCHAR(40) NOT NULL,

    -- Cross-service source reference (opaque; never FKs). Null for Ops-native complaints.
    source_system VARCHAR(40),
    source_type VARCHAR(60),
    source_id VARCHAR(64),
    community_customer_id VARCHAR(64),
    community_apartment_id VARCHAR(64),

    -- Scope anchors (nullable; area scoping when resolvable, GLOBAL-only when null).
    area_id UUID,
    region_id UUID,

    -- MAID_NO_SHOW | WORK_NOT_DONE | MAID_LATE | DAMAGE | BEHAVIOR | BILLING | OTHER
    complaint_type VARCHAR(40) NOT NULL DEFAULT 'OTHER',
    description VARCHAR(2000),
    -- OPEN | IN_PROGRESS | RESOLVED | CLOSED
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',

    -- Customer / resident (denormalized display copy; identity owned by Community).
    customer_name VARCHAR(120),
    customer_mobile VARCHAR(20),

    -- Assigned maid/helper (opaque display copy; NEVER a workforce FK here).
    assignee_ref VARCHAR(64),
    assignee_name VARCHAR(120),
    assignee_mobile VARCHAR(20),

    booking_reference VARCHAR(64),
    admin_notes VARCHAR(2000),
    resolved_at TIMESTAMP,
    closed_at TIMESTAMP,

    CONSTRAINT uk_ops_complaint_reference UNIQUE (reference_no),
    CONSTRAINT uk_ops_complaint_source UNIQUE (source_system, source_type, source_id),
    CONSTRAINT fk_ops_complaint_area FOREIGN KEY (area_id) REFERENCES area (id),
    CONSTRAINT fk_ops_complaint_region FOREIGN KEY (region_id) REFERENCES region (id),
    CONSTRAINT ck_ops_complaint_status CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED')),
    CONSTRAINT ck_ops_complaint_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT'))
);

CREATE INDEX idx_ops_complaint_status ON ops_complaint (status);
CREATE INDEX idx_ops_complaint_area ON ops_complaint (area_id);
CREATE INDEX idx_ops_complaint_created_at ON ops_complaint (created_at);
CREATE INDEX idx_ops_complaint_source ON ops_complaint (source_system, source_type, source_id);
