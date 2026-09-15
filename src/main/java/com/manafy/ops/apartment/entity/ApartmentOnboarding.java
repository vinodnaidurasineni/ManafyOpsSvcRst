package com.manafy.ops.apartment.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Apartment onboarding lifecycle (1:1 with apartment).
 * Status: DRAFT → SUBMITTED → UNDER_REVIEW → (REJECTED → RESUBMITTED → …) →
 * VERIFIED → ACTIVE; SUSPENDED is a post-active state mirrored from the apartment.
 */
@Entity
@Table(name = "apartment_onboarding",
        uniqueConstraints = @UniqueConstraint(name = "uk_onboarding_apartment", columnNames = "apartment_id"))
public class ApartmentOnboarding extends BaseEntity {

    @Column(name = "apartment_id", nullable = false)
    private UUID apartmentId;

    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "started_by")
    private UUID startedBy;
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;
    @Column(name = "verified_by")
    private UUID verifiedBy;
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
    @Column(name = "rejected_by")
    private UUID rejectedBy;
    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;
    @Column(name = "resubmitted_at")
    private LocalDateTime resubmittedAt;

    public UUID getApartmentId() { return apartmentId; }
    public void setApartmentId(UUID apartmentId) { this.apartmentId = apartmentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getStartedBy() { return startedBy; }
    public void setStartedBy(UUID startedBy) { this.startedBy = startedBy; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public UUID getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(UUID verifiedBy) { this.verifiedBy = verifiedBy; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public UUID getRejectedBy() { return rejectedBy; }
    public void setRejectedBy(UUID rejectedBy) { this.rejectedBy = rejectedBy; }
    public LocalDateTime getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(LocalDateTime rejectedAt) { this.rejectedAt = rejectedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public LocalDateTime getResubmittedAt() { return resubmittedAt; }
    public void setResubmittedAt(LocalDateTime resubmittedAt) { this.resubmittedAt = resubmittedAt; }
}
