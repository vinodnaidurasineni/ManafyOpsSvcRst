package com.manafy.ops.workforce.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** People associated with a vendor (Phase 3 §10). May reference a technician/helper. */
@Entity
@Table(name = "vendor_staff")
public class VendorStaff extends BaseEntity {

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "technician_id")
    private UUID technicianId;

    @Column(name = "helper_id")
    private UUID helperId;

    @Column(name = "staff_name", length = 150)
    private String staffName;

    @Column(name = "role_title", length = 100)
    private String roleTitle;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }
    public UUID getTechnicianId() { return technicianId; }
    public void setTechnicianId(UUID technicianId) { this.technicianId = technicianId; }
    public UUID getHelperId() { return helperId; }
    public void setHelperId(UUID helperId) { this.helperId = helperId; }
    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }
    public String getRoleTitle() { return roleTitle; }
    public void setRoleTitle(String roleTitle) { this.roleTitle = roleTitle; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
    public LocalDateTime getLeftAt() { return leftAt; }
    public void setLeftAt(LocalDateTime leftAt) { this.leftAt = leftAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
