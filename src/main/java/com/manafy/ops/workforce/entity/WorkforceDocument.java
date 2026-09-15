package com.manafy.ops.workforce.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Workforce document metadata (Phase 3 §18). Bytes in object storage. docCategory
 * (GENERAL|KYC|FINANCE) drives authorization: KYC and FINANCE are separately gated.
 */
@Entity
@Table(name = "workforce_document")
public class WorkforceDocument extends BaseEntity {

    @Column(name = "workforce_kind", nullable = false, length = 20)
    private String workforceKind;   // TECHNICIAN | HELPER | VENDOR | EMPLOYEE

    @Column(name = "technician_id")
    private UUID technicianId;
    @Column(name = "helper_id")
    private UUID helperId;
    @Column(name = "vendor_id")
    private UUID vendorId;
    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(name = "doc_type", nullable = false, length = 50)
    private String docType;

    /** GENERAL | KYC | FINANCE. */
    @Column(name = "doc_category", nullable = false, length = 20)
    private String docCategory = "GENERAL";

    @Column(name = "file_name", length = 255)
    private String fileName;
    @Column(name = "object_key", length = 500)
    private String objectKey;
    @Column(name = "content_type", length = 100)
    private String contentType;
    @Column(name = "size_bytes")
    private Long sizeBytes;
    @Column(name = "reference_no", length = 100)
    private String referenceNo;
    @Column(name = "issued_date")
    private LocalDate issuedDate;
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(nullable = false, length = 20)
    private String status = "UPLOADED";
    @Column(name = "verified_by")
    private UUID verifiedBy;
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    public String getWorkforceKind() { return workforceKind; }
    public void setWorkforceKind(String workforceKind) { this.workforceKind = workforceKind; }
    public UUID getTechnicianId() { return technicianId; }
    public void setTechnicianId(UUID technicianId) { this.technicianId = technicianId; }
    public UUID getHelperId() { return helperId; }
    public void setHelperId(UUID helperId) { this.helperId = helperId; }
    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public String getDocCategory() { return docCategory; }
    public void setDocCategory(String docCategory) { this.docCategory = docCategory; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }
    public LocalDate getIssuedDate() { return issuedDate; }
    public void setIssuedDate(LocalDate issuedDate) { this.issuedDate = issuedDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(UUID verifiedBy) { this.verifiedBy = verifiedBy; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
