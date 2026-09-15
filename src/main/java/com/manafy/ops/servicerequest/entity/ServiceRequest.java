package com.manafy.ops.servicerequest.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service Request core record (Phase 4A foundation).
 *
 * Scope anchors (apartment/area/region) are persisted here, denormalized from the
 * apartment at create time, so authorization/IDOR checks resolve from server-side
 * columns via {@code ResourceScopeResolver.serviceRequest(...)} — reusing the
 * Phase 2 apartment scope model. Lifecycle is intentionally limited to
 * NEW -> CANCELLED in this phase (assignment/dispatch/etc. are deferred).
 */
@Entity
@Table(name = "service_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_service_request_reference", columnNames = "reference_no"))
public class ServiceRequest extends BaseEntity {

    @Column(name = "reference_no", nullable = false, length = 40)
    private String referenceNo;

    @Column(name = "apartment_id", nullable = false)
    private UUID apartmentId;

    @Column(name = "area_id", nullable = false)
    private UUID areaId;

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "requester_user_id", nullable = false)
    private UUID requesterUserId;

    /** PLUMBING|ELECTRICAL|HVAC|CLEANING|SECURITY|GENERAL|OTHER. */
    @Column(nullable = false, length = 40)
    private String category;

    /** LOW|MEDIUM|HIGH|URGENT. */
    @Column(nullable = false, length = 20)
    private String priority = "MEDIUM";

    @Column(nullable = false, length = 2000)
    private String description;

    /** NEW|CANCELLED (Phase 4A). */
    @Column(nullable = false, length = 20)
    private String status = "NEW";

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 1000)
    private String cancelReason;

    /** Points to the currently-active assignment (Phase 4B); null when unassigned. */
    @Column(name = "active_assignment_id")
    private UUID activeAssignmentId;

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }
    public UUID getApartmentId() { return apartmentId; }
    public void setApartmentId(UUID apartmentId) { this.apartmentId = apartmentId; }
    public UUID getAreaId() { return areaId; }
    public void setAreaId(UUID areaId) { this.areaId = areaId; }
    public UUID getRegionId() { return regionId; }
    public void setRegionId(UUID regionId) { this.regionId = regionId; }
    public UUID getRequesterUserId() { return requesterUserId; }
    public void setRequesterUserId(UUID requesterUserId) { this.requesterUserId = requesterUserId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public UUID getActiveAssignmentId() { return activeAssignmentId; }
    public void setActiveAssignmentId(UUID activeAssignmentId) { this.activeAssignmentId = activeAssignmentId; }
}
