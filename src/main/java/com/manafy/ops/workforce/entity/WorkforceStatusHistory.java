package com.manafy.ops.workforce.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Insert-only workforce lifecycle transition history (Phase 3 §4). Never updated. */
@Entity
@Table(name = "workforce_status_history")
public class WorkforceStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workforce_kind", nullable = false, length = 20)
    private String workforceKind;

    @Column(name = "workforce_id", nullable = false)
    private UUID workforceId;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(length = 1000)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getWorkforceKind() { return workforceKind; }
    public void setWorkforceKind(String workforceKind) { this.workforceKind = workforceKind; }
    public UUID getWorkforceId() { return workforceId; }
    public void setWorkforceId(UUID workforceId) { this.workforceId = workforceId; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public UUID getChangedBy() { return changedBy; }
    public void setChangedBy(UUID changedBy) { this.changedBy = changedBy; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
