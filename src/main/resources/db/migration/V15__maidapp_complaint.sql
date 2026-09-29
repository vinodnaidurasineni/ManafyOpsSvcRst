-- =====================================================================
-- V15 — Maid/booking complaints (ported from ManafySvcRst).
--
-- Backs the admin complaints screen (/api/v1/admin/complaints). Customer/maid
-- display fields are denormalized. Matches com.manafy.ops.maidapp.entity.Complaint
-- exactly (ddl-auto=validate). Dialect-neutral.
-- =====================================================================
CREATE TABLE IF NOT EXISTS maidapp_complaint (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    customer_id UUID,
    booking_id UUID,
    maid_id UUID,
    complaint_type VARCHAR(50) NOT NULL,
    description TEXT,
    complaint_status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    admin_notes TEXT,
    customer_name VARCHAR(150),
    customer_mobile VARCHAR(30),
    maid_name VARCHAR(150),
    maid_mobile VARCHAR(30),
    current_escalation_level INTEGER DEFAULT 0,
    last_escalated_at TIMESTAMP,
    sla_breach BOOLEAN DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_mcomplaint_status ON maidapp_complaint (complaint_status);
