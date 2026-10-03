package com.manafy.ops.gate.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

/** A registered household-staff gate pass (ported from ManafySvcRst gate domain). */
@Entity
@Table(name = "gate_staff_pass", indexes = {
        @Index(name = "idx_gstaff_apartment_mobile", columnList = "apartment_id,staff_mobile")
})
public class StaffPass extends BaseEntity {

    @Column(name = "apartment_id", nullable = false)
    private UUID apartmentId;

    @Column(name = "flat_id")
    private UUID flatId;

    @Column(name = "staff_name", nullable = false, length = 150)
    private String staffName;

    @Column(name = "staff_mobile", nullable = false, length = 30)
    private String staffMobile;

    @Column(name = "staff_type", length = 30)
    private String staffType; // MAID, COOK, DRIVER, NANNY, OTHER

    @Column(name = "flat_number", length = 50)
    private String flatNumber;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE"; // ACTIVE, PAUSED, EXPIRED

    public UUID getApartmentId() { return apartmentId; }
    public void setApartmentId(UUID apartmentId) { this.apartmentId = apartmentId; }
    public UUID getFlatId() { return flatId; }
    public void setFlatId(UUID flatId) { this.flatId = flatId; }
    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }
    public String getStaffMobile() { return staffMobile; }
    public void setStaffMobile(String staffMobile) { this.staffMobile = staffMobile; }
    public String getStaffType() { return staffType; }
    public void setStaffType(String staffType) { this.staffType = staffType; }
    public String getFlatNumber() { return flatNumber; }
    public void setFlatNumber(String flatNumber) { this.flatNumber = flatNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
