package com.manafy.ops.maidapp.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Maid-app booking (ported from ManafySvcRst booking domain).
 *
 * Part of the maid self-service slice: a recurring booking whose per-day
 * occurrences live in {@link BookingSchedule}, each of which is assigned to a
 * maid via {@link BookingAssignment}. The maid app reads its jobs/earnings from
 * these tables.
 */
@Entity
@Table(name = "maidapp_booking", indexes = {
        @Index(name = "idx_mbooking_customer", columnList = "customer_id"),
        @Index(name = "idx_mbooking_number", columnList = "booking_number"),
        @Index(name = "idx_mbooking_status", columnList = "booking_status")
})
public class Booking extends BaseEntity {

    @Column(name = "booking_number", unique = true, length = 50)
    private String bookingNumber;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "apartment_id")
    private UUID apartmentId;

    @Column(name = "slot_id")
    private UUID slotId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "duration_months", nullable = false)
    private int durationMonths = 1;

    @Column(name = "booking_status", nullable = false, length = 50)
    private String bookingStatus;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "bhk_type", length = 10)
    private String bhkType;

    @Column(name = "selected_time", length = 40)
    private String selectedTime; // e.g. "06:00 AM - 06:30 AM"

    public String getBookingNumber() { return bookingNumber; }
    public void setBookingNumber(String bookingNumber) { this.bookingNumber = bookingNumber; }

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }

    public UUID getApartmentId() { return apartmentId; }
    public void setApartmentId(UUID apartmentId) { this.apartmentId = apartmentId; }

    public UUID getSlotId() { return slotId; }
    public void setSlotId(UUID slotId) { this.slotId = slotId; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public int getDurationMonths() { return durationMonths; }
    public void setDurationMonths(int durationMonths) { this.durationMonths = durationMonths; }

    public String getBookingStatus() { return bookingStatus; }
    public void setBookingStatus(String bookingStatus) { this.bookingStatus = bookingStatus; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getBhkType() { return bhkType; }
    public void setBhkType(String bhkType) { this.bhkType = bhkType; }

    public String getSelectedTime() { return selectedTime; }
    public void setSelectedTime(String selectedTime) { this.selectedTime = selectedTime; }
}
