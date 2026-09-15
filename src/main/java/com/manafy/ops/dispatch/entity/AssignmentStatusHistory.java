package com.manafy.ops.dispatch.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Insert-only assignment status history (Phase 4B §21). Rows are never updated or
 * deleted through normal APIs. Not a BaseEntity (no version/soft-delete) — it is an
 * append-only log, mirroring workforce_status_history.
 */
@Entity
@Table(name = "assignment_status_history")
public class AssignmentStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 20)
    private String toStatus;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(length = 1000)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public UUID getId() { return id; }
    public UUID getAssignmentId() { return assignmentId; }
    public void setAssignmentId(UUID v) { this.assignmentId = v; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String v) { this.fromStatus = v; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String v) { this.toStatus = v; }
    public UUID getChangedBy() { return changedBy; }
    public void setChangedBy(UUID v) { this.changedBy = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
