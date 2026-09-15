package com.manafy.ops.workforce.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Helper (Phase 3 §8). Relationship (MANAFY|TECHNICIAN|VENDOR) is explicit. */
@Entity
@Table(name = "helper", uniqueConstraints = @UniqueConstraint(name = "uk_helper_code", columnNames = "code"))
public class Helper extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 30)
    private String phone;

    /** MANAFY | TECHNICIAN | VENDOR. */
    @Column(nullable = false, length = 20)
    private String relationship = "MANAFY";

    @Column(name = "technician_id")
    private UUID technicianId;

    @Column(name = "vendor_id")
    private UUID vendorId;

    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }
    public UUID getTechnicianId() { return technicianId; }
    public void setTechnicianId(UUID technicianId) { this.technicianId = technicianId; }
    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
