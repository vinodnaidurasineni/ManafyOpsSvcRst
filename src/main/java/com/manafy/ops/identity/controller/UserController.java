package com.manafy.ops.identity.controller;

import com.manafy.ops.common.authz.entity.Role;
import com.manafy.ops.common.authz.entity.UserRole;
import com.manafy.ops.common.authz.entity.UserScope;
import com.manafy.ops.common.authz.repository.RoleRepository;
import com.manafy.ops.common.authz.repository.UserRoleRepository;
import com.manafy.ops.common.authz.repository.UserScopeRepository;
import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.dto.PageResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.idempotency.IdempotencyService;
import com.manafy.ops.common.security.AuditService;
import com.manafy.ops.common.security.AuthenticationContext;
import com.manafy.ops.common.security.AuthorizationService;
import com.manafy.ops.common.security.PermissionService;
import com.manafy.ops.common.security.ScopeType;
import com.manafy.ops.identity.dto.UserDtos.*;
import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.identity.repository.OpsUserRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Internal user administration (Artifact #3 §1). Every action enforces the
 * required permission server-side; sensitive actions are audited. Role assignment
 * is bounded by assignability (spec §119).
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final OpsUserRepository userRepo;
    private final RoleRepository roleRepo;
    private final UserRoleRepository userRoleRepo;
    private final UserScopeRepository userScopeRepo;
    private final AuthenticationContext authContext;
    private final AuthorizationService authz;
    private final PermissionService permissionService;
    private final AuditService audit;
    private final IdempotencyService idempotency;

    public UserController(OpsUserRepository userRepo, RoleRepository roleRepo,
                          UserRoleRepository userRoleRepo, UserScopeRepository userScopeRepo,
                          AuthenticationContext authContext, AuthorizationService authz,
                          PermissionService permissionService, AuditService audit,
                          IdempotencyService idempotency) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.userRoleRepo = userRoleRepo;
        this.userScopeRepo = userScopeRepo;
        this.authContext = authContext;
        this.authz = authz;
        this.permissionService = permissionService;
        this.audit = audit;
        this.idempotency = idempotency;
    }

    @GetMapping
    public PageResponse<UserResponse> list(@RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer pageSize) {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "USER_VIEW");
        int p = PageResponse.normalizePage(page);
        int ps = PageResponse.clampPageSize(pageSize);
        var result = userRepo.findAll(PageRequest.of(p - 1, ps));
        List<UserResponse> data = result.getContent().stream()
                .filter(u -> !u.isDeleted())
                .map(this::toResponse).toList();
        return PageResponse.of(data, p, ps, result.getTotalElements());
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> get(@PathVariable UUID id) {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "USER_VIEW");
        OpsUser u = userRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("User not found"));
        return ApiResponse.ok(toResponse(u));
    }

    @PostMapping
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest req,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "USER_CREATE");
        idempotency.register(idemKey, "POST /users", actor);

        OpsUser u = new OpsUser();
        u.setDisplayName(req.displayName());
        u.setEmail(req.email());
        u.setMobile(req.mobile());
        u.setCognitoSub(req.cognitoSub());
        u.setStatus("ACTIVE");
        OpsUser saved = userRepo.save(u);
        audit.audit(actor, primaryRole(actor), "USER_CREATED", "OPS_USER", saved.getId(),
                null, null, "Internal user created");
        return ApiResponse.ok(toResponse(saved));
    }

    @PatchMapping("/{id}")
    public ApiResponse<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest req) {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "USER_UPDATE");
        OpsUser u = userRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("User not found"));
        applyOptimisticVersion(u, req.version());
        if (req.displayName() != null) u.setDisplayName(req.displayName());
        if (req.email() != null) u.setEmail(req.email());
        if (req.mobile() != null) u.setMobile(req.mobile());
        OpsUser saved = userRepo.save(u);
        audit.audit(actor, primaryRole(actor), "USER_UPDATED", "OPS_USER", saved.getId(),
                null, null, "Internal user updated");
        return ApiResponse.ok(toResponse(saved));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable UUID id) {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "USER_DISABLE");
        OpsUser u = userRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("User not found"));
        u.setStatus("DISABLED");
        userRepo.save(u);
        audit.audit(actor, primaryRole(actor), "USER_DISABLED", "OPS_USER", u.getId(),
                null, null, "Internal user disabled");
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/roles")
    public ApiResponse<Void> assignRole(@PathVariable UUID id, @Valid @RequestBody AssignRoleRequest req) {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "ROLE_ASSIGN");
        // Assignability guard (spec §119): non-super cannot grant SUPER_ADMIN, etc.
        authz.requireCanAssignRole(actor, req.roleCode());

        OpsUser target = userRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("User not found"));
        Role role = roleRepo.findByCodeAndDeletedFalse(req.roleCode())
                .orElseThrow(() -> BusinessException.notFound("Role not found: " + req.roleCode()));

        var existing = userRoleRepo.findByUserIdAndRoleIdAndDeletedFalse(target.getId(), role.getId());
        if (existing.isPresent() && "ACTIVE".equals(existing.get().getStatus())) {
            return ApiResponse.ok(null); // already assigned — idempotent
        }
        UserRole ur = existing.orElseGet(UserRole::new);
        ur.setUserId(target.getId());
        ur.setRoleId(role.getId());
        ur.setAssignedBy(actor);
        ur.setAssignedAt(LocalDateTime.now());
        ur.setStatus("ACTIVE");
        userRoleRepo.save(ur);
        audit.audit(actor, primaryRole(actor), "ROLE_ASSIGNED", "OPS_USER", target.getId(),
                null, null, "Role assigned: " + req.roleCode());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    public ApiResponse<Void> revokeRole(@PathVariable UUID id, @PathVariable UUID roleId) {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "ROLE_ASSIGN");
        var ur = userRoleRepo.findByUserIdAndRoleIdAndDeletedFalse(id, roleId)
                .orElseThrow(() -> BusinessException.notFound("Role assignment not found"));
        ur.setStatus("REVOKED");
        userRoleRepo.save(ur);
        audit.audit(actor, primaryRole(actor), "ROLE_REVOKED", "OPS_USER", id,
                null, null, "Role revoked");
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/scopes")
    public ApiResponse<Void> assignScope(@PathVariable UUID id, @Valid @RequestBody AssignScopeRequest req) {
        UUID actor = authContext.currentUserId();
        authz.requirePermission(actor, "SCOPE_ASSIGN");
        OpsUser target = userRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("User not found"));

        // Validate scope_type + matching ref (mirrors the DB CHECK, fail fast at 400).
        ScopeType type;
        try {
            type = ScopeType.valueOf(req.scopeType());
        } catch (IllegalArgumentException e) {
            throw BusinessException.validation("Invalid scopeType: " + req.scopeType());
        }
        validateScopeRef(type, req);

        UserScope s = new UserScope();
        s.setUserId(target.getId());
        s.setScopeType(type.name());
        s.setRegionId(req.regionId());
        s.setAreaId(req.areaId());
        s.setApartmentId(req.apartmentId());
        s.setVendorId(req.vendorId());
        userScopeRepo.save(s);
        audit.audit(actor, primaryRole(actor), "SCOPE_ASSIGNED", "OPS_USER", target.getId(),
                null, null, "Scope assigned: " + type.name());
        return ApiResponse.ok(null);
    }

    // ─── helpers ─────────────────────────────────────────────────────

    private void validateScopeRef(ScopeType type, AssignScopeRequest req) {
        boolean ok = switch (type) {
            case REGION -> req.regionId() != null;
            case AREA -> req.areaId() != null;
            case APARTMENT -> req.apartmentId() != null;
            case VENDOR -> req.vendorId() != null;
            case GLOBAL, SELF, ASSIGNED -> true;
        };
        if (!ok) {
            throw BusinessException.validation("scopeType " + type + " requires its matching ref id");
        }
    }

    private void applyOptimisticVersion(OpsUser u, Long expectedVersion) {
        if (expectedVersion != null && expectedVersion != u.getVersion()) {
            throw new BusinessException("CONCURRENCY_CONFLICT",
                    "The user was modified concurrently. Reload and retry.", HttpStatus.CONFLICT);
        }
    }

    private String primaryRole(UUID userId) {
        return permissionService.effectiveRoleCodes(userId).stream().sorted().findFirst().orElse(null);
    }

    private UserResponse toResponse(OpsUser u) {
        return new UserResponse(u.getId(), u.getDisplayName(), u.getEmail(), u.getMobile(),
                u.getStatus(), u.isSuperAdmin(), u.getVersion());
    }
}
