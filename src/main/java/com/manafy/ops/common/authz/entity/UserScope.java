package com.manafy.ops.common.authz.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

/**
 * A scope grant for a user (Artifact #1 §1, spec §9). Combined with role
 * permissions during authorization: permission and scope are independent gates.
 *
 * {@code scopeType} is one of GLOBAL | REGION | AREA | APARTMENT | VENDOR | SELF |
 * ASSIGNED (validated by a CHECK constraint + application enum). The ref column
 * matching the type is set; GLOBAL/SELF/ASSIGNED carry no ref.
 *
 * NOTE (foundation): apartment_id and vendor_id columns exist for forward
 * compatibility but their FKs are added in later phases when those tables exist
 * (Artifact #1 §11 deferred-FK strategy). Region/area FKs are added in V4.
 */
@Entity
@Table(name = "user_scope")
public class UserScope extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "scope_type", nullable = false, length = 20)
    private String scopeType;

    @Column(name = "region_id")
    private UUID regionId;

    @Column(name = "area_id")
    private UUID areaId;

    @Column(name = "apartment_id")
    private UUID apartmentId;

    @Column(name = "vendor_id")
    private UUID vendorId;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public UUID getRegionId() { return regionId; }
    public void setRegionId(UUID regionId) { this.regionId = regionId; }
    public UUID getAreaId() { return areaId; }
    public void setAreaId(UUID areaId) { this.areaId = areaId; }
    public UUID getApartmentId() { return apartmentId; }
    public void setApartmentId(UUID apartmentId) { this.apartmentId = apartmentId; }
    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }
}
