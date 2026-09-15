package com.manafy.ops.apartment.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "apartment_facility")
public class ApartmentFacility extends BaseEntity {

    @Column(name = "apartment_id", nullable = false)
    private UUID apartmentId;

    @Column(name = "building_id")
    private UUID buildingId;

    /** APARTMENT | BUILDING | COMMON_AREA. */
    @Column(name = "facility_scope", nullable = false, length = 20)
    private String facilityScope = "APARTMENT";

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 2000)
    private String metadata;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    public UUID getApartmentId() { return apartmentId; }
    public void setApartmentId(UUID apartmentId) { this.apartmentId = apartmentId; }
    public UUID getBuildingId() { return buildingId; }
    public void setBuildingId(UUID buildingId) { this.buildingId = buildingId; }
    public String getFacilityScope() { return facilityScope; }
    public void setFacilityScope(String facilityScope) { this.facilityScope = facilityScope; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
