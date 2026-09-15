-- =====================================================================
-- V11 — Authentication reconciliation: link ops_user to the canonical Community
-- person as a PROJECTION REFERENCE (service-boundary doc §11/§17).
--
-- ops_user remains an OPERATIONAL AUTHORIZATION PROJECTION, not a second identity.
-- The authoritative cross-service identity key is the Cognito `sub` (already stored
-- in ops_user.cognito_sub and in Community customer.external_identity_id). This
-- column is an OPTIONAL convenience link to the Community customer.id for the same
-- human, populated out-of-band once the shared Cognito pool is live and subs align.
--
-- IMPORTANT (§15 — no cross-service DB coupling):
--   * This is a plain nullable UUID column, NOT a foreign key. There is deliberately
--     NO FK constraint, because the referenced row lives in the SEPARATE Community
--     database. Ops must never read/write Community tables.
--   * Additive + nullable → non-breaking; existing rows and tests are unaffected.
--   * Ops is NEVER the source of truth for customer identity.
--
-- Dialect-neutral (H2 dev/test + PostgreSQL prod).
-- =====================================================================

ALTER TABLE ops_user ADD COLUMN community_customer_id UUID;

-- Indexed for reverse lookup ("which ops_user maps to this Community customer?").
CREATE INDEX idx_ops_user_community_customer ON ops_user (community_customer_id);
