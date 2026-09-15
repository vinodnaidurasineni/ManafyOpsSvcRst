package com.manafy.ops.apartment.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "apartment_building",
        uniqueConstraints = @UniqueConstraint(name = "uk_building_apartment_name", columnNames = {"apartment_id", "name"}))
public class ApartmentBuilding extends BaseEntity {

    @Column(name = "apartment_id", nullable = false)
    private UUID apartmentId;

    @Column(nullable = false, length = 100)
    private String name;

    private Integer floors;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    public UUID getApartmentId() { return apartmentId; }
    public void setApartmentId(UUID apartmentId) { this.apartmentId = apartmentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getFloors() { return floors; }
    public void setFloors(Integer floors) { this.floors = floors; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
