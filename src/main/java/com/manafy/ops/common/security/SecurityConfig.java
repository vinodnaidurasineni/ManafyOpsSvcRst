package com.manafy.ops.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Security configuration for ManafyOpsSvcRst.
 *
 * DESIGN — why permitAll():
 * Authorization is enforced in the application layer (AuthorizationService,
 * PermissionService, ScopeService, RelationshipPredicateResolver) called from
 * controllers/services. Spring Security here is responsible for AUTHENTICATION
 * plumbing only: validating Amazon Cognito JWTs (signature/issuer/expiry/token_use/
 * audience via JWK) when a pool is configured. Both permission AND scope gates run
 * server-side in the app layer; a single authorization model applies regardless.
 *
 * COGNITO GATING: the resource-server JWT decoder is wired only when a Cognito
 * issuer-uri is configured. Until a real User Pool exists, issuer-uri is blank and
 * no invalid decoder is created (the app still boots for local/test).
 */
@Configuration
public class SecurityConfig {

    private final String cognitoIssuerUri;
    private final String cognitoAppClientId;

    public SecurityConfig(
            @Value("${manafy.cognito.issuer-uri:}") String cognitoIssuerUri,
            @Value("${manafy.cognito.app-client-id:}") String cognitoAppClientId) {
        this.cognitoIssuerUri = cognitoIssuerUri;
        this.cognitoAppClientId = cognitoAppClientId;
    }

    private boolean cognitoConfigured() {
        return StringUtils.hasText(cognitoIssuerUri);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Authorization is enforced in the app layer; Spring Security permits transport.
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        if (cognitoConfigured()) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                    jwt.decoder(cognitoJwtDecoder())
                       .jwtAuthenticationConverter(new CognitoJwtAuthenticationConverter())));
        }

        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    /**
     * Cognito JWT decoder: validates signature (pool JWKs), issuer, expiry,
     * token_use ∈ {access,id}, and — when configured — the app client/audience.
     * No custom cryptography; NimbusJwtDecoder fetches/caches the JWK set.
     */
    JwtDecoder cognitoJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(cognitoIssuerUri).build();
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(cognitoIssuerUri));
        if (StringUtils.hasText(cognitoAppClientId)) {
            validators.add(new CognitoAudienceValidator(cognitoAppClientId));
        }
        validators.add(new CognitoTokenUseValidator());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    /** token_use must be present and be access|id. */
    static class CognitoTokenUseValidator implements OAuth2TokenValidator<Jwt> {
        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            String tokenUse = jwt.getClaimAsString("token_use");
            if ("access".equals(tokenUse) || "id".equals(tokenUse)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "Unexpected Cognito token_use: " + tokenUse, null));
        }
    }

    /** access tokens carry client id in client_id; id tokens in aud. Accept either. */
    static class CognitoAudienceValidator implements OAuth2TokenValidator<Jwt> {
        private final String expectedClientId;

        CognitoAudienceValidator(String expectedClientId) {
            this.expectedClientId = expectedClientId;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            String clientId = jwt.getClaimAsString("client_id");
            List<String> aud = jwt.getAudience();
            boolean ok = expectedClientId.equals(clientId)
                    || (aud != null && aud.contains(expectedClientId));
            return ok ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "Cognito token client/audience mismatch", null));
        }
    }
}
