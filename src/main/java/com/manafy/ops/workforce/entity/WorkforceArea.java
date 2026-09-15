package com.manafy.ops.workforce.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

/** Area coverage for a workforce member (technician / helper / vendor). */
@Entity
@Table(name = "workforce_area")
public class WorkforceArea extends BaseEntity {

    @Column(name = "workforce_kind", nullable = false, length = 20)
    private String workforceKind;   // TECHNICIAN | HELPER | VENDOR

    @Column(name = "technician_id")
    private UUID technicianId;

    @Column(name = "helper_id")
    private UUID helperId;

    @Column(name = "vendor_id")
    private UUID vendorId;

    @Column(name = "area_id", nullable = false)
    private UUID areaId;

    public String getWorkforceKind() { return workforceKind; }
    public void setWorkforceKind(String workforceKind) { this.workforceKind = workforceKind; }
    public UUID getTechnicianId() { return technicianId; }
    public void setTechnicianId(UUID technicianId) { this.technicianId = technicianId; }
    public UUID getHelperId() { return helperId; }
    public void setHelperId(UUID helperId) { this.helperId = helperId; }
    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }
    public UUID getAreaId() { return areaId; }
    public void setAreaId(UUID areaId) { this.areaId = areaId; }
}
