-- ---------------------------------------------------------------------
-- V13 — Helper service category + availability (manual-fulfillment).
--
-- The manual (Recurring Helper) assignment flow needs to (a) match a request's
-- service type to helpers who perform that service, and (b) know whether a
-- helper can currently receive work. The Helper table (V8) had neither: only a
-- `relationship` (MANAFY|TECHNICIAN|VENDOR) and a lifecycle `status`.
--
-- We add:
--   * category            — the service the helper performs (MAID, COOK, PLUMBER,
--                           ELECTRICIAN, CLEANER, CARPENTER, OTHER). Mirrors the
--                           Community service-type codes; nullable for existing
--                           rows / helpers that are not part of a category pool.
--   * availability_status — AVAILABLE | UNAVAILABLE. Combined with `status`
--                           (ACTIVE) to decide eligibility. Defaults AVAILABLE so
--                           an ACTIVE helper is assignable unless explicitly paused.
--
-- No availability CALENDAR is introduced (none exists today) — this is the
-- simplest reliable two-axis compatibility check the domain supports.
-- ---------------------------------------------------------------------

ALTER TABLE helper ADD COLUMN category VARCHAR(40);
ALTER TABLE helper ADD COLUMN availability_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE';

ALTER TABLE helper ADD CONSTRAINT ck_helper_availability_status
    CHECK (availability_status IN ('AVAILABLE', 'UNAVAILABLE'));

-- Assignment queries filter helpers by category + status + availability.
CREATE INDEX idx_helper_category ON helper (category);
CREATE INDEX idx_helper_status_availability ON helper (status, availability_status);
