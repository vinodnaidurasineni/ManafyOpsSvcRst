-- =====================================================================
-- V10 — Phase 4B: Operations & Dispatch.
--
-- Dialect-neutral (H2 dev/test + PostgreSQL prod), matching Phase 1/2/3/4A
-- conventions (Q-F1/Q-F2): application-generated UUIDs, VARCHAR + CHECK for
-- status/enum, TIMESTAMP, BIGINT version, soft-delete columns.
--
-- SCOPE (Phase 4B): assignment of a service request to a technician/helper (+ vendor
-- where applicable), the assignment lifecycle, insert-only assignment status history,
-- and field visits. Also widens service_request.status to the projection states
-- driven by assignment activity (DD-36).
--
-- Scope anchoring (IDOR/authorization): an assignment is scoped through its parent
-- service_request (apartment/area/region), resolved server-side by
-- ResourceScopeResolver.assignment(...) — no parallel scope mechanism.
--
-- NOT in this phase (deferred): technician login, finance/payouts, automatic
-- dispatch, notification providers.
-- =====================================================================

-- ---------------------------------------------------------------------
-- service_request.status — widen to the Phase 4B projection states (DD-36).
-- V9 allowed only NEW|CANCELLED; assignment activity now projects onto the request.
-- Drop + re-add the CHECK (dialect-neutral; both H2 and PostgreSQL support this).
-- ---------------------------------------------------------------------
ALTER TABLE service_request DROP CONSTRAINT ck_sr_status;
ALTER TABLE service_request ADD CONSTRAINT ck_sr_status
    CHECK (status IN ('NEW','ASSIGNED','IN_PROGRESS','COMPLETED','REWORK','CANCELLED'));

-- Denormalized pointer to the currently-active assignment (nullable). Kept in sync
-- by the service layer; FK added after the assignment table exists (below).
ALTER TABLE service_request ADD COLUMN active_assignment_id UUID;

-- ---------------------------------------------------------------------
-- assignment — a service request assigned to a technician (+ optional helper/vendor).
-- Reassignment NEVER mutates a prior assignment to look like the new technician —
-- the old row is CANCELLED/closed and a new row is inserted, both preserved.
-- ---------------------------------------------------------------------
CREATE TABLE assignment (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    reference_no VARCHAR(40) NOT NULL,
    service_request_id UUID NOT NULL,
    technician_id UUID NOT NULL,
    helper_id UUID,
    vendor_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    scheduled_start TIMESTAMP,
    scheduled_end TIMESTAMP,
    assigned_at TIMESTAMP,
    accepted_at TIMESTAMP,
    declined_at TIMESTAMP,
    en_route_at TIMESTAMP,
    arrived_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    completion_notes VARCHAR(2000),
    decline_reason VARCHAR(1000),
    cancel_reason VARCHAR(1000),
    no_show_reason VARCHAR(1000),
    rework_reason VARCHAR(1000),
    previous_assignment_id UUID,
    assigned_by UUID,
    CONSTRAINT uk_assignment_reference UNIQUE (reference_no),
    CONSTRAINT fk_asg_request FOREIGN KEY (service_request_id) REFERENCES service_request (id),
    CONSTRAINT fk_asg_technician FOREIGN KEY (technician_id) REFERENCES technician (id),
    CONSTRAINT fk_asg_helper FOREIGN KEY (helper_id) REFERENCES helper (id),
    CONSTRAINT fk_asg_vendor FOREIGN KEY (vendor_id) REFERENCES vendor (id),
    CONSTRAINT fk_asg_previous FOREIGN KEY (previous_assignment_id) REFERENCES assignment (id),
    CONSTRAINT fk_asg_assigned_by FOREIGN KEY (assigned_by) REFERENCES ops_user (id),
    CONSTRAINT ck_asg_status CHECK (status IN
        ('ASSIGNED','ACCEPTED','EN_ROUTE','ARRIVED','IN_PROGRESS','COMPLETED',
         'DECLINED','CANCELLED','NO_SHOW','REWORK')),
    CONSTRAINT ck_asg_schedule CHECK (scheduled_end IS NULL OR scheduled_start IS NULL OR scheduled_end >= scheduled_start)
);
CREATE INDEX idx_asg_request ON assignment (service_request_id);
CREATE INDEX idx_asg_technician ON assignment (technician_id);
CREATE INDEX idx_asg_vendor ON assignment (vendor_id);
CREATE INDEX idx_asg_status ON assignment (status);
CREATE INDEX idx_asg_scheduled_start ON assignment (scheduled_start);
CREATE INDEX idx_asg_active ON assignment (service_request_id, active);
-- NOTE: "at most one ACTIVE assignment per service request" is a business invariant
-- enforced in the service layer (reassignment deactivates the old row in the same
-- transaction, guarded by optimistic locking). A partial UNIQUE index (WHERE
-- active = TRUE) is intentionally NOT used because H2 does not support filtered
-- indexes; keeping the migration dialect-neutral (H2 + PostgreSQL).

-- Deferred FK: service_request.active_assignment_id → assignment.id.
ALTER TABLE service_request ADD CONSTRAINT fk_sr_active_assignment
    FOREIGN KEY (active_assignment_id) REFERENCES assignment (id);

-- ---------------------------------------------------------------------
-- assignment_status_history — insert-only lifecycle log (never updated).
-- ---------------------------------------------------------------------
CREATE TABLE assignment_status_history (
    id UUID NOT NULL PRIMARY KEY,
    assignment_id UUID NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    changed_by UUID,
    reason VARCHAR(1000),
    created_at TIMESTAMP,
    CONSTRAINT fk_asghist_assignment FOREIGN KEY (assignment_id) REFERENCES assignment (id)
);
CREATE INDEX idx_asghist_assignment ON assignment_status_history (assignment_id);

-- ---------------------------------------------------------------------
-- field_visit — a scheduled/executed visit tied to an assignment (+ request/apartment).
-- Visit lifecycle mirrors the operational milestones without duplicating assignment state.
-- ---------------------------------------------------------------------
CREATE TABLE field_visit (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    assignment_id UUID NOT NULL,
    service_request_id UUID NOT NULL,
    technician_id UUID NOT NULL,
    apartment_id UUID NOT NULL,
    scheduled_start TIMESTAMP,
    scheduled_end TIMESTAMP,
    arrived_at TIMESTAMP,
    departed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    outcome VARCHAR(30),
    notes VARCHAR(2000),
    CONSTRAINT fk_fv_assignment FOREIGN KEY (assignment_id) REFERENCES assignment (id),
    CONSTRAINT fk_fv_request FOREIGN KEY (service_request_id) REFERENCES service_request (id),
    CONSTRAINT fk_fv_technician FOREIGN KEY (technician_id) REFERENCES technician (id),
    CONSTRAINT fk_fv_apartment FOREIGN KEY (apartment_id) REFERENCES apartment (id),
    CONSTRAINT ck_fv_status CHECK (status IN ('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED','NO_SHOW')),
    CONSTRAINT ck_fv_outcome CHECK (outcome IS NULL OR outcome IN ('RESOLVED','PARTIAL','UNRESOLVED','REWORK_REQUIRED')),
    CONSTRAINT ck_fv_schedule CHECK (scheduled_end IS NULL OR scheduled_start IS NULL OR scheduled_end >= scheduled_start)
);
CREATE INDEX idx_fv_assignment ON field_visit (assignment_id);
CREATE INDEX idx_fv_request ON field_visit (service_request_id);
CREATE INDEX idx_fv_technician ON field_visit (technician_id);
CREATE INDEX idx_fv_scheduled_start ON field_visit (scheduled_start);
