package com.manafy.ops.workforce.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

/** Workforce certification (Phase 3 §12). */
@Entity
@Table(name = "certification")
public class Certification extends BaseEntity {

    @Column(name = "workforce_kind", nullable = false, length = 20)
    private String workforceKind;   // TECHNICIAN | HELPER

    @Column(name = "technician_id")
    private UUID technicianId;

    @Column(name = "helper_id")
    private UUID helperId;

    @Column(name = "cert_type", nullable = false, length = 100)
    private String certType;

    @Column(name = "issuing_authority", length = 150)
    private String issuingAuthority;

    @Column(name = "reference_no", length = 100)
    private String referenceNo;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** ACTIVE | EXPIRED | REVOKED. */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    public String getWorkforceKind() { return workforceKind; }
    public void setWorkforceKind(String workforceKind) { this.workforceKind = workforceKind; }
    public UUID getTechnicianId() { return technicianId; }
    public void setTechnicianId(UUID technicianId) { this.technicianId = technicianId; }
    public UUID getHelperId() { return helperId; }
    public void setHelperId(UUID helperId) { this.helperId = helperId; }
    public String getCertType() { return certType; }
    public void setCertType(String certType) { this.certType = certType; }
    public String getIssuingAuthority() { return issuingAuthority; }
    public void setIssuingAuthority(String issuingAuthority) { this.issuingAuthority = issuingAuthority; }
    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }
    public LocalDate getIssuedDate() { return issuedDate; }
    public void setIssuedDate(LocalDate issuedDate) { this.issuedDate = issuedDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
