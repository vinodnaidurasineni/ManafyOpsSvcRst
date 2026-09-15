package com.manafy.ops.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Base for HTTP-level integration tests: full controller + Spring Security +
 * authorization stack via MockMvc. A request is authenticated by injecting a
 * Cognito-style JWT whose {@code sub} + {@code token_use} claims mirror a real
 * token; CurrentUserService then resolves it to an OpsUser exactly as in prod.
 *
 * Note: the resource-server decoder is NOT wired in the test profile (no Cognito
 * issuer configured), so we inject an already-authenticated JwtAuthenticationToken
 * with jwt() — this exercises the SAME CurrentUserService → authz path a validated
 * token would take, without needing a live Cognito pool.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;

    /** A request post-processor authenticating as the given Cognito subject. */
    protected RequestPostProcessor asCognito(String sub) {
        return jwt().jwt(builder -> builder
                .subject(sub)
                .claim("token_use", "access"));
    }
}
