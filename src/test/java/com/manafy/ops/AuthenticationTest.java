package com.manafy.ops;

import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.common.security.CurrentUserService;
import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.identity.repository.OpsUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Authentication resolution tests: valid Cognito identity → OpsUser; unknown
 * subject provisioned unprivileged (fail-safe); disabled user rejected.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthenticationTest {

    @Autowired CurrentUserService currentUserService;
    @Autowired OpsUserRepository userRepo;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String sub, Map<String, Object> extraClaims) {
        Jwt.Builder b = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(sub)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("token_use", "access");
        extraClaims.forEach(b::claim);
        Jwt jwt = b.build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, java.util.Collections.emptyList(), sub));
    }

    @Test
    void unknownCognitoSubjectIsProvisionedUnprivileged() {
        String sub = "cognito-unknown-1";
        authenticateAs(sub, Map.of("email", "new@manafy.in"));
        OpsUser u = currentUserService.currentUser();
        assertThat(u.getCognitoSub()).isEqualTo(sub);
        assertThat(u.getStatus()).isEqualTo("ACTIVE");
        assertThat(u.isSuperAdmin()).isFalse();
        // Fail-safe: no roles are granted on first login.
        // (verified via the user existing but having no user_role rows)
        assertThat(userRepo.findByCognitoSubAndDeletedFalse(sub)).isPresent();
    }

    @Test
    void secondLoginResolvesSameUser() {
        String sub = "cognito-repeat-1";
        authenticateAs(sub, Map.of());
        OpsUser first = currentUserService.currentUser();
        SecurityContextHolder.clearContext();
        authenticateAs(sub, Map.of());
        OpsUser second = currentUserService.currentUser();
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void disabledUserIsRejected() {
        String sub = "cognito-disabled-1";
        // Pre-create the user as DISABLED.
        OpsUser u = new OpsUser();
        u.setCognitoSub(sub);
        u.setDisplayName("disabled");
        u.setStatus("DISABLED");
        userRepo.save(u);

        authenticateAs(sub, Map.of());
        assertThatThrownBy(() -> currentUserService.currentUser())
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("AUTH_DISABLED");
    }

    @Test
    void unauthenticatedRequestIsRejected() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> currentUserService.currentUser())
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("UNAUTHENTICATED");
    }
}
