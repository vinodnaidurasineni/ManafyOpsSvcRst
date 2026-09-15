-- =====================================================================
-- V2 — Foundation: user scope grants (Artifact #1 §1, spec §9).
--
-- Permission and scope are independent gates. A row grants a scope_type with the
-- matching ref id. region/area FKs are added in V4 (after org tables exist);
-- apartment/vendor FKs are DEFERRED to their domain phases (tables absent now) per
-- Artifact #1 §11 deferred-FK strategy — the columns exist here, nullable.
--
-- The CHECK enforces that the ref column matching scope_type is populated.
-- =====================================================================

CREATE TABLE user_scope (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL,
    scope_type VARCHAR(20) NOT NULL,
    region_id UUID,
    area_id UUID,
    apartment_id UUID,
    vendor_id UUID,
    CONSTRAINT fk_us_user FOREIGN KEY (user_id) REFERENCES ops_user (id),
    CONSTRAINT ck_user_scope_type CHECK (
        scope_type IN ('GLOBAL','REGION','AREA','APARTMENT','VENDOR','SELF','ASSIGNED')
    ),
    CONSTRAINT ck_user_scope_ref CHECK (
        (scope_type = 'REGION'    AND region_id    IS NOT NULL) OR
        (scope_type = 'AREA'      AND area_id      IS NOT NULL) OR
        (scope_type = 'APARTMENT' AND apartment_id IS NOT NULL) OR
        (scope_type = 'VENDOR'    AND vendor_id    IS NOT NULL) OR
        (scope_type IN ('GLOBAL','SELF','ASSIGNED'))
    )
);
CREATE INDEX idx_user_scope_user ON user_scope (user_id);
CREATE INDEX idx_user_scope_area ON user_scope (area_id);
CREATE INDEX idx_user_scope_region ON user_scope (region_id);
