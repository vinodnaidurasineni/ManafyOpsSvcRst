package com.manafy.ops.apartment.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Apartment document metadata (Artifact #1 §3/§52). Bytes live in object storage;
 * only metadata + object_key are persisted (no second storage abstraction).
 */
@Entity
@Table(name = "apartment_document")
public class ApartmentDocument extends BaseEntity {

    @Column(name = "apartment_id", nullable = false)
    private UUID apartmentId;

    @Column(name = "doc_type", nullable = false, length = 50)
    private String docType;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "object_key", length = 500)
    private String objectKey;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** UPLOADED | UNDER_REVIEW | VERIFIED | REJECTED | EXPIRED. */
    @Column(nullable = false, length = 20)
    private String status = "UPLOADED";

    @Column(name = "verified_by")
    private UUID verifiedBy;
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    public UUID getApartmentId() { return apartmentId; }
    public void setApartmentId(UUID apartmentId) { this.apartmentId = apartmentId; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(UUID verifiedBy) { this.verifiedBy = verifiedBy; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
