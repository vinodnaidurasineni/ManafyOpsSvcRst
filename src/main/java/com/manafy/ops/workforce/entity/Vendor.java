package com.manafy.ops.workforce.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** External vendor organization (Phase 3 §9). Distinct from a technician. */
@Entity
@Table(name = "vendor", uniqueConstraints = @UniqueConstraint(name = "uk_vendor_code", columnNames = "code"))
public class Vendor extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "registration_no", length = 100)
    private String registrationNo;

    @Column(length = 255)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 500)
    private String address;

    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getRegistrationNo() { return registrationNo; }
    public void setRegistrationNo(String registrationNo) { this.registrationNo = registrationNo; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
