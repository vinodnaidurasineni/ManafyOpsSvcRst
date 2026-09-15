package com.manafy.ops.common.security;

import com.manafy.ops.identity.entity.OpsUser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Convenience facade over the resolved principal: the current OpsUser plus their
 * effective roles, permissions and scopes. Controllers use this to answer
 * {@code /auth/me} and to obtain the acting user id for authorization calls.
 *
 * This composes the authentication resolution (CurrentUserService) with the
 * authorization services; it holds no state of its own.
 */
@Service
public class AuthenticationContext {

    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;
    private final ScopeService scopeService;

    public AuthenticationContext(CurrentUserService currentUserService,
                                 PermissionService permissionService,
                                 ScopeService scopeService) {
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
        this.scopeService = scopeService;
    }

    public OpsUser currentUser() {
        return currentUserService.currentUser();
    }

    public UUID currentUserId() {
        return currentUserService.currentUserId();
    }

    public Set<String> roles(UUID userId) {
        return permissionService.effectiveRoleCodes(userId);
    }

    public List<String> permissions(UUID userId) {
        return permissionService.effectivePermissionList(userId);
    }

    /** Scope grants for the user (drives client-side navigation pre-filtering). */
    public List<com.manafy.ops.common.authz.entity.UserScope> scopes(UUID userId) {
        return scopeService.scopesOf(userId);
    }

    /**
     * Compose each effective permission with the scope(s) the user holds, for the
     * /auth/me payload. This is a UX pre-filter only — the backend re-checks scope
     * on every request. If the user has GLOBAL scope, permissions are reported as
     * GLOBAL; otherwise the union of the user's concrete scope grants is attached.
     */
    public List<com.manafy.ops.identity.dto.MeResponse.PermissionGrant> permissionGrants(UUID userId) {
        List<String> perms = permissions(userId);
        boolean global = scopeService.hasGlobal(userId);

        // Build scope descriptor once; the same grant applies to every permission
        // in this scope model (scope is not per-permission in the current design).
        String scopeType;
        List<String> scopeIds = new java.util.ArrayList<>();
        if (global) {
            scopeType = ScopeType.GLOBAL.name();
        } else {
            var areaIds = scopeService.grantedAreaIds(userId);
            var regionIds = scopeService.grantedRegionIds(userId);
            if (!areaIds.isEmpty()) {
                scopeType = ScopeType.AREA.name();
                areaIds.forEach(id -> scopeIds.add(id.toString()));
            } else if (!regionIds.isEmpty()) {
                scopeType = ScopeType.REGION.name();
                regionIds.forEach(id -> scopeIds.add(id.toString()));
            } else {
                scopeType = ScopeType.SELF.name();
            }
        }
        final String st = scopeType;
        return perms.stream()
                .map(p -> new com.manafy.ops.identity.dto.MeResponse.PermissionGrant(p, st, List.copyOf(scopeIds)))
                .toList();
    }
}
