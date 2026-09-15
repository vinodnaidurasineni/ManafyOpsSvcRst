package com.manafy.ops.common.security;

import com.manafy.ops.common.authz.entity.Permission;
import com.manafy.ops.common.authz.entity.Role;
import com.manafy.ops.common.authz.repository.PermissionRepository;
import com.manafy.ops.common.authz.repository.RolePermissionRepository;
import com.manafy.ops.common.authz.repository.RoleRepository;
import com.manafy.ops.common.authz.repository.UserRoleRepository;
import com.manafy.ops.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves an Ops user's effective permissions from the normalized model:
 *   user_role (ACTIVE) → role → role_permission → permission.code
 *
 * ALL authorization is permission-based (fine-grained), NEVER role-string based.
 * There is intentionally no {@code if (role == "ADMIN")} anywhere.
 */
@Service
public class PermissionService {

    private final UserRoleRepository userRoleRepo;
    private final RoleRepository roleRepo;
    private final RolePermissionRepository rolePermissionRepo;
    private final PermissionRepository permissionRepo;

    public PermissionService(UserRoleRepository userRoleRepo, RoleRepository roleRepo,
                             RolePermissionRepository rolePermissionRepo, PermissionRepository permissionRepo) {
        this.userRoleRepo = userRoleRepo;
        this.roleRepo = roleRepo;
        this.rolePermissionRepo = rolePermissionRepo;
        this.permissionRepo = permissionRepo;
    }

    /** Effective role codes (ACTIVE assignments only). */
    public Set<String> effectiveRoleCodes(UUID userId) {
        Set<String> codes = new HashSet<>();
        for (var ur : userRoleRepo.findByUserIdAndStatusAndDeletedFalse(userId, "ACTIVE")) {
            roleRepo.findById(ur.getRoleId())
                    .filter(r -> !r.isDeleted())
                    .ifPresent(r -> codes.add(r.getCode()));
        }
        return codes;
    }

    /** Effective permission codes, unioned across all active roles. */
    public Set<String> effectivePermissionCodes(UUID userId) {
        Set<String> perms = new HashSet<>();
        for (var ur : userRoleRepo.findByUserIdAndStatusAndDeletedFalse(userId, "ACTIVE")) {
            Optional<Role> role = roleRepo.findById(ur.getRoleId()).filter(r -> !r.isDeleted());
            if (role.isEmpty()) continue;
            for (var rp : rolePermissionRepo.findByRoleIdAndDeletedFalse(role.get().getId())) {
                permissionRepo.findById(rp.getPermissionId())
                        .filter(p -> !p.isDeleted())
                        .map(Permission::getCode)
                        .ifPresent(perms::add);
            }
        }
        return perms;
    }

    public boolean hasPermission(UUID userId, String permissionCode) {
        return effectivePermissionCodes(userId).contains(permissionCode);
    }

    public boolean hasAnyPermission(UUID userId, String... permissionCodes) {
        Set<String> perms = effectivePermissionCodes(userId);
        for (String code : permissionCodes) {
            if (perms.contains(code)) return true;
        }
        return false;
    }

    /** @throws BusinessException 403 FORBIDDEN if the user lacks the permission. */
    public void requirePermission(UUID userId, String permissionCode) {
        if (!hasPermission(userId, permissionCode)) {
            throw BusinessException.forbidden("Missing permission: " + permissionCode);
        }
    }

    public List<String> effectivePermissionList(UUID userId) {
        return effectivePermissionCodes(userId).stream().sorted().toList();
    }
}
