package com.manafy.ops.workforce.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Technician (Phase 3 §6). Direct Manafy technician (vendorId null) or
 * vendor-associated. Employment {@code status} and {@code availabilityStatus} are
 * separate axes. No login identity in MVP (§7).
 */
@Entity
@Table(name = "technician", uniqueConstraints = @UniqueConstraint(name = "uk_technician_code", columnNames = "code"))
public class Technician extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "vendor_id")
    private UUID vendorId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 30)
    private String phone;          // PII

    @Column(length = 255)
    private String email;          // PII

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;  // PII

    @Column(name = "region_id")
    private UUID regionId;

    @Column(name = "service_radius_km", precision = 6, scale = 2)
    private BigDecimal serviceRadiusKm;

    @Column(name = "max_concurrent_jobs", nullable = false)
    private int maxConcurrentJobs = 1;

    @Column(name = "max_daily_jobs")
    private Integer maxDailyJobs;

    /** DRAFT|ACTIVE|INACTIVE|SUSPENDED|TERMINATED. */
    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    /** AVAILABLE|BUSY|OFFLINE|ON_LEAVE — separate from employment status. */
    @Column(name = "availability_status", nullable = false, length = 20)
    private String availabilityStatus = "OFFLINE";

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public UUID getRegionId() { return regionId; }
    public void setRegionId(UUID regionId) { this.regionId = regionId; }
    public BigDecimal getServiceRadiusKm() { return serviceRadiusKm; }
    public void setServiceRadiusKm(BigDecimal v) { this.serviceRadiusKm = v; }
    public int getMaxConcurrentJobs() { return maxConcurrentJobs; }
    public void setMaxConcurrentJobs(int v) { this.maxConcurrentJobs = v; }
    public Integer getMaxDailyJobs() { return maxDailyJobs; }
    public void setMaxDailyJobs(Integer v) { this.maxDailyJobs = v; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(String v) { this.availabilityStatus = v; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
