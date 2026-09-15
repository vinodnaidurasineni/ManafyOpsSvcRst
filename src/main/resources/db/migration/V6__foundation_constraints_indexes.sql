-- =====================================================================
-- V6 — Foundation: additional indexes + constraints.
--
-- DIALECT NOTE (Q-F1): H2 does NOT support partial (filtered) unique indexes
-- (CREATE UNIQUE INDEX ... WHERE ...), which PostgreSQL does. To keep the shared
-- migration path H2-compatible:
--   * The "at most one CURRENT primary Field Officer per area" and "no duplicate
--     current (area, officer) pair" invariants are enforced in the APPLICATION
--     layer (AreaFieldOfficer service/repository checks) for the shared path.
--   * A PostgreSQL production profile MAY add the partial unique indexes and the
--     audit_log immutability REVOKE as prod-only hardening (see
--     db/migration/postgres/ documented in the plan). These are intentionally NOT
--     in the shared path so H2 tests run identically.
--
-- Portable supporting indexes only:
-- =====================================================================

-- Speeds up "current officers of an area" and "current areas of an officer".
CREATE INDEX idx_afo_area_current ON area_field_officer (area_id, effective_to);
CREATE INDEX idx_afo_officer_current ON area_field_officer (field_officer_id, effective_to);

-- Idempotency lookups by endpoint (diagnostics).
CREATE INDEX idx_idempotency_endpoint ON idempotency_key (endpoint);
