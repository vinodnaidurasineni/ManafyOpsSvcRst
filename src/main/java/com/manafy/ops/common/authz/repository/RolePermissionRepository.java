package com.manafy.ops.common.authz.repository;

import com.manafy.ops.common.authz.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {
    List<RolePermission> findByRoleIdAndDeletedFalse(UUID roleId);
    boolean existsByRoleIdAndPermissionIdAndDeletedFalse(UUID roleId, UUID permissionId);
}
