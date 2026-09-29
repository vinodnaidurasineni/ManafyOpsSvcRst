-- =====================================================================
-- V17 — Gate/guard domain (visitors, deliveries, staff passes; ported from
-- ManafySvcRst). Backs the guard app (/api/v1/gate/guard/*). Tables match
-- com.manafy.ops.gate.entity.* exactly (ddl-auto=validate). Dialect-neutral.
-- =====================================================================

CREATE TABLE IF NOT EXISTS gate_visitor (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    apartment_id UUID NOT NULL,
    flat_id UUID,
    visitor_name VARCHAR(150) NOT NULL,
    visitor_mobile VARCHAR(30),
    purpose VARCHAR(100),
    visitor_vehicle_number VARCHAR(30),
    visitor_photo_url VARCHAR,
    notes TEXT,
    otp_code VARCHAR(10),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    checkin_at TIMESTAMP,
    checkout_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_gvisitor_apartment_status ON gate_visitor (apartment_id, status);

CREATE TABLE IF NOT EXISTS gate_delivery (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    apartment_id UUID NOT NULL,
    flat_id UUID,
    delivery_company VARCHAR(100) NOT NULL,
    delivery_person_name VARCHAR(150),
    delivery_person_mobile VARCHAR(30),
    package_description VARCHAR(255),
    photo_url VARCHAR,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING'
);
CREATE INDEX IF NOT EXISTS idx_gdelivery_apartment_status ON gate_delivery (apartment_id, status);

CREATE TABLE IF NOT EXISTS gate_staff_pass (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    apartment_id UUID NOT NULL,
    flat_id UUID,
    staff_name VARCHAR(150) NOT NULL,
    staff_mobile VARCHAR(30) NOT NULL,
    staff_type VARCHAR(30),
    flat_number VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);
CREATE INDEX IF NOT EXISTS idx_gstaff_apartment_mobile ON gate_staff_pass (apartment_id, staff_mobile);
