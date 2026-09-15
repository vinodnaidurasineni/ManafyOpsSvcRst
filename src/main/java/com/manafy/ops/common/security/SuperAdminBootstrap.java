package com.manafy.ops.common.security;

import com.manafy.ops.common.authz.entity.Role;
import com.manafy.ops.common.authz.entity.UserRole;
import com.manafy.ops.common.authz.repository.RoleRepository;
import com.manafy.ops.common.authz.repository.UserRoleRepository;
import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.identity.repository.OpsUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Config-driven Super Admin bootstrap (owner decision Q-F4).
 *
 * On startup, if {@code manafy.bootstrap.super-admin-sub} is configured, the
 * OpsUser with that Cognito subject is promoted to the SUPER_ADMIN role (and the
 * super_admin guard flag). Requirements honoured:
 *   - The first Cognito login is NEVER auto-promoted (promotion only for the exact
 *     configured sub).
 *   - Unknown Cognito users receive no privileged roles.
 *   - Bootstrap must be explicitly configured per environment (empty → no-op).
 *   - Promotion writes an audit record.
 *   - The Cognito sub is NOT hard-coded in source and is NEVER logged or returned.
 *
 * Idempotent: if the user is already SUPER_ADMIN, nothing happens. If the user row
 * does not exist yet (that person has not logged in), bootstrap is a safe no-op —
 * the promotion applies once they exist and the app restarts, or an admin grants
 * the role through the API.
 */
@Component
public class SuperAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final String superAdminSub;
    private final OpsUserRepository userRepo;
    private final RoleRepository roleRepo;
    private final UserRoleRepository userRoleRepo;
    private final AuditService auditService;

    public SuperAdminBootstrap(@Value("${manafy.bootstrap.super-admin-sub:}") String superAdminSub,
                               OpsUserRepository userRepo, RoleRepository roleRepo,
                               UserRoleRepository userRoleRepo, AuditService auditService) {
        this.superAdminSub = superAdminSub;
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.userRoleRepo = userRoleRepo;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (superAdminSub == null || superAdminSub.isBlank()) {
            // Not configured for this environment → no bootstrap. Do not log the (absent) value.
            log.info("Super Admin bootstrap not configured; skipping.");
            return;
        }

        Optional<OpsUser> maybeUser = userRepo.findByCognitoSubAndDeletedFalse(superAdminSub);
        if (maybeUser.isEmpty()) {
            // The configured identity has not logged in yet; nothing to promote now.
            // Do NOT create a privileged user pre-emptively, and do NOT log the sub.
            log.info("Super Admin bootstrap: configured identity not present yet; will promote after first login + restart.");
            return;
        }

        Optional<Role> saRole = roleRepo.findByCodeAndDeletedFalse(SUPER_ADMIN);
        if (saRole.isEmpty()) {
            log.warn("Super Admin bootstrap: SUPER_ADMIN role missing (seed not applied?); skipping.");
            return;
        }

        OpsUser user = maybeUser.get();
        Role role = saRole.get();

        boolean alreadyAssigned = userRoleRepo
                .findByUserIdAndRoleIdAndDeletedFalse(user.getId(), role.getId())
                .filter(ur -> "ACTIVE".equals(ur.getStatus()))
                .isPresent();

        if (alreadyAssigned && user.isSuperAdmin()) {
            log.info("Super Admin bootstrap: target already promoted; no action.");
            return;
        }

        if (!alreadyAssigned) {
            UserRole ur = new UserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(role.getId());
            ur.setAssignedAt(LocalDateTime.now());
            ur.setStatus("ACTIVE");
            userRoleRepo.save(ur);
        }
        if (!user.isSuperAdmin()) {
            user.setSuperAdmin(true);
            userRepo.save(user);
        }

        auditService.auditSystem(user.getId(), "SUPER_ADMIN_BOOTSTRAP", "OPS_USER", user.getId(),
                "SUPER_ADMIN granted via config-driven bootstrap");
        // Deliberately do NOT log the sub or any bootstrap value.
        log.info("Super Admin bootstrap: promotion applied to configured identity.");
    }
}
