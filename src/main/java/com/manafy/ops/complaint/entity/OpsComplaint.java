package com.manafy.ops.complaint.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Operational complaint visible to and worked by Ops users. Deliberately SEPARATE
 * from the technician dispatch path (service_request/assignment) and from the
 * manual_assignment_request queue.
 *
 * Community remains the source of truth for resident-facing society complaints;
 * this entity holds only the operational projection an Ops user acts on. Cross-
 * service references (source ids, community ids) are opaque strings — never FKs —
 * matching {@code manual_assignment_request} (§V12) and {@code ops_user}
 * community_customer_id (§V11). The assigned maid/helper is captured as an opaque
 * name/phone (never a workforce foreign key).
 */
@Entity
@Table(name = "ops_complaint",
        uniqueConstraints = @UniqueConstraint(name = "uk_ops_complaint_reference", columnNames = "reference_no"))
public class OpsComplaint extends BaseEntity {

    @Column(name = "reference_no", nullable = false, length = 40)
    private String referenceNo;

    // ─── Source reference (cross-service, opaque; never FKs) ─────────
    @Column(name = "source_system", length = 40)
    private String sourceSystem;

    @Column(name = "source_type", length = 60)
    private String sourceType;

    @Column(name = "source_id", length = 64)
    private String sourceId;

    @Column(name = "community_customer_id", length = 64)
    private String communityCustomerId;

    @Column(name = "community_apartment_id", length = 64)
    private String communityApartmentId;

    // ─── Scope anchors (nullable) ────────────────────────────────────
    @Column(name = "area_id")
    private UUID areaId;

    @Column(name = "region_id")
    private UUID regionId;

    // ─── Complaint detail ────────────────────────────────────────────
    /** MAID_NO_SHOW | WORK_NOT_DONE | MAID_LATE | DAMAGE | BEHAVIOR | BILLING | OTHER. */
    @Column(name = "complaint_type", nullable = false, length = 40)
    private String complaintType = "OTHER";

    @Column(length = 2000)
    private String description;

    /** OPEN | IN_PROGRESS | RESOLVED | CLOSED. */
    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(nullable = false, length = 20)
    private String priority = "MEDIUM";

    @Column(name = "customer_name", length = 120)
    private String customerName;

    @Column(name = "customer_mobile", length = 20)
    private String customerMobile;

    @Column(name = "assignee_ref", length = 64)
    private String assigneeRef;

    @Column(name = "assignee_name", length = 120)
    private String assigneeName;

    @Column(name = "assignee_mobile", length = 20)
    private String assigneeMobile;

    @Column(name = "booking_reference", length = 64)
    private String bookingReference;

    @Column(name = "admin_notes", length = 2000)
    private String adminNotes;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    // ─── Getters / setters ───────────────────────────────────────────
    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String v) { this.referenceNo = v; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String v) { this.sourceSystem = v; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String v) { this.sourceType = v; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }
    public String getCommunityCustomerId() { return communityCustomerId; }
    public void setCommunityCustomerId(String v) { this.communityCustomerId = v; }
    public String getCommunityApartmentId() { return communityApartmentId; }
    public void setCommunityApartmentId(String v) { this.communityApartmentId = v; }
    public UUID getAreaId() { return areaId; }
    public void setAreaId(UUID v) { this.areaId = v; }
    public UUID getRegionId() { return regionId; }
    public void setRegionId(UUID v) { this.regionId = v; }
    public String getComplaintType() { return complaintType; }
    public void setComplaintType(String v) { this.complaintType = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getPriority() { return priority; }
    public void setPriority(String v) { this.priority = v; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String v) { this.customerName = v; }
    public String getCustomerMobile() { return customerMobile; }
    public void setCustomerMobile(String v) { this.customerMobile = v; }
    public String getAssigneeRef() { return assigneeRef; }
    public void setAssigneeRef(String v) { this.assigneeRef = v; }
    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String v) { this.assigneeName = v; }
    public String getAssigneeMobile() { return assigneeMobile; }
    public void setAssigneeMobile(String v) { this.assigneeMobile = v; }
    public String getBookingReference() { return bookingReference; }
    public void setBookingReference(String v) { this.bookingReference = v; }
    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String v) { this.adminNotes = v; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime v) { this.resolvedAt = v; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime v) { this.closedAt = v; }
}
