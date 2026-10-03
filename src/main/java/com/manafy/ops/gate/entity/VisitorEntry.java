package com.manafy.ops.gate.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** A gate visitor entry (ported from ManafySvcRst gate domain). */
@Entity
@Table(name = "gate_visitor", indexes = {
        @Index(name = "idx_gvisitor_apartment_status", columnList = "apartment_id,status")
})
public class VisitorEntry extends BaseEntity {

    @Column(name = "apartment_id", nullable = false)
    private UUID apartmentId;

    @Column(name = "flat_id")
    private UUID flatId;

    @Column(name = "visitor_name", nullable = false, length = 150)
    private String visitorName;

    @Column(name = "visitor_mobile", length = 30)
    private String visitorMobile;

    @Column(length = 100)
    private String purpose;

    @Column(name = "visitor_vehicle_number", length = 30)
    private String visitorVehicleNumber;

    @Column(name = "visitor_photo_url")
    private String visitorPhotoUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "otp_code", length = 10)
    private String otpCode;

    @Column(nullable = false, length = 20)
    private String status = "PENDING"; // PENDING, APPROVED, CHECKED_IN, CHECKED_OUT, REJECTED

    @Column(name = "checkin_at")
    private LocalDateTime checkinAt;

    @Column(name = "checkout_at")
    private LocalDateTime checkoutAt;

    public UUID getApartmentId() { return apartmentId; }
    public void setApartmentId(UUID apartmentId) { this.apartmentId = apartmentId; }
    public UUID getFlatId() { return flatId; }
    public void setFlatId(UUID flatId) { this.flatId = flatId; }
    public String getVisitorName() { return visitorName; }
    public void setVisitorName(String visitorName) { this.visitorName = visitorName; }
    public String getVisitorMobile() { return visitorMobile; }
    public void setVisitorMobile(String visitorMobile) { this.visitorMobile = visitorMobile; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getVisitorVehicleNumber() { return visitorVehicleNumber; }
    public void setVisitorVehicleNumber(String visitorVehicleNumber) { this.visitorVehicleNumber = visitorVehicleNumber; }
    public String getVisitorPhotoUrl() { return visitorPhotoUrl; }
    public void setVisitorPhotoUrl(String visitorPhotoUrl) { this.visitorPhotoUrl = visitorPhotoUrl; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCheckinAt() { return checkinAt; }
    public void setCheckinAt(LocalDateTime checkinAt) { this.checkinAt = checkinAt; }
    public LocalDateTime getCheckoutAt() { return checkoutAt; }
    public void setCheckoutAt(LocalDateTime checkoutAt) { this.checkoutAt = checkoutAt; }
}
