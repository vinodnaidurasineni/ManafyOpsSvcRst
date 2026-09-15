package com.manafy.ops.dispatch.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Field visit tied to an assignment (Phase 4B §16). Connects service request +
 * assignment + technician + apartment. Tracks the visit's own schedule/arrival/
 * departure/outcome without duplicating assignment lifecycle state.
 */
@Entity
@Table(name = "field_visit")
public class FieldVisit extends BaseEntity {

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "service_request_id", nullable = false)
    private UUID serviceRequestId;

    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    @Column(name = "apartment_id", nullable = false)
    private UUID apartmentId;

    @Column(name = "scheduled_start")
    private LocalDateTime scheduledStart;
    @Column(name = "scheduled_end")
    private LocalDateTime scheduledEnd;
    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;
    @Column(name = "departed_at")
    private LocalDateTime departedAt;

    /** SCHEDULED|IN_PROGRESS|COMPLETED|CANCELLED|NO_SHOW. */
    @Column(nullable = false, length = 20)
    private String status = "SCHEDULED";

    /** RESOLVED|PARTIAL|UNRESOLVED|REWORK_REQUIRED. */
    @Column(length = 30)
    private String outcome;

    @Column(length = 2000)
    private String notes;

    public UUID getAssignmentId() { return assignmentId; }
    public void setAssignmentId(UUID v) { this.assignmentId = v; }
    public UUID getServiceRequestId() { return serviceRequestId; }
    public void setServiceRequestId(UUID v) { this.serviceRequestId = v; }
    public UUID getTechnicianId() { return technicianId; }
    public void setTechnicianId(UUID v) { this.technicianId = v; }
    public UUID getApartmentId() { return apartmentId; }
    public void setApartmentId(UUID v) { this.apartmentId = v; }
    public LocalDateTime getScheduledStart() { return scheduledStart; }
    public void setScheduledStart(LocalDateTime v) { this.scheduledStart = v; }
    public LocalDateTime getScheduledEnd() { return scheduledEnd; }
    public void setScheduledEnd(LocalDateTime v) { this.scheduledEnd = v; }
    public LocalDateTime getArrivedAt() { return arrivedAt; }
    public void setArrivedAt(LocalDateTime v) { this.arrivedAt = v; }
    public LocalDateTime getDepartedAt() { return departedAt; }
    public void setDepartedAt(LocalDateTime v) { this.departedAt = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String v) { this.outcome = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
}
