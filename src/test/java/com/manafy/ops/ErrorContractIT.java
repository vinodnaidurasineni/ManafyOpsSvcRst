package com.manafy.ops;

import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.support.AuthzFixtures;
import com.manafy.ops.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP error-contract tests. Verifies status codes and the response envelope, and
 * that internal detail (exception text, SQL, stack traces, secrets) never leaks.
 *
 * NOTE ON ENVELOPE SHAPE: the implemented envelope is the flat ApiResponse
 * ({success,errorCode,message,errorId}) reused from ManafyCommunitySvcRst, NOT the
 * nested {error:{code,message,details}} shape in Artifact #3 §56. This is a known
 * discrepancy recorded in docs/05 (Schema/Contract discrepancies). These tests
 * assert the ACTUAL implemented contract.
 */
class ErrorContractIT extends IntegrationTestBase {

    @Autowired AuthzFixtures fx;

    private String globalSuperAdmin() {
        String sub = "err-su-" + UUID.randomUUID();
        OpsUser su = fx.createUserWithSub("su", sub, "ACTIVE");
        fx.assignRole(su.getId(), "SUPER_ADMIN");
        fx.grantGlobalScope(su.getId());
        return sub;
    }

    @Test
    void unauthenticated401() throws Exception {
        // No auth → CurrentUserService throws UNAUTHENTICATED (401).
        mockMvc.perform(get("/api/v1/areas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    void forbidden403() throws Exception {
        String sub = "err-forbidden-" + UUID.randomUUID();
        fx.createUserWithSub("nobody", sub, "ACTIVE"); // no roles
        mockMvc.perform(get("/api/v1/areas").with(asCognito(sub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void notFound404() throws Exception {
        String sub = globalSuperAdmin();
        mockMvc.perform(get("/api/v1/regions/" + UUID.randomUUID()).with(asCognito(sub)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void conflict409OnDuplicateCode() throws Exception {
        String sub = globalSuperAdmin();
        String code = "DUP-" + UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(Map.of("code", code, "name", "Dup"));
        mockMvc.perform(post("/api/v1/regions").with(asCognito(sub))
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk());
        // Same code again → RESOURCE_CONFLICT (409).
        mockMvc.perform(post("/api/v1/regions").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_CONFLICT"));
    }

    @Test
    void validation400OnMissingRequiredField() throws Exception {
        String sub = globalSuperAdmin();
        // Missing required 'name' (and 'code') → bean validation → 400 VALIDATION_ERROR.
        mockMvc.perform(post("/api/v1/regions").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("city", "x"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void badScopeType422StyleValidation() throws Exception {
        String sub = globalSuperAdmin();
        OpsUser target = fx.createUserWithSub("target", "err-target-" + UUID.randomUUID(), "ACTIVE");
        // Invalid scopeType → VALIDATION_ERROR (400).
        mockMvc.perform(post("/api/v1/users/" + target.getId() + "/scopes").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("scopeType", "NONSENSE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void errorBodyNeverLeaksInternalDetail() throws Exception {
        // A 404 response must contain only the safe code/message, no stack/SQL.
        String sub = globalSuperAdmin();
        String body = mockMvc.perform(get("/api/v1/regions/" + UUID.randomUUID()).with(asCognito(sub)))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("Exception", "org.hibernate", "org.springframework", "jdbc", "SQL", "at com.manafy");
    }
}
