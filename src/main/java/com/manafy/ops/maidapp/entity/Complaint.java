package com.manafy.ops.maidapp.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Booking/maid complaint (ported from ManafySvcRst). Worked by admins; can be
 * escalated up the management chain. Customer/maid display fields are
 * denormalized for the admin list (this slice has no separate customer table).
 */
@Entity
@Table(name = "maidapp_complaint", indexes = {
        @Index(name = "idx_mcomplaint_status", columnList = "complaint_status")
})
public class Complaint extends BaseEntity {

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "maid_id")
    private UUID maidId;

    @Column(name = "complaint_type", nullable = false, length = 50)
    private String complaintType; // MAID_NO_SHOW, WORK_NOT_DONE, MAID_LATE, DAMAGE, OTHER

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "complaint_status", nullable = false, length = 30)
    private String complaintStatus = "OPEN"; // OPEN, IN_PROGRESS, RESOLVED, CLOSED

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    // Denormalized display fields for the admin list.
    @Column(name = "customer_name", length = 150)
    private String customerName;

    @Column(name = "customer_mobile", length = 30)
    private String customerMobile;

    @Column(name = "maid_name", length = 150)
    private String maidName;

    @Column(name = "maid_mobile", length = 30)
    private String maidMobile;

    // Escalation metrics.
    @Column(name = "current_escalation_level")
    private Integer currentEscalationLevel = 0;

    @Column(name = "last_escalated_at")
    private LocalDateTime lastEscalatedAt;

    @Column(name = "sla_breach")
    private Boolean slaBreached = false;

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }
    public UUID getMaidId() { return maidId; }
    public void setMaidId(UUID maidId) { this.maidId = maidId; }
    public String getComplaintType() { return complaintType; }
    public void setComplaintType(String complaintType) { this.complaintType = complaintType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getComplaintStatus() { return complaintStatus; }
    public void setComplaintStatus(String complaintStatus) { this.complaintStatus = complaintStatus; }
    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerMobile() { return customerMobile; }
    public void setCustomerMobile(String customerMobile) { this.customerMobile = customerMobile; }
    public String getMaidName() { return maidName; }
    public void setMaidName(String maidName) { this.maidName = maidName; }
    public String getMaidMobile() { return maidMobile; }
    public void setMaidMobile(String maidMobile) { this.maidMobile = maidMobile; }
    public Integer getCurrentEscalationLevel() { return currentEscalationLevel; }
    public void setCurrentEscalationLevel(Integer currentEscalationLevel) { this.currentEscalationLevel = currentEscalationLevel; }
    public LocalDateTime getLastEscalatedAt() { return lastEscalatedAt; }
    public void setLastEscalatedAt(LocalDateTime lastEscalatedAt) { this.lastEscalatedAt = lastEscalatedAt; }
    public Boolean getSlaBreached() { return slaBreached; }
    public void setSlaBreached(Boolean slaBreached) { this.slaBreached = slaBreached; }
}
