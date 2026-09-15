package com.manafy.ops.apartment.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Apartment / community master (Artifact #1 §3, Phase 2).
 *
 * Field Officer authorization is AREA-derived (via area_field_officer, DD-20).
 * {@code assignedFieldOfficerId} is an OPTIONAL override only (DD-46) and never
 * grants cross-area access — enforced in the service layer.
 */
@Entity
@Table(name = "apartment", uniqueConstraints = @UniqueConstraint(name = "uk_apartment_code", columnNames = "code"))
public class Apartment extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "area_id", nullable = false)
    private UUID areaId;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;
    @Column(name = "address_line2", length = 255)
    private String addressLine2;
    @Column(length = 100)
    private String city;
    @Column(length = 100)
    private String state;
    @Column(nullable = false, length = 80)
    private String country = "India";
    @Column(length = 20)
    private String pincode;
    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;
    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    /** PROSPECT|ONBOARDING|PENDING_VERIFICATION|READY_FOR_ACTIVATION|ACTIVE|SUSPENDED|INACTIVE|TERMINATED. */
    @Column(nullable = false, length = 30)
    private String status = "PROSPECT";

    @Column(name = "management_company", length = 200)
    private String managementCompany;

    @Column(nullable = false, length = 60)
    private String timezone = "Asia/Kolkata";

    /** Optional per-apartment override (DD-46). Never grants cross-area access. */
    @Column(name = "assigned_field_officer_id")
    private UUID assignedFieldOfficerId;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;
    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public UUID getRegionId() { return regionId; }
    public void setRegionId(UUID regionId) { this.regionId = regionId; }
    public UUID getAreaId() { return areaId; }
    public void setAreaId(UUID areaId) { this.areaId = areaId; }
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String v) { this.addressLine1 = v; }
    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String v) { this.addressLine2 = v; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getManagementCompany() { return managementCompany; }
    public void setManagementCompany(String v) { this.managementCompany = v; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public UUID getAssignedFieldOfficerId() { return assignedFieldOfficerId; }
    public void setAssignedFieldOfficerId(UUID v) { this.assignedFieldOfficerId = v; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(LocalDateTime v) { this.activatedAt = v; }
    public LocalDateTime getSuspendedAt() { return suspendedAt; }
    public void setSuspendedAt(LocalDateTime v) { this.suspendedAt = v; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime v) { this.deletedAt = v; }
}
