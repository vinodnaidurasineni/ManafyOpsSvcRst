package com.manafy.ops.common.security.dev;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * DEV-ONLY authentication filter. When enabled, it accepts a locally-minted HS256
 * bearer token, verifies it, and places an equivalent {@link JwtAuthenticationToken}
 * into the SecurityContext — the SAME authentication type the Cognito resource
 * server would produce — so {@code CurrentUserService} resolves the principal via
 * its normal {@code sub → OpsUser} path with zero changes to authorization.
 *
 * <p>Only registered when {@code manafy.dev-auth.enabled=true} AND Cognito is not
 * configured (see SecurityConfig). It never runs in a real deployment.
 */
public class DevJwtAuthFilter extends OncePerRequestFilter {

    private final DevJwtService devJwt;

    public DevJwtAuthFilter(DevJwtService devJwt) {
        this.devJwt = devJwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // Do not override an already-authenticated request.
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7).trim();
                String sub = devJwt.verifyAndGetSubject(token);
                if (sub != null) {
                    Instant now = Instant.now();
                    Jwt jwt = Jwt.withTokenValue(token)
                            .header("alg", "HS256")
                            .subject(sub)
                            .issuer("manafy-ops-dev")
                            .issuedAt(now)
                            .expiresAt(now.plusSeconds(60))
                            .claim("token_use", "access")
                            .claims(c -> c.putIfAbsent("scope", "dev"))
                            .build();
                    JwtAuthenticationToken auth = new JwtAuthenticationToken(
                            jwt, AuthorityUtils.NO_AUTHORITIES, sub);
                    auth.setDetails(Map.of("devAuth", true));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }

        chain.doFilter(request, response);
    }
}
