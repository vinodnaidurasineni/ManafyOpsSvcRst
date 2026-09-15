package com.manafy.ops.org.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

/**
 * A geographic operational area within a region (Artifact #1 §2).
 *
 * C-1 / DD-20: Field Officer ownership is NOT stored here. {@code area_field_officer}
 * is the single authoritative source of truth for area↔FO assignment. There are
 * deliberately no primary/secondary field-officer columns on this entity.
 */
@Entity
@Table(name = "area", uniqueConstraints = @UniqueConstraint(name = "uk_area_code", columnNames = "code"))
public class Area extends BaseEntity {

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    /** Comma-separated postal codes (dialect-neutral; avoids array types). */
    @Column(name = "postal_codes", length = 500)
    private String postalCodes;

    /** Reserved for a future polygon (stored as text/JSON string; PostGIS is future). */
    @Column(name = "geo_boundary")
    private String geoBoundary;

    @Column(nullable = false, length = 60)
    private String timezone = "Asia/Kolkata";

    /** ACTIVE | INACTIVE. */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    public UUID getRegionId() { return regionId; }
    public void setRegionId(UUID regionId) { this.regionId = regionId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPostalCodes() { return postalCodes; }
    public void setPostalCodes(String postalCodes) { this.postalCodes = postalCodes; }
    public String getGeoBoundary() { return geoBoundary; }
    public void setGeoBoundary(String geoBoundary) { this.geoBoundary = geoBoundary; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
