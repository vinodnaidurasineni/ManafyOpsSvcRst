-- =====================================================================
-- V4 — Foundation: deferred forward FKs (C-2 resolution, Artifact #1 §11).
--
-- user_scope was created (V2) before region/area (V3). Now that region and area
-- exist, add the region/area FKs. apartment/vendor scope FKs remain DEFERRED to
-- their domain phases (apartments/vendors tables do not exist yet) — this is
-- intentional and does NOT reintroduce a circular FK: the columns are nullable and
-- unconstrained until then.
-- =====================================================================

ALTER TABLE user_scope
    ADD CONSTRAINT fk_us_region FOREIGN KEY (region_id) REFERENCES region (id);

ALTER TABLE user_scope
    ADD CONSTRAINT fk_us_area FOREIGN KEY (area_id) REFERENCES area (id);
