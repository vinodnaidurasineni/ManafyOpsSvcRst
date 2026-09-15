package com.manafy.ops.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Ops Cognito JWT claim validators (SecurityConfig). Signature,
 * issuer, expiry and JWK handling are enforced by Spring's NimbusJwtDecoder +
 * JwtValidators.createDefaultWithIssuer (framework code, wired only when a real pool
 * is configured); here we test OUR custom claim validators: token_use and
 * audience/client. This proves Ops does NOT accept "any JWT signed by the pool" — it
 * enforces the claims the application requires.
 */
class CognitoJwtValidationTest {

    private Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
        // Ensure at least one claim so the builder is valid.
        if (claims.isEmpty()) b.subject("s");
        claims.forEach(b::claim);
        return b.build();
    }

    // ─── token_use ───────────────────────────────────────────────────

    @Test
    void tokenUseAccessAccepted() {
        var v = new SecurityConfig.CognitoTokenUseValidator();
        assertThat(v.validate(jwt(Map.of("token_use", "access"))).hasErrors()).isFalse();
    }

    @Test
    void tokenUseIdAccepted() {
        // Cognito phone claims live on the ID token; Ops accepts id as well as access.
        var v = new SecurityConfig.CognitoTokenUseValidator();
        assertThat(v.validate(jwt(Map.of("token_use", "id"))).hasErrors()).isFalse();
    }

    @Test
    void tokenUseRefreshRejected() {
        var v = new SecurityConfig.CognitoTokenUseValidator();
        OAuth2TokenValidatorResult r = v.validate(jwt(Map.of("token_use", "refresh")));
        assertThat(r.hasErrors()).isTrue();
    }

    @Test
    void tokenUseMissingRejected() {
        var v = new SecurityConfig.CognitoTokenUseValidator();
        assertThat(v.validate(jwt(Map.of("sub", "s"))).hasErrors()).isTrue();
    }

    // ─── audience / client_id ────────────────────────────────────────

    @Test
    void audienceMatchesViaClientIdClaim() {
        // Cognito ACCESS tokens carry the app client in client_id.
        var v = new SecurityConfig.CognitoAudienceValidator("app-client-123");
        assertThat(v.validate(jwt(Map.of("client_id", "app-client-123"))).hasErrors()).isFalse();
    }

    @Test
    void audienceMatchesViaAudClaim() {
        // Cognito ID tokens carry the app client in aud.
        var v = new SecurityConfig.CognitoAudienceValidator("app-client-123");
        assertThat(v.validate(jwt(Map.of("aud", List.of("app-client-123")))).hasErrors()).isFalse();
    }

    @Test
    void audienceMismatchRejected() {
        var v = new SecurityConfig.CognitoAudienceValidator("app-client-123");
        assertThat(v.validate(jwt(Map.of("client_id", "some-other-client"))).hasErrors()).isTrue();
    }

    @Test
    void audienceMissingRejected() {
        var v = new SecurityConfig.CognitoAudienceValidator("app-client-123");
        assertThat(v.validate(jwt(Map.of("sub", "s"))).hasErrors()).isTrue();
    }
}
