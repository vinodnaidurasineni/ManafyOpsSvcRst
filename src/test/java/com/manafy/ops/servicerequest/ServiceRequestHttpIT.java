package com.manafy.ops.servicerequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.org.entity.Area;
import com.manafy.ops.org.entity.Region;
import com.manafy.ops.support.AuthzFixtures;
import com.manafy.ops.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 4A service request HTTP integration tests through the full controller +
 * security + authorization stack: create, get, list, cancel, validation failures,
 * stale-version concurrency, idempotency, and apartment/area consistency.
 */
class ServiceRequestHttpIT extends IntegrationTestBase {

    @Autowired AuthzFixtures fx;

    /** Admin with global scope — holds APARTMENT_* and SERVICE_REQUEST_* perms. */
    private String admin(String tag) {
        String sub = "sr-admin-" + tag + "-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub("admin", sub, "ACTIVE");
        fx.assignRole(u.getId(), "MANAFY_ADMIN");
        fx.grantGlobalScope(u.getId());
        return sub;
    }

    private JsonNode data(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
    }

    /** Create an apartment via the Phase 2 API and return its id. */
    private UUID createApartment(String sub, UUID regionId, UUID areaId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "APT-" + UUID.randomUUID());
        body.put("name", "Apt");
        body.put("regionId", regionId.toString());
        body.put("areaId", areaId.toString());
        body.put("city", "City");
        body.put("pincode", "560001");
        MvcResult res = mockMvc.perform(post("/api/v1/apartments").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
        return UUID.fromString(data(res).get("id").asText());
    }

    private Map<String, Object> srBody(UUID apartmentId) {
        Map<String, Object> m = new HashMap<>();
        m.put("apartmentId", apartmentId.toString());
        m.put("category", "PLUMBING");
        m.put("priority", "HIGH");
        m.put("description", "Leaking pipe in basement");
        return m;
    }

    private JsonNode createServiceRequest(String sub, UUID apartmentId) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/service-requests").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(srBody(apartmentId))))
                .andExpect(status().isOk()).andReturn();
        return data(res);
    }

    // ─── Create / Get ────────────────────────────────────────────────

    @Test
    void createAndGetServiceRequest() throws Exception {
        String sub = admin("cg");
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        UUID aptId = createApartment(sub, r.getId(), a.getId());

        JsonNode sr = createServiceRequest(sub, aptId);
        assertThat(sr.get("status").asText()).isEqualTo("NEW");
        assertThat(sr.get("referenceNo").asText()).startsWith("SR-");
        assertThat(sr.get("areaId").asText()).isEqualTo(a.getId().toString());
        UUID srId = UUID.fromString(sr.get("id").asText());

        mockMvc.perform(get("/api/v1/service-requests/" + srId).with(asCognito(sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEW"))
                .andExpect(jsonPath("$.data.category").value("PLUMBING"));
    }

    @Test
    void getUnknownReturnsNotFound() throws Exception {
        String sub = admin("nf");
        mockMvc.perform(get("/api/v1/service-requests/" + UUID.randomUUID()).with(asCognito(sub)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    // ─── List ────────────────────────────────────────────────────────

    @Test
    void listReturnsCreatedRequests() throws Exception {
        String sub = admin("list");
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        UUID aptId = createApartment(sub, r.getId(), a.getId());
        createServiceRequest(sub, aptId);
        createServiceRequest(sub, aptId);

        mockMvc.perform(get("/api/v1/service-requests").param("apartmentId", aptId.toString()).with(asCognito(sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.total").value(2));
    }

    @Test
    void listFiltersByStatus() throws Exception {
        String sub = admin("lfs");
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        UUID aptId = createApartment(sub, r.getId(), a.getId());
        JsonNode sr = createServiceRequest(sub, aptId);
        UUID srId = UUID.fromString(sr.get("id").asText());
        mockMvc.perform(post("/api/v1/service-requests/" + srId + "/cancel").with(asCognito(sub)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/service-requests")
                        .param("apartmentId", aptId.toString()).param("status", "CANCELLED").with(asCognito(sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1));
        mockMvc.perform(get("/api/v1/service-requests")
                        .param("apartmentId", aptId.toString()).param("status", "NEW").with(asCognito(sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(0));
    }

    // ─── Cancel ──────────────────────────────────────────────────────

    @Test
    void cancelServiceRequest() throws Exception {
        String sub = admin("cancel");
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        UUID aptId = createApartment(sub, r.getId(), a.getId());
        UUID srId = UUID.fromString(createServiceRequest(sub, aptId).get("id").asText());

        mockMvc.perform(post("/api/v1/service-requests/" + srId + "/cancel").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Resolved directly"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("Resolved directly"));
    }

    @Test
    void cancelAlreadyCancelledReturns409() throws Exception {
        String sub = admin("cc");
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        UUID aptId = createApartment(sub, r.getId(), a.getId());
        UUID srId = UUID.fromString(createServiceRequest(sub, aptId).get("id").asText());

        mockMvc.perform(post("/api/v1/service-requests/" + srId + "/cancel").with(asCognito(sub)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/service-requests/" + srId + "/cancel").with(asCognito(sub)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATE_TRANSITION"));
    }

    // ─── Validation failures ─────────────────────────────────────────

    @Test
    void missingRequiredFieldsRejected() throws Exception {
        String sub = admin("val");
        // No apartmentId / category / description → bean validation 400.
        mockMvc.perform(post("/api/v1/service-requests").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("priority", "HIGH"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidCategoryRejected() throws Exception {
        String sub = admin("cat");
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        UUID aptId = createApartment(sub, r.getId(), a.getId());
        Map<String, Object> body = srBody(aptId);
        body.put("category", "TELEPORTATION");
        mockMvc.perform(post("/api/v1/service-requests").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void invalidPriorityRejected() throws Exception {
        String sub = admin("prio");
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        UUID aptId = createApartment(sub, r.getId(), a.getId());
        Map<String, Object> body = srBody(aptId);
        body.put("priority", "WHENEVER");
        mockMvc.perform(post("/api/v1/service-requests").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void unknownApartmentRejected() throws Exception {
        String sub = admin("noapt");
        Map<String, Object> body = srBody(UUID.randomUUID());
        mockMvc.perform(post("/api/v1/service-requests").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // ─── Concurrency ─────────────────────────────────────────────────

    @Test
    void staleVersionCancelReturns409() throws Exception {
        String sub = admin("ver");
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        UUID aptId = createApartment(sub, r.getId(), a.getId());
        UUID srId = UUID.fromString(createServiceRequest(sub, aptId).get("id").asText());

        // version 0 exists; passing a stale version (99) → CONCURRENCY_CONFLICT.
        mockMvc.perform(post("/api/v1/service-requests/" + srId + "/cancel")
                        .param("version", "99").with(asCognito(sub)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENCY_CONFLICT"));
    }

    // ─── Idempotency ─────────────────────────────────────────────────

    @Test
    void duplicateCreateKeyRejected() throws Exception {
        String sub = admin("idem");
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        UUID aptId = createApartment(sub, r.getId(), a.getId());
        String key = "sr-create-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/service-requests").with(asCognito(sub))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(srBody(aptId))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/service-requests").with(asCognito(sub))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(srBody(aptId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_REQUEST"));
    }
}
