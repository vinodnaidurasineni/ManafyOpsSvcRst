package com.manafy.ops;

import com.manafy.ops.common.authz.repository.AuditLogRepository;
import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.identity.repository.OpsUserRepository;
import com.manafy.ops.org.entity.Region;
import com.manafy.ops.org.repository.RegionRepository;
import com.manafy.ops.support.AuthzFixtures;
import com.manafy.ops.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-level audit, idempotency and optimistic-locking integration tests.
 */
class AuditIdempotencyConcurrencyIT extends IntegrationTestBase {

    @Autowired AuthzFixtures fx;
    @Autowired AuditLogRepository auditRepo;
    @Autowired RegionRepository regionRepo;
    @Autowired OpsUserRepository userRepo;

    /** A global SUPER_ADMIN acting subject. */
    private String superAdmin() {
        String sub = "audit-su-" + UUID.randomUUID();
        OpsUser su = fx.createUserWithSub("su", sub, "ACTIVE");
        fx.assignRole(su.getId(), "SUPER_ADMIN");
        fx.grantGlobalScope(su.getId());
        return sub;
    }

    private long countAudits(String action, UUID resourceId) {
        return auditRepo.findByOrderByCreatedAtDesc(PageRequest.of(0, 500)).getContent().stream()
                .filter(a -> action.equals(a.getAction()) && resourceId.equals(a.getResourceId()))
                .count();
    }

    // ─── Audit: sensitive op produces an audit record ────────────────

    @Test
    void configUpdateProducesAuditWithBeforeAfter() throws Exception {
        String sub = superAdmin();
        // Update a seeded config key and verify an audit row with before/after.
        mockMvc.perform(patch("/api/v1/configuration/DEFAULT_TIMEZONE")
                        .with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("value", "UTC"))))
                .andExpect(status().isOk());

        var audits = auditRepo.findByOrderByCreatedAtDesc(PageRequest.of(0, 50)).getContent();
        var configAudit = audits.stream()
                .filter(a -> "CONFIG_UPDATED".equals(a.getAction())).findFirst().orElseThrow();
        assertThat(configAudit.getActorUserId()).isNotNull();
        assertThat(configAudit.getResourceType()).isEqualTo("SYSTEM_CONFIGURATION");
        assertThat(configAudit.getCreatedAt()).isNotNull();
        assertThat(configAudit.getAfterState()).isEqualTo("UTC");
        assertThat(configAudit.getBeforeState()).isEqualTo("Asia/Kolkata");
        // Correlation id is captured from the request.
        assertThat(configAudit.getCorrelationId()).isNotBlank();
    }

    @Test
    void regionCreateProducesAudit() throws Exception {
        String sub = superAdmin();
        var res = mockMvc.perform(post("/api/v1/regions")
                        .with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("code", "RC-" + UUID.randomUUID(), "name", "Region C"))))
                .andExpect(status().isOk())
                .andReturn();
        String body = res.getResponse().getContentAsString();
        UUID regionId = UUID.fromString(objectMapper.readTree(body).at("/data/id").asText());
        assertThat(countAudits("REGION_CREATED", regionId)).isEqualTo(1);
    }

    // ─── Idempotency ─────────────────────────────────────────────────

    @Test
    void sameIdempotencyKeyDoesNotDuplicate() throws Exception {
        String sub = superAdmin();
        String key = "idem-http-" + UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(Map.of("displayName", "Idem User"));

        // First request A + key X → created.
        mockMvc.perform(post("/api/v1/users").with(asCognito(sub))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());

        // Repeat A + key X → rejected as duplicate (no second user, no second audit).
        long usersBefore = userRepo.count();
        mockMvc.perform(post("/api/v1/users").with(asCognito(sub))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_REQUEST"));
        assertThat(userRepo.count()).isEqualTo(usersBefore);
    }

    @Test
    void differentPayloadSameKeyIsRejected() throws Exception {
        String sub = superAdmin();
        String key = "idem-http2-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/users").with(asCognito(sub))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("displayName", "Payload A"))))
                .andExpect(status().isOk());

        // Different payload B, same key X → rejected (current design: key reuse is
        // rejected regardless of payload; see docs note on fingerprinting).
        mockMvc.perform(post("/api/v1/users").with(asCognito(sub))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("displayName", "Payload B"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_REQUEST"));
    }

    // ─── Optimistic locking ──────────────────────────────────────────

    @Test
    void staleVersionUpdateReturns409() throws Exception {
        String sub = superAdmin();
        // Create a region.
        var res = mockMvc.perform(post("/api/v1/regions").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("code", "RL-" + UUID.randomUUID(), "name", "Lock Region"))))
                .andExpect(status().isOk()).andReturn();
        var json = objectMapper.readTree(res.getResponse().getContentAsString());
        UUID id = UUID.fromString(json.at("/data/id").asText());
        long v0 = json.at("/data/version").asLong();

        // First update at v0 → succeeds (version bumps).
        mockMvc.perform(patch("/api/v1/regions/" + id).with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "First", "version", v0))))
                .andExpect(status().isOk());

        // Second update still using the stale v0 → 409 CONCURRENCY_CONFLICT, no silent overwrite.
        mockMvc.perform(patch("/api/v1/regions/" + id).with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Second", "version", v0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENCY_CONFLICT"));

        // Confirm the name is "First", not silently overwritten to "Second".
        mockMvc.perform(get("/api/v1/regions/" + id).with(asCognito(sub)))
                .andExpect(jsonPath("$.data.name").value("First"));
    }
}
