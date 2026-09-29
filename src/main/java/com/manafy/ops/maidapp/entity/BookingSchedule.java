package com.manafy.ops.maidapp.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A single service occurrence on a specific date (ported from ManafySvcRst).
 *
 * Display fields (service_name, apartment_name, tower, flat_number, slot_time)
 * are denormalized here so the maid-app slice needs only the booking domain —
 * no separate CustomerAddress / Apartment / catalog tables for this slice.
 * Assignments attach to schedule rows, not bookings.
 */
@Entity
@Table(name = "maidapp_booking_schedule", indexes = {
        @Index(name = "idx_mschedule_booking", columnList = "booking_id"),
        @Index(name = "idx_mschedule_date_status", columnList = "scheduled_date,status")
})
public class BookingSchedule extends BaseEntity {

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "slot_id")
    private UUID slotId;

    @Column(name = "apartment_id")
    private UUID apartmentId;

    @Column(nullable = false, length = 50)
    private String status; // PENDING, ASSIGNED, IN_PROGRESS, COMPLETED, SKIPPED

    // ─── Denormalized display fields (source resolves these from address/apartment/catalog) ───
    @Column(name = "service_name", length = 150)
    private String serviceName;

    @Column(name = "apartment_name", length = 200)
    private String apartmentName;

    @Column(length = 50)
    private String tower;

    @Column(name = "flat_number", length = 50)
    private String flatNumber;

    @Column(name = "slot_time", length = 40)
    private String slotTime;

    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }

    public UUID getServiceId() { return serviceId; }
    public void setServiceId(UUID serviceId) { this.serviceId = serviceId; }

    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }

    public UUID getSlotId() { return slotId; }
    public void setSlotId(UUID slotId) { this.slotId = slotId; }

    public UUID getApartmentId() { return apartmentId; }
    public void setApartmentId(UUID apartmentId) { this.apartmentId = apartmentId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getApartmentName() { return apartmentName; }
    public void setApartmentName(String apartmentName) { this.apartmentName = apartmentName; }

    public String getTower() { return tower; }
    public void setTower(String tower) { this.tower = tower; }

    public String getFlatNumber() { return flatNumber; }
    public void setFlatNumber(String flatNumber) { this.flatNumber = flatNumber; }

    public String getSlotTime() { return slotTime; }
    public void setSlotTime(String slotTime) { this.slotTime = slotTime; }
}
