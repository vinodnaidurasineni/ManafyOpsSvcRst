package com.manafy.ops.common.authz.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

/**
 * A named role in Manafy's authorization model. Roles aggregate fine-grained
 * permissions; authorization is never based on the role string itself.
 */
@Entity
@Table(name = "role", uniqueConstraints = @UniqueConstraint(name = "uk_role_code", columnNames = "code"))
public class Role extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    /** Seed roles are protected system roles. */
    @Column(name = "is_system", nullable = false)
    private boolean system = true;

    /**
     * Role-assignability guard (design DD-06 / spec §119): the minimum assigner
     * role code permitted to grant this role. e.g. SUPER_ADMIN may grant anything;
     * MANAFY_ADMIN may grant all except SUPER_ADMIN.
     */
    @Column(name = "assignable_by_min_role", length = 50)
    private String assignableByMinRole;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isSystem() { return system; }
    public void setSystem(boolean system) { this.system = system; }
    public String getAssignableByMinRole() { return assignableByMinRole; }
    public void setAssignableByMinRole(String assignableByMinRole) { this.assignableByMinRole = assignableByMinRole; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
