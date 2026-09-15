package com.manafy.ops.common.security;

import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.identity.repository.OpsUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Resolves the authenticated request to a Manafy {@link OpsUser} — the single
 * point where authentication ("who is this?") becomes a Manafy user.
 *
 * Cognito provides identity: a validated JWT is present as a
 * {@link JwtAuthenticationToken}; its {@code sub} is linked to an OpsUser.
 *
 * FAIL-SAFE (owner requirement Q-F4 + DD-39):
 *   - An UNKNOWN Cognito subject is provisioned as a fresh OpsUser with NO roles
 *     and status ACTIVE, but zero permissions/scopes — it can authenticate yet do
 *     nothing until an admin grants roles/scope. First login is NEVER auto-promoted.
 *   - Super Admin promotion happens ONLY via the config-driven bootstrap runner,
 *     never here.
 *
 * FAIL-CLOSED status enforcement: only ACTIVE passes; DISABLED and any unknown
 * status are rejected (403).
 *
 * SECURITY: never trusts a user id supplied by the client. Identity is always
 * derived from the validated token.
 */
@Service
public class CurrentUserService {

    private final OpsUserRepository userRepo;
    private final AuditService auditService;

    public CurrentUserService(OpsUserRepository userRepo, AuditService auditService) {
        this.userRepo = userRepo;
        this.auditService = auditService;
    }

    /** Resolve the current authenticated OpsUser, enforcing account status. */
    public OpsUser currentUser() {
        OpsUser u = resolve();
        enforceStatus(u);
        return u;
    }

    public UUID currentUserId() {
        return currentUser().getId();
    }

    private OpsUser resolve() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return resolveFromCognito(jwtAuth.getToken());
        }
        throw BusinessException.unauthenticated();
    }

    /**
     * Cognito subject → OpsUser.
     *   1. Already linked (cognito_sub) → return it.
     *   2. First login → create a fresh OpsUser with NO roles (never privileged).
     * Insert-then-catch handles a concurrent first login for the same sub.
     */
    private OpsUser resolveFromCognito(Jwt jwt) {
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new BusinessException("UNAUTHENTICATED", "Invalid token subject", HttpStatus.UNAUTHORIZED);
        }

        var linked = userRepo.findByCognitoSubAndDeletedFalse(sub);
        if (linked.isPresent()) {
            return touchLogin(linked.get());
        }

        // First login: provision an unprivileged user. No roles, no scopes.
        OpsUser u = new OpsUser();
        u.setCognitoSub(sub);
        u.setEmail(jwt.getClaimAsString("email"));
        String phone = jwt.getClaimAsString("phone_number");
        if (phone != null && !phone.isBlank()) u.setMobile(phone);
        u.setDisplayName(deriveDisplayName(jwt, sub));
        u.setStatus("ACTIVE");
        u.setSuperAdmin(false);
        OpsUser saved;
        try {
            saved = touchLogin(userRepo.save(u));
        } catch (DataIntegrityViolationException dup) {
            return touchLogin(userRepo.findByCognitoSubAndDeletedFalse(sub)
                    .orElseThrow(() -> new BusinessException("AUTH_CONFLICT",
                            "Identity creation conflict", HttpStatus.CONFLICT)));
        }
        // Audit the provisioning. NOTE: never log/persist the Cognito sub as a secret;
        // the sub is an identity reference and is stored on the user row itself.
        auditService.auditSystem(saved.getId(), "OPS_USER_PROVISIONED", "OPS_USER", saved.getId(),
                "New Ops user provisioned on first Cognito login (no roles granted)");
        return saved;
    }

    private String deriveDisplayName(Jwt jwt, String sub) {
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) return name;
        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) return email;
        return "User " + sub.substring(0, Math.min(8, sub.length()));
    }

    private OpsUser touchLogin(OpsUser u) {
        u.setLastLoginAt(LocalDateTime.now());
        return userRepo.save(u);
    }

    /**
     * FAIL-CLOSED: only an explicit allow-list passes.
     *   ACTIVE   → allowed
     *   DISABLED → denied (403)
     *   (null)   → treated as ACTIVE for legacy safety
     *   (else)   → denied (403)
     */
    private void enforceStatus(OpsUser u) {
        String status = u.getStatus() == null ? "ACTIVE" : u.getStatus();
        switch (status) {
            case "ACTIVE" -> { /* allowed */ }
            case "DISABLED" -> throw new BusinessException("AUTH_DISABLED", "Account disabled", HttpStatus.FORBIDDEN);
            default -> throw new BusinessException("AUTH_STATUS_DENIED", "Account not permitted", HttpStatus.FORBIDDEN);
        }
    }
}
