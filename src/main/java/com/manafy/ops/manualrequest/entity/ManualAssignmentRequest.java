package com.manafy.ops.manualrequest.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Manual (Field-Officer-coordinated) fulfillment request.
 *
 * This is the Ops record for resident requests — such as Recurring Helpers
 * (maid/cook/etc.) — that need a person to fulfill them but are NOT technician
 * dispatch. It is intentionally separate from {@code service_request}/
 * {@code assignment}/{@code field_visit}: it has NO technician FK and NEVER runs
 * technician eligibility/skill/certification. A Field Officer manually records the
 * chosen assignee (an opaque reference — Community helper/vendor, Ops workforce, or
 * external person — captured as type + ref/name/phone, never an FK to another DB).
 *
 * Cross-service references (source ids, community apartment/flat/customer ids) are
 * opaque strings — never database foreign keys — so Community stays the source of
 * truth for resident identity.
 *
 * Scope anchoring: area_id/region_id are nullable. When resolvable they enable
 * Field-Officer area scoping via ResourceScopeResolver + ScopeService; when null
 * (e.g. Community apartment not yet mapped to an Ops area) the request is visible to
 * GLOBAL-scoped operators (Admin/Ops Manager) only.
 */
@Entity
@Table(name = "manual_assignment_request",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_manual_request_reference", columnNames = "reference_no"),
                @UniqueConstraint(name = "uk_manual_request_source", columnNames = {"source_system", "source_type", "source_id"})
        })
public class ManualAssignmentRequest extends BaseEntity {

    @Column(name = "reference_no", nullable = false, length = 40)
    private String referenceNo;

    // ─── Source reference (cross-service, opaque; never FKs) ─────────
    @Column(name = "source_system", length = 40)
    private String sourceSystem;   // e.g. COMMUNITY

    @Column(name = "source_type", length = 60)
    private String sourceType;     // e.g. RECURRING_HELPER_REQUEST

    @Column(name = "source_id", length = 64)
    private String sourceId;       // the Community booking id (opaque string)

    @Column(name = "community_customer_id", length = 64)
    private String communityCustomerId;

    @Column(name = "community_apartment_id", length = 64)
    private String communityApartmentId;

    @Column(name = "community_flat_id", length = 64)
    private String communityFlatId;

    // ─── Scope anchors (nullable; area scoping when resolvable) ──────
    @Column(name = "area_id")
    private UUID areaId;

    @Column(name = "region_id")
    private UUID regionId;

    // ─── Request detail ──────────────────────────────────────────────
    /** MAID | COOK | DRIVER | NANNY | GARDENER | WATCHMAN | OTHER (free-form helper type). */
    @Column(name = "service_type", length = 40)
    private String serviceType;

    @Column(name = "service_title", length = 120)
    private String serviceTitle;

    /** ONE_TIME | DAILY | WEEKLY | MONTHLY. */
    @Column(length = 20)
    private String frequency;

    @Column(name = "start_date")
    private LocalDate startDate;

    /** MORNING | AFTERNOON | EVENING | FULL_DAY. */
    @Column(name = "time_slot", length = 20)
    private String timeSlot;

    @Column(name = "service_address", length = 400)
    private String serviceAddress;

    @Column(name = "resident_name", length = 120)
    private String residentName;

    @Column(name = "contact_number", length = 20)
    private String contactNumber;

    @Column(length = 2000)
    private String notes;

    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    /** NONE | DUMMY_PAID — mirrors Community; no real payment here. */
    @Column(name = "payment_status", length = 20)
    private String paymentStatus = "NONE";

    @Column(length = 20)
    private String priority = "MEDIUM";

    /** OPEN | QUEUED | ACKED_BY_FO | ASSIGNED | IN_PROGRESS | COMPLETED | CANCELLED. */
    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    /** The Ops user (Field Officer) who acknowledged/owns the request; null until acked. */
    @Column(name = "field_officer_id")
    private UUID fieldOfficerId;

    // ─── Manual assignee (opaque; NEVER a technician FK) ─────────────
    /** EXTERNAL_PERSON | COMMUNITY_HELPER_REF | COMMUNITY_VENDOR_REF | OPS_WORKFORCE. */
    @Column(name = "assignee_type", length = 30)
    private String assigneeType;

    @Column(name = "assignee_ref", length = 64)
    private String assigneeRef;

    @Column(name = "assignee_name", length = 120)
    private String assigneeName;

    @Column(name = "assignee_phone", length = 20)
    private String assigneePhone;

    @Column(name = "completion_notes", length = 1000)
    private String completionNotes;

    @Column(name = "cancel_reason", length = 1000)
    private String cancelReason;

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
    public String getCommunityFlatId() { return communityFlatId; }
    public void setCommunityFlatId(String v) { this.communityFlatId = v; }
    public UUID getAreaId() { return areaId; }
    public void setAreaId(UUID v) { this.areaId = v; }
    public UUID getRegionId() { return regionId; }
    public void setRegionId(UUID v) { this.regionId = v; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String v) { this.serviceType = v; }
    public String getServiceTitle() { return serviceTitle; }
    public void setServiceTitle(String v) { this.serviceTitle = v; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String v) { this.frequency = v; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate v) { this.startDate = v; }
    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String v) { this.timeSlot = v; }
    public String getServiceAddress() { return serviceAddress; }
    public void setServiceAddress(String v) { this.serviceAddress = v; }
    public String getResidentName() { return residentName; }
    public void setResidentName(String v) { this.residentName = v; }
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String v) { this.contactNumber = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String v) { this.paymentStatus = v; }
    public String getPriority() { return priority; }
    public void setPriority(String v) { this.priority = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public UUID getFieldOfficerId() { return fieldOfficerId; }
    public void setFieldOfficerId(UUID v) { this.fieldOfficerId = v; }
    public String getAssigneeType() { return assigneeType; }
    public void setAssigneeType(String v) { this.assigneeType = v; }
    public String getAssigneeRef() { return assigneeRef; }
    public void setAssigneeRef(String v) { this.assigneeRef = v; }
    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String v) { this.assigneeName = v; }
    public String getAssigneePhone() { return assigneePhone; }
    public void setAssigneePhone(String v) { this.assigneePhone = v; }
    public String getCompletionNotes() { return completionNotes; }
    public void setCompletionNotes(String v) { this.completionNotes = v; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String v) { this.cancelReason = v; }
}
