-- =====================================================================
-- V9 — Phase 4A: Service Request foundation.
--
-- Dialect-neutral (H2 dev/test + PostgreSQL prod), matching Phase 1/2/3 conventions
-- (Q-F1/Q-F2): application-generated UUIDs, VARCHAR + CHECK for status/enum,
-- TIMESTAMP, BIGINT version, soft-delete columns.
--
-- SCOPE (Phase 4A ONLY): the core Service Request record + a limited lifecycle
-- (NEW -> CANCELLED). This migration does NOT create assignment, dispatch,
-- scheduling, field-visit, payment, or notification tables — those belong to later
-- phases.
--
-- Scope anchoring (IDOR/authorization): a service_request stores its own
-- apartment_id AND the apartment's area_id + region_id (denormalized, server-set at
-- create time from the apartment row). ResourceScopeResolver.serviceRequest(...)
-- reads these persisted columns so ScopeService.covers() grants access via
-- APARTMENT / AREA (incl. Field-Officer area ownership) / REGION→AREA inheritance —
-- reusing the exact Phase 2 apartment scope model, no parallel mechanism.
--
-- requester_user_id references ops_user (the internal actor who raised the request).
-- =====================================================================

-- ---------------------------------------------------------------------
-- service_request — core request record (Phase 4A foundation).
-- ---------------------------------------------------------------------
CREATE TABLE service_request (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    reference_no VARCHAR(40) NOT NULL,
    apartment_id UUID NOT NULL,
    area_id UUID NOT NULL,
    region_id UUID NOT NULL,
    requester_user_id UUID NOT NULL,
    category VARCHAR(40) NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    description VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    cancelled_at TIMESTAMP,
    cancel_reason VARCHAR(1000),
    CONSTRAINT uk_service_request_reference UNIQUE (reference_no),
    CONSTRAINT fk_sr_apartment FOREIGN KEY (apartment_id) REFERENCES apartment (id),
    CONSTRAINT fk_sr_area FOREIGN KEY (area_id) REFERENCES area (id),
    CONSTRAINT fk_sr_region FOREIGN KEY (region_id) REFERENCES region (id),
    CONSTRAINT fk_sr_requester FOREIGN KEY (requester_user_id) REFERENCES ops_user (id),
    CONSTRAINT ck_sr_category CHECK (category IN ('PLUMBING','ELECTRICAL','HVAC','CLEANING','SECURITY','GENERAL','OTHER')),
    CONSTRAINT ck_sr_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')),
    CONSTRAINT ck_sr_status CHECK (status IN ('NEW','CANCELLED'))
);

-- Indexes justified by the Phase 4A list/filter API (apartment, area, requester,
-- status, priority) and by created-date ordering. No speculative indexes.
CREATE INDEX idx_sr_apartment ON service_request (apartment_id);
CREATE INDEX idx_sr_area ON service_request (area_id);
CREATE INDEX idx_sr_requester ON service_request (requester_user_id);
CREATE INDEX idx_sr_status ON service_request (status);
CREATE INDEX idx_sr_priority ON service_request (priority);
CREATE INDEX idx_sr_created_at ON service_request (created_at);
