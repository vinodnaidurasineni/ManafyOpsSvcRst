package com.manafy.ops.dispatch.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Assignment of a service request to a technician (+ optional helper/vendor) — the
 * core dispatch record (Phase 4B).
 *
 * History-preserving: reassignment NEVER mutates a prior assignment to look like the
 * new technician. The old row is CANCELLED and {@code active} set false; a new row is
 * inserted with {@code previousAssignmentId} pointing back. Scope is resolved via the
 * parent service request (IDOR-safe).
 */
@Entity
@Table(name = "assignment",
        uniqueConstraints = @UniqueConstraint(name = "uk_assignment_reference", columnNames = "reference_no"))
public class Assignment extends BaseEntity {

    @Column(name = "reference_no", nullable = false, length = 40)
    private String referenceNo;

    @Column(name = "service_request_id", nullable = false)
    private UUID serviceRequestId;

    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    @Column(name = "helper_id")
    private UUID helperId;

    @Column(name = "vendor_id")
    private UUID vendorId;

    /** ASSIGNED|ACCEPTED|EN_ROUTE|ARRIVED|IN_PROGRESS|COMPLETED|DECLINED|CANCELLED|NO_SHOW|REWORK. */
    @Column(nullable = false, length = 20)
    private String status = "ASSIGNED";

    /** True while this is the current assignment for the request. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "scheduled_start")
    private LocalDateTime scheduledStart;
    @Column(name = "scheduled_end")
    private LocalDateTime scheduledEnd;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;
    @Column(name = "declined_at")
    private LocalDateTime declinedAt;
    @Column(name = "en_route_at")
    private LocalDateTime enRouteAt;
    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "completion_notes", length = 2000)
    private String completionNotes;
    @Column(name = "decline_reason", length = 1000)
    private String declineReason;
    @Column(name = "cancel_reason", length = 1000)
    private String cancelReason;
    @Column(name = "no_show_reason", length = 1000)
    private String noShowReason;
    @Column(name = "rework_reason", length = 1000)
    private String reworkReason;

    @Column(name = "previous_assignment_id")
    private UUID previousAssignmentId;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String v) { this.referenceNo = v; }
    public UUID getServiceRequestId() { return serviceRequestId; }
    public void setServiceRequestId(UUID v) { this.serviceRequestId = v; }
    public UUID getTechnicianId() { return technicianId; }
    public void setTechnicianId(UUID v) { this.technicianId = v; }
    public UUID getHelperId() { return helperId; }
    public void setHelperId(UUID v) { this.helperId = v; }
    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID v) { this.vendorId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public LocalDateTime getScheduledStart() { return scheduledStart; }
    public void setScheduledStart(LocalDateTime v) { this.scheduledStart = v; }
    public LocalDateTime getScheduledEnd() { return scheduledEnd; }
    public void setScheduledEnd(LocalDateTime v) { this.scheduledEnd = v; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime v) { this.assignedAt = v; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime v) { this.acceptedAt = v; }
    public LocalDateTime getDeclinedAt() { return declinedAt; }
    public void setDeclinedAt(LocalDateTime v) { this.declinedAt = v; }
    public LocalDateTime getEnRouteAt() { return enRouteAt; }
    public void setEnRouteAt(LocalDateTime v) { this.enRouteAt = v; }
    public LocalDateTime getArrivedAt() { return arrivedAt; }
    public void setArrivedAt(LocalDateTime v) { this.arrivedAt = v; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime v) { this.startedAt = v; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime v) { this.completedAt = v; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime v) { this.cancelledAt = v; }
    public String getCompletionNotes() { return completionNotes; }
    public void setCompletionNotes(String v) { this.completionNotes = v; }
    public String getDeclineReason() { return declineReason; }
    public void setDeclineReason(String v) { this.declineReason = v; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String v) { this.cancelReason = v; }
    public String getNoShowReason() { return noShowReason; }
    public void setNoShowReason(String v) { this.noShowReason = v; }
    public String getReworkReason() { return reworkReason; }
    public void setReworkReason(String v) { this.reworkReason = v; }
    public UUID getPreviousAssignmentId() { return previousAssignmentId; }
    public void setPreviousAssignmentId(UUID v) { this.previousAssignmentId = v; }
    public UUID getAssignedBy() { return assignedBy; }
    public void setAssignedBy(UUID v) { this.assignedBy = v; }
}
