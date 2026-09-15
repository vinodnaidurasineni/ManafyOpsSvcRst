package com.manafy.ops.apartment.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "apartment_unit",
        uniqueConstraints = @UniqueConstraint(name = "uk_unit_building_number", columnNames = {"building_id", "unit_number"}))
public class ApartmentUnit extends BaseEntity {

    @Column(name = "building_id", nullable = false)
    private UUID buildingId;

    /** Denormalized for scope filtering / IDOR resolution. */
    @Column(name = "apartment_id", nullable = false)
    private UUID apartmentId;

    @Column(name = "unit_number", nullable = false, length = 50)
    private String unitNumber;

    private Integer floor;

    @Column(name = "unit_type", length = 40)
    private String unitType;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    public UUID getBuildingId() { return buildingId; }
    public void setBuildingId(UUID buildingId) { this.buildingId = buildingId; }
    public UUID getApartmentId() { return apartmentId; }
    public void setApartmentId(UUID apartmentId) { this.apartmentId = apartmentId; }
    public String getUnitNumber() { return unitNumber; }
    public void setUnitNumber(String unitNumber) { this.unitNumber = unitNumber; }
    public Integer getFloor() { return floor; }
    public void setFloor(Integer floor) { this.floor = floor; }
    public String getUnitType() { return unitType; }
    public void setUnitType(String unitType) { this.unitType = unitType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
