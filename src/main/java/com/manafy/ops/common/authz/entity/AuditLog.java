package com.manafy.ops.common.authz.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable audit record (design DD-09/DD-10, Artifact #1 §8, spec §48).
 *
 * Append-only: this entity deliberately does NOT extend BaseEntity — it has no
 * {@code updatedAt}, no soft-delete, and no version, because audit rows are never
 * updated or deleted through the application. DB-level immutability (REVOKE
 * UPDATE/DELETE) is applied in the PostgreSQL production profile (V6 / prod).
 *
 * Captures actor, role, action, resource, before/after state, reason, source, ip,
 * user agent, correlation id and timestamp.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_role", length = 100)
    private String actorRole;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "resource_type", length = 60)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    /** Serialized before/after state (JSON string). Never contains secrets. */
    @Column(name = "before_state", length = 4000)
    private String beforeState;

    @Column(name = "after_state", length = 4000)
    private String afterState;

    @Column(length = 500)
    private String reason;

    /** API | SYSTEM | JOB */
    @Column(length = 20)
    private String source;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getActorUserId() { return actorUserId; }
    public void setActorUserId(UUID actorUserId) { this.actorUserId = actorUserId; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public UUID getResourceId() { return resourceId; }
    public void setResourceId(UUID resourceId) { this.resourceId = resourceId; }
    public String getBeforeState() { return beforeState; }
    public void setBeforeState(String beforeState) { this.beforeState = beforeState; }
    public String getAfterState() { return afterState; }
    public void setAfterState(String afterState) { this.afterState = afterState; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
