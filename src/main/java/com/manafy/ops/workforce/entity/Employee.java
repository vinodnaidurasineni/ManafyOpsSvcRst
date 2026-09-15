package com.manafy.ops.workforce.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Internal Manafy staff (HR-owned). A Field Officer is an employee of type
 * FIELD_OFFICER linked to an ops_user identity (userId). Area↔FO ownership is NOT
 * stored here — it remains solely in area_field_officer (§15).
 */
@Entity
@Table(name = "employee", uniqueConstraints = @UniqueConstraint(name = "uk_employee_code", columnNames = "employee_code"))
public class Employee extends BaseEntity {

    @Column(name = "employee_code", nullable = false, length = 50)
    private String employeeCode;

    /** Login identity for internal employees / field officers. */
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 255)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 100)
    private String designation;

    /** STAFF | FIELD_OFFICER | MANAGER. */
    @Column(name = "employee_type", nullable = false, length = 30)
    private String employeeType = "STAFF";

    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public String getEmployeeType() { return employeeType; }
    public void setEmployeeType(String employeeType) { this.employeeType = employeeType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
