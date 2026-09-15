package com.manafy.ops.workforce;

import com.fasterxml.jackson.databind.JsonNode;
import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.org.entity.Area;
import com.manafy.ops.org.entity.Region;
import com.manafy.ops.support.AuthzFixtures;
import com.manafy.ops.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 3 workforce HTTP integration tests through the full controller + security
 * + authorization stack. Covers HR happy-path CRUD, lifecycle rules (inactive area
 * assignment), duplicate skill/area conflicts, expired-cert flag, stale-version
 * conflict, idempotency, and vendor-staff parent authorization.
 */
class WorkforceHttpIT extends IntegrationTestBase {

    @Autowired AuthzFixtures fx;

    // ─── Actor helpers ───────────────────────────────────────────────

    /** HR with GLOBAL scope — the primary workforce manager. */
    private String hr(String tag) {
        String sub = "wf-hr-" + tag + "-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub("hr", sub, "ACTIVE");
        fx.assignRole(u.getId(), "HR");
        fx.grantGlobalScope(u.getId());
        return sub;
    }

    private JsonNode data(org.springframework.test.web.servlet.MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
    }

    private JsonNode postTechnician(String sub, String code) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", "Tech " + code);
        var res = mockMvc.perform(post("/api/v1/technicians").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
        return data(res);
    }

    private JsonNode postVendor(String sub, String code) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("legalName", "Legal " + code);
        body.put("displayName", "Vendor " + code);
        var res = mockMvc.perform(post("/api/v1/vendors").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
        return data(res);
    }

    private JsonNode postSkill(String sub, String code) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", "Skill " + code);
        var res = mockMvc.perform(post("/api/v1/skills").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
        return data(res);
    }

    // ─── HR happy path: create workforce ─────────────────────────────

    @Test
    void hrCanCreateTechnician() throws Exception {
        JsonNode t = postTechnician(hr("tcreate"), "T-" + UUID.randomUUID());
        assertThat(t.get("status").asText()).isEqualTo("DRAFT");
    }

    @Test
    void hrCanCreateVendorAndHelper() throws Exception {
        String sub = hr("vh");
        JsonNode v = postVendor(sub, "V-" + UUID.randomUUID());
        assertThat(v.get("status").asText()).isEqualTo("DRAFT");

        Map<String, Object> helper = new HashMap<>();
        helper.put("code", "H-" + UUID.randomUUID());
        helper.put("name", "Helper A");
        helper.put("relationship", "MANAFY");
        mockMvc.perform(post("/api/v1/helpers").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(helper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    // ─── Vendor staff parent authorization ───────────────────────────

    @Test
    void vendorStaffResolvesThroughParentVendor() throws Exception {
        String sub = hr("staff");
        JsonNode v = postVendor(sub, "VS-" + UUID.randomUUID());
        UUID vendorId = UUID.fromString(v.get("id").asText());

        Map<String, Object> staff = new HashMap<>();
        staff.put("staffName", "Ops Contact");
        staff.put("roleTitle", "MANAGER");
        var res = mockMvc.perform(post("/api/v1/vendors/" + vendorId + "/staff").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(staff)))
                .andExpect(status().isOk()).andReturn();
        UUID staffId = UUID.fromString(data(res).get("id").asText());

        // Direct-by-id fetch resolves through the parent vendor.
        mockMvc.perform(get("/api/v1/vendor-staff/" + staffId).with(asCognito(sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vendorId").value(vendorId.toString()));
    }

    // ─── Lifecycle: inactive/terminated technician cannot get an area ─

    @Test
    void inactiveTechnicianCannotBeAssignedArea() throws Exception {
        String sub = hr("inactive");
        JsonNode t = postTechnician(sub, "T-" + UUID.randomUUID());
        UUID techId = UUID.fromString(t.get("id").asText());
        // DRAFT → ACTIVE → INACTIVE.
        mockMvc.perform(post("/api/v1/technicians/" + techId + "/activate").with(asCognito(sub)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/technicians/" + techId + "/deactivate").with(asCognito(sub)))
                .andExpect(status().isOk());

        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        mockMvc.perform(post("/api/v1/technicians/" + techId + "/areas").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("areaId", a.getId().toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void invalidLifecycleTransitionReturns409() throws Exception {
        String sub = hr("badtransition");
        JsonNode t = postTechnician(sub, "T-" + UUID.randomUUID());
        UUID techId = UUID.fromString(t.get("id").asText());
        // DRAFT → SUSPENDED is illegal (must go through ACTIVE first).
        mockMvc.perform(post("/api/v1/technicians/" + techId + "/suspend").with(asCognito(sub)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATE_TRANSITION"));
    }

    // ─── Duplicate skill / area → RESOURCE_CONFLICT ──────────────────

    @Test
    void duplicateSkillAssignmentReturns409() throws Exception {
        String sub = hr("dupskill");
        JsonNode t = postTechnician(sub, "T-" + UUID.randomUUID());
        UUID techId = UUID.fromString(t.get("id").asText());
        JsonNode skill = postSkill(sub, "SK-" + UUID.randomUUID());
        UUID skillId = UUID.fromString(skill.get("id").asText());

        Map<String, Object> assign = Map.of("skillId", skillId.toString(), "skillLevel", "EXPERT");
        mockMvc.perform(post("/api/v1/technicians/" + techId + "/skills").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(assign)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/technicians/" + techId + "/skills").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(assign)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_CONFLICT"));
    }

    @Test
    void duplicateAreaAssignmentReturns409() throws Exception {
        String sub = hr("duparea");
        JsonNode t = postTechnician(sub, "T-" + UUID.randomUUID());
        UUID techId = UUID.fromString(t.get("id").asText());
        mockMvc.perform(post("/api/v1/technicians/" + techId + "/activate").with(asCognito(sub)))
                .andExpect(status().isOk());
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());

        Map<String, Object> assign = Map.of("areaId", a.getId().toString());
        mockMvc.perform(post("/api/v1/technicians/" + techId + "/areas").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(assign)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/technicians/" + techId + "/areas").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(assign)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_CONFLICT"));
    }

    // ─── Expired certification flag ──────────────────────────────────

    @Test
    void expiredCertificationIsFlagged() throws Exception {
        String sub = hr("cert");
        JsonNode t = postTechnician(sub, "T-" + UUID.randomUUID());
        UUID techId = UUID.fromString(t.get("id").asText());

        Map<String, Object> cert = new HashMap<>();
        cert.put("certType", "SAFETY");
        cert.put("issuingAuthority", "Authority");
        cert.put("expiryDate", "2000-01-01"); // long past
        mockMvc.perform(post("/api/v1/technicians/" + techId + "/certifications").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cert)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expired").value(true))
                .andExpect(jsonPath("$.data.status").value("EXPIRED"));
    }

    // ─── Stale version → 409 CONCURRENCY_CONFLICT ────────────────────

    @Test
    void staleVersionUpdateReturns409() throws Exception {
        String sub = hr("ver");
        JsonNode t = postTechnician(sub, "T-" + UUID.randomUUID());
        UUID techId = UUID.fromString(t.get("id").asText());
        long v0 = t.get("version").asLong();

        mockMvc.perform(put("/api/v1/technicians/" + techId).with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "First", "version", v0))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/technicians/" + techId).with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Second", "version", v0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENCY_CONFLICT"));
    }

    // ─── Idempotency ─────────────────────────────────────────────────

    @Test
    void duplicateCreateKeyRejected() throws Exception {
        String sub = hr("idem");
        String key = "tech-create-" + UUID.randomUUID();
        Map<String, Object> body1 = Map.of("code", "T-" + UUID.randomUUID(), "name", "One");
        mockMvc.perform(post("/api/v1/technicians").with(asCognito(sub))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body1)))
                .andExpect(status().isOk());
        Map<String, Object> body2 = Map.of("code", "T-" + UUID.randomUUID(), "name", "Two");
        mockMvc.perform(post("/api/v1/technicians").with(asCognito(sub))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_REQUEST"));
    }

    @Test
    void duplicateTechnicianCodeReturns409() throws Exception {
        String sub = hr("dupcode");
        String code = "T-" + UUID.randomUUID();
        postTechnician(sub, code);
        mockMvc.perform(post("/api/v1/technicians").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code, "name", "Dup"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_CONFLICT"));
    }
}
