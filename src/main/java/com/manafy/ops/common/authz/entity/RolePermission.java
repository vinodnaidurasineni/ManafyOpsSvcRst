package com.manafy.ops.common.authz.entity;

import com.manafy.ops.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

/** Many-to-many mapping of Role → Permission. */
@Entity
@Table(name = "role_permission", uniqueConstraints =
        @UniqueConstraint(name = "uk_role_permission", columnNames = {"role_id", "permission_id"}))
public class RolePermission extends BaseEntity {

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    public UUID getRoleId() { return roleId; }
    public void setRoleId(UUID roleId) { this.roleId = roleId; }
    public UUID getPermissionId() { return permissionId; }
    public void setPermissionId(UUID permissionId) { this.permissionId = permissionId; }
}
