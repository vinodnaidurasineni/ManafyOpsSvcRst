package com.manafy.ops.maidapp.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Assignment of a maid (workforce Helper id) to a single booking_schedule
 * occurrence (ported from ManafySvcRst).
 */
@Entity
@Table(name = "maidapp_booking_assignment", indexes = {
        @Index(name = "idx_massignment_schedule", columnList = "booking_schedule_id"),
        @Index(name = "idx_massignment_maid", columnList = "maid_id")
})
public class BookingAssignment extends BaseEntity {

    @Column(name = "booking_schedule_id", nullable = false)
    private UUID bookingScheduleId;

    /** The maid = workforce Helper id. */
    @Column(name = "maid_id", nullable = false)
    private UUID maidId;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "assigned_by", length = 50)
    private String assignedBy; // SYSTEM or admin id

    @Column(name = "assignment_status", nullable = false, length = 50)
    private String assignmentStatus; // ASSIGNED, REASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED

    public UUID getBookingScheduleId() { return bookingScheduleId; }
    public void setBookingScheduleId(UUID bookingScheduleId) { this.bookingScheduleId = bookingScheduleId; }

    public UUID getMaidId() { return maidId; }
    public void setMaidId(UUID maidId) { this.maidId = maidId; }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }

    public String getAssignmentStatus() { return assignmentStatus; }
    public void setAssignmentStatus(String assignmentStatus) { this.assignmentStatus = assignmentStatus; }
}
