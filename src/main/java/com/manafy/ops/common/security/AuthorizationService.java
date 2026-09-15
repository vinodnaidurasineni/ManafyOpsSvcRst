package com.manafy.ops.common.security;

import com.manafy.ops.common.authz.entity.Role;
import com.manafy.ops.common.authz.repository.RoleRepository;
import com.manafy.ops.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * Single entry point for authorization decisions (Artifact #2 §1ter pipeline).
 *
 *   permission (can the action be done at all?)
 *     AND scope (on THIS resource?)
 *       [AND relationship predicate (does the actor relate to THIS resource?)]
 *
 * Both permission and scope are independent gates; both must pass. Fails closed —
 * any missing gate throws 403. No role-string checks.
 */
@Service
public class AuthorizationService {

    private final PermissionService permissionService;
    private final ScopeService scopeService;
    private final RelationshipPredicateResolver predicateResolver;
    private final RoleRepository roleRepo;

    public AuthorizationService(PermissionService permissionService, ScopeService scopeService,
                                RelationshipPredicateResolver predicateResolver, RoleRepository roleRepo) {
        this.permissionService = permissionService;
        this.scopeService = scopeService;
        this.predicateResolver = predicateResolver;
        this.roleRepo = roleRepo;
    }

    /** Permission-only gate (GLOBAL-style admin actions with no per-resource scope). */
    public void requirePermission(UUID userId, String permissionCode) {
        permissionService.requirePermission(userId, permissionCode);
    }

    /** Permission + scope gate over a specific resource. */
    public void authorize(UUID userId, String permissionCode, ResourceRef ref) {
        permissionService.requirePermission(userId, permissionCode);
        if (!scopeService.covers(userId, ref)) {
            throw BusinessException.scopeDenied();
        }
    }

    /** Permission + scope + relationship-predicate gate. */
    public void authorize(UUID userId, String permissionCode, ResourceRef ref, RelationshipPredicate predicate) {
        permissionService.requirePermission(userId, permissionCode);
        if (!scopeService.covers(userId, ref)) {
            throw BusinessException.scopeDenied();
        }
        if (predicate != null && !predicateResolver.resolves(userId, predicate, ref)) {
            throw BusinessException.forbidden("Relationship predicate not satisfied: " + predicate);
        }
    }

    /**
     * Enforce role assignability (design DD-06, spec §119): the assigner may grant
     * {@code targetRoleCode} only if allowed by the target role's
     * {@code assignable_by_min_role}, and never a role the assigner does not
     * effectively hold the authority for. A non-super user can never grant
     * SUPER_ADMIN.
     */
    public void requireCanAssignRole(UUID assignerUserId, String targetRoleCode) {
        Set<String> assignerRoles = permissionService.effectiveRoleCodes(assignerUserId);

        if ("SUPER_ADMIN".equals(targetRoleCode) && !assignerRoles.contains("SUPER_ADMIN")) {
            throw new BusinessException("ROLE_NOT_ASSIGNABLE",
                    "Only a Super Admin may assign SUPER_ADMIN", org.springframework.http.HttpStatus.FORBIDDEN);
        }

        Role target = roleRepo.findByCodeAndDeletedFalse(targetRoleCode)
                .orElseThrow(() -> BusinessException.notFound("Role not found: " + targetRoleCode));

        String minRole = target.getAssignableByMinRole();
        // If a min-role is declared, the assigner must hold it (or be SUPER_ADMIN).
        if (minRole != null && !minRole.isBlank()
                && !assignerRoles.contains(minRole)
                && !assignerRoles.contains("SUPER_ADMIN")) {
            throw new BusinessException("ROLE_NOT_ASSIGNABLE",
                    "You are not permitted to assign role " + targetRoleCode,
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }
    }
}
