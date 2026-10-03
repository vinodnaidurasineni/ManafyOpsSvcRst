-- =====================================================================
-- V14 — Maid-app booking domain (ported from ManafySvcRst).
--
-- Backs the maid self-service surface (/api/v1/maids/me/*): recurring bookings,
-- their per-day schedule occurrences, maid assignments, attendance and leave.
-- Tables match com.manafy.ops.maidapp.entity.* exactly (ddl-auto=validate).
-- Dialect-neutral (H2 + PostgreSQL). Sample data is seeded by DevUserSeeder so
-- assignments can reference the runtime-generated Helper ids.
-- =====================================================================

CREATE TABLE IF NOT EXISTS maidapp_booking (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    booking_number VARCHAR(50),
    customer_id UUID NOT NULL,
    apartment_id UUID,
    slot_id UUID,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    duration_months INTEGER NOT NULL DEFAULT 1,
    booking_status VARCHAR(50) NOT NULL,
    total_amount NUMERIC(10,2) NOT NULL,
    bhk_type VARCHAR(10),
    selected_time VARCHAR(40),
    CONSTRAINT uk_maidapp_booking_number UNIQUE (booking_number)
);
CREATE INDEX IF NOT EXISTS idx_mbooking_customer ON maidapp_booking (customer_id);
CREATE INDEX IF NOT EXISTS idx_mbooking_number ON maidapp_booking (booking_number);
CREATE INDEX IF NOT EXISTS idx_mbooking_status ON maidapp_booking (booking_status);

CREATE TABLE IF NOT EXISTS maidapp_booking_schedule (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    booking_id UUID NOT NULL,
    service_id UUID,
    scheduled_date DATE NOT NULL,
    slot_id UUID,
    apartment_id UUID,
    status VARCHAR(50) NOT NULL,
    service_name VARCHAR(150),
    apartment_name VARCHAR(200),
    tower VARCHAR(50),
    flat_number VARCHAR(50),
    slot_time VARCHAR(40)
);
CREATE INDEX IF NOT EXISTS idx_mschedule_booking ON maidapp_booking_schedule (booking_id);
CREATE INDEX IF NOT EXISTS idx_mschedule_date_status ON maidapp_booking_schedule (scheduled_date, status);

CREATE TABLE IF NOT EXISTS maidapp_booking_assignment (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    booking_schedule_id UUID NOT NULL,
    maid_id UUID NOT NULL,
    assigned_at TIMESTAMP NOT NULL,
    assigned_by VARCHAR(50),
    assignment_status VARCHAR(50) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_massignment_schedule ON maidapp_booking_assignment (booking_schedule_id);
CREATE INDEX IF NOT EXISTS idx_massignment_maid ON maidapp_booking_assignment (maid_id);

CREATE TABLE IF NOT EXISTS maidapp_attendance (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    maid_id UUID NOT NULL,
    attendance_date DATE NOT NULL,
    checkin_time TIMESTAMP,
    checkout_time TIMESTAMP,
    latitude NUMERIC(10,6),
    longitude NUMERIC(10,6),
    status VARCHAR(50) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_mattendance_maid_date ON maidapp_attendance (maid_id, attendance_date);

CREATE TABLE IF NOT EXISTS maidapp_maid_leave (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    maid_id UUID NOT NULL,
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    reason VARCHAR(255),
    leave_status VARCHAR(20) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_mleave_maid ON maidapp_maid_leave (maid_id);
