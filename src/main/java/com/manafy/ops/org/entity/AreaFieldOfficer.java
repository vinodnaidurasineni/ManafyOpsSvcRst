package com.manafy.ops.org.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AUTHORITATIVE Area ↔ Field Officer assignment (C-1 resolution, DD-20).
 *
 * Single source of truth for which Field Officer is responsible for which area,
 * including primary/secondary designation and full history. Field Officer
 * authorization scope (AREA_RESPONSIBLE) is derived exclusively from the CURRENT
 * rows here (effectiveTo IS NULL).
 *
 * Reassignment closes the current row (effectiveTo = now()) and inserts a new one;
 * rows are never deleted, preserving history.
 */
@Entity
@Table(name = "area_field_officer")
public class AreaFieldOfficer extends BaseEntity {

    @Column(name = "area_id", nullable = false)
    private UUID areaId;

    @Column(name = "field_officer_id", nullable = false)
    private UUID fieldOfficerId;

    /** PRIMARY | SECONDARY (replaces the removed areas.primary/secondary columns). */
    @Column(nullable = false, length = 20)
    private String designation = "PRIMARY";

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom = LocalDateTime.now();

    /** Null => current/active assignment. */
    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    public UUID getAreaId() { return areaId; }
    public void setAreaId(UUID areaId) { this.areaId = areaId; }
    public UUID getFieldOfficerId() { return fieldOfficerId; }
    public void setFieldOfficerId(UUID fieldOfficerId) { this.fieldOfficerId = fieldOfficerId; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDateTime getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDateTime effectiveTo) { this.effectiveTo = effectiveTo; }
    public UUID getAssignedBy() { return assignedBy; }
    public void setAssignedBy(UUID assignedBy) { this.assignedBy = assignedBy; }
}
