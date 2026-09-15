package com.manafy.ops.common.authz.controller;

import com.manafy.ops.common.authz.dto.AuthzDtos.*;
import com.manafy.ops.common.authz.entity.Permission;
import com.manafy.ops.common.authz.entity.Role;
import com.manafy.ops.common.authz.entity.RolePermission;
import com.manafy.ops.common.authz.repository.PermissionRepository;
import com.manafy.ops.common.authz.repository.RolePermissionRepository;
import com.manafy.ops.common.authz.repository.RoleRepository;
import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.security.AuditService;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.common.security.AuthorizationService;
import com.manafy.ops.common.security.PermissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Role administration (Artifact #3 §1). */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleRepository roleRepo;
    private final PermissionRepository permissionRepo;
    private final RolePermissionRepository rolePermissionRepo;
    private final AuthenticationContext authContext;
    private final AuthorizationService authz;
    private final PermissionService permissionService;
    private final AuditService audit;

    public RoleController(RoleRepository roleRepo, PermissionRepository permissionRepo,
                          RolePermissionRepository rolePermissionRepo, AuthenticationContext authContext,
                          AuthorizationService authz, PermissionService permissionService, AuditService audit) {
        this.roleRepo = roleRepo;
        this.permissionRepo = permissionRepo;
        this.rolePermissionRepo = rolePermissionRepo;
        this.authContext = authContext;
        this.authz = authz;
        this.permissionService = permissionService;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<RoleResponse>> list() {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "ROLE_VIEW");
        return ApiResponse.ok(roleRepo.findByDeletedFalse().stream().map(this::toResponse).toList());
    }

    @PostMapping
    public ApiResponse<RoleResponse> create(@Valid @RequestBody CreateRoleRequest req) {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "ROLE_CREATE");
        if (roleRepo.existsByCode(req.code())) {
            throw new BusinessException("RESOURCE_CONFLICT", "Role code already exists", HttpStatus.CONFLICT);
        }
        Role r = new Role();
        r.setCode(req.code());
        r.setName(req.name());
        r.setDescription(req.description());
        r.setAssignableByMinRole(req.assignableByMinRole());
        r.setSystem(false);
        Role saved = roleRepo.save(r);
        audit.audit(actor, primaryRole(actor), "ROLE_CREATED", "ROLE", saved.getId(), null, null,
                "Role created: " + req.code());
        return ApiResponse.ok(toResponse(saved));
    }

    @PatchMapping("/{id}")
    public ApiResponse<RoleResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest req) {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "ROLE_UPDATE");
        Role r = roleRepo.findById(id).filter(x -> !x.isDeleted())
                .orElseThrow(() -> BusinessException.notFound("Role not found"));
        if (req.version() != null && req.version() != r.getVersion()) {
            throw new BusinessException("CONCURRENCY_CONFLICT", "Role modified concurrently", HttpStatus.CONFLICT);
        }
        if (req.name() != null) r.setName(req.name());
        if (req.description() != null) r.setDescription(req.description());
        if (req.assignableByMinRole() != null) r.setAssignableByMinRole(req.assignableByMinRole());
        Role saved = roleRepo.save(r);
        audit.audit(actor, primaryRole(actor), "ROLE_UPDATED", "ROLE", saved.getId(), null, null, "Role updated");
        return ApiResponse.ok(toResponse(saved));
    }

    @PostMapping("/{id}/permissions")
    public ApiResponse<Void> addPermission(@PathVariable UUID id, @Valid @RequestBody RolePermissionRequest req) {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "PERMISSION_CHANGE");
        Role role = roleRepo.findById(id).filter(x -> !x.isDeleted())
                .orElseThrow(() -> BusinessException.notFound("Role not found"));
        Permission perm = permissionRepo.findByCodeAndDeletedFalse(req.permissionCode())
                .orElseThrow(() -> BusinessException.notFound("Permission not found: " + req.permissionCode()));

        // A user can never grant a permission they do not themselves hold (spec §118.9),
        // unless they are effectively SUPER_ADMIN.
        if (!authContext.roles(actor).contains("SUPER_ADMIN")
                && !permissionService.hasPermission(actor, perm.getCode())) {
            throw new BusinessException("PERMISSION_NOT_GRANTABLE",
                    "You cannot grant a permission you do not hold: " + perm.getCode(), HttpStatus.FORBIDDEN);
        }

        if (rolePermissionRepo.existsByRoleIdAndPermissionIdAndDeletedFalse(role.getId(), perm.getId())) {
            return ApiResponse.ok(null); // idempotent
        }
        RolePermission rp = new RolePermission();
        rp.setRoleId(role.getId());
        rp.setPermissionId(perm.getId());
        rolePermissionRepo.save(rp);
        audit.audit(actor, primaryRole(actor), "PERMISSION_CHANGED", "ROLE", role.getId(), null, null,
                "Permission added to role: " + perm.getCode());
        return ApiResponse.ok(null);
    }

    private String primaryRole(UUID userId) {
        return permissionService.effectiveRoleCodes(userId).stream().sorted().findFirst().orElse(null);
    }

    private RoleResponse toResponse(Role r) {
        return new RoleResponse(r.getId(), r.getCode(), r.getName(), r.getDescription(),
                r.isSystem(), r.getAssignableByMinRole(), r.getStatus(), r.getVersion());
    }
}
