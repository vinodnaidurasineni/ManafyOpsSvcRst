package com.manafy.ops.apartment;

import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.org.entity.Area;
import com.manafy.ops.org.entity.Region;
import com.manafy.ops.support.AuthzFixtures;
import com.manafy.ops.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
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
 * Phase 2 apartment HTTP integration tests through the full controller + security
 * + authorization stack. Covers CRUD authz, FO area access, IDOR, lifecycle,
 * stale-version conflict, idempotency, and disabled-user denial.
 */
class ApartmentHttpIT extends IntegrationTestBase {

    @Autowired AuthzFixtures fx;

    private String admin(String sub) {
        OpsUser u = fx.createUserWithSub("admin", sub, "ACTIVE");
        fx.assignRole(u.getId(), "MANAFY_ADMIN");
        fx.grantGlobalScope(u.getId());
        return sub;
    }

    private Map<String, Object> createReq(String code, UUID regionId, UUID areaId) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code); m.put("name", "Apt " + code);
        m.put("regionId", regionId.toString()); m.put("areaId", areaId.toString());
        m.put("city", "City"); m.put("pincode", "560001");
        return m;
    }

    private JsonNode postApartment(String sub, Map<String, Object> body) throws Exception {
        var res = mockMvc.perform(post("/api/v1/apartments").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
    }

    // ─── CRUD authorization ──────────────────────────────────────────

    @Test
    void adminCanCreateApartment() throws Exception {
        String sub = admin("apt-create-" + UUID.randomUUID());
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        JsonNode apt = postApartment(sub, createReq("APT-" + UUID.randomUUID(), r.getId(), a.getId()));
        assertThat(apt.get("status").asText()).isEqualTo("PROSPECT");
    }

    @Test
    void onboarderCanCreateButNotActivate() throws Exception {
        String sub = "apt-onb-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub("onb", sub, "ACTIVE");
        fx.assignRole(u.getId(), "APARTMENT_ONBOARDER");
        fx.grantGlobalScope(u.getId());
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        JsonNode apt = postApartment(sub, createReq("APT-" + UUID.randomUUID(), r.getId(), a.getId()));
        UUID id = UUID.fromString(apt.get("id").asText());
        // Onboarder lacks APARTMENT_ACTIVATE → 403.
        mockMvc.perform(post("/api/v1/apartments/" + id + "/activate").with(asCognito(sub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void userWithoutPermissionCannotCreate() throws Exception {
        String sub = "apt-noperm-" + UUID.randomUUID();
        fx.createUserWithSub("nobody", sub, "ACTIVE"); // no roles
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        mockMvc.perform(post("/api/v1/apartments").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq("X", r.getId(), a.getId()))))
                .andExpect(status().isForbidden());
    }

    // ─── Field Officer area access ───────────────────────────────────

    @Test
    void fieldOfficerSeesOwnAreaApartmentNotOtherArea() throws Exception {
        String adminSub = admin("apt-fo-admin-" + UUID.randomUUID());
        Region r = fx.region("R");
        Area mine = fx.area("MINE", r.getId());
        Area other = fx.area("OTHER", r.getId());
        JsonNode aptMine = postApartment(adminSub, createReq("M-" + UUID.randomUUID(), r.getId(), mine.getId()));
        JsonNode aptOther = postApartment(adminSub, createReq("O-" + UUID.randomUUID(), r.getId(), other.getId()));
        UUID mineId = UUID.fromString(aptMine.get("id").asText());
        UUID otherId = UUID.fromString(aptOther.get("id").asText());

        String foSub = "apt-fo-" + UUID.randomUUID();
        OpsUser fo = fx.createUserWithSub("fo", foSub, "ACTIVE");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        fx.assignFieldOfficer(mine.getId(), fo.getId(), "PRIMARY");

        mockMvc.perform(get("/api/v1/apartments/" + mineId).with(asCognito(foSub)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/apartments/" + otherId).with(asCognito(foSub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SCOPE_DENIED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void fieldOfficerLosesAccessWhenAreaAssignmentEnds() throws Exception {
        String adminSub = admin("apt-fo-exp-admin-" + UUID.randomUUID());
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        JsonNode apt = postApartment(adminSub, createReq("E-" + UUID.randomUUID(), r.getId(), a.getId()));
        UUID id = UUID.fromString(apt.get("id").asText());

        String foSub = "apt-fo-exp-" + UUID.randomUUID();
        OpsUser fo = fx.createUserWithSub("fo", foSub, "ACTIVE");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        var afo = fx.assignFieldOfficer(a.getId(), fo.getId(), "PRIMARY");
        mockMvc.perform(get("/api/v1/apartments/" + id).with(asCognito(foSub))).andExpect(status().isOk());
        // End the assignment → access disappears immediately.
        fx.endFieldOfficerAssignment(afo.getId());
        mockMvc.perform(get("/api/v1/apartments/" + id).with(asCognito(foSub))).andExpect(status().isForbidden());
    }

    // ─── IDOR ────────────────────────────────────────────────────────

    @Test
    void idorApartmentByIdDenied() throws Exception {
        String adminSub = admin("apt-idor-admin-" + UUID.randomUUID());
        Region r = fx.region("R");
        Area mine = fx.area("MINE", r.getId());
        Area victim = fx.area("VICTIM", r.getId());
        JsonNode victimApt = postApartment(adminSub, createReq("V-" + UUID.randomUUID(), r.getId(), victim.getId()));
        UUID victimId = UUID.fromString(victimApt.get("id").asText());

        String foSub = "apt-idor-fo-" + UUID.randomUUID();
        OpsUser fo = fx.createUserWithSub("fo", foSub, "ACTIVE");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        fx.assignFieldOfficer(mine.getId(), fo.getId(), "PRIMARY");
        // FO tries to read a victim-area apartment by id → denied, no body.
        mockMvc.perform(get("/api/v1/apartments/" + victimId).with(asCognito(foSub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void idorBuildingUnderWrongApartmentDenied() throws Exception {
        String adminSub = admin("bld-idor-admin-" + UUID.randomUUID());
        Region r = fx.region("R");
        Area mine = fx.area("MINE", r.getId());
        Area victim = fx.area("VICTIM", r.getId());
        JsonNode victimApt = postApartment(adminSub, createReq("VB-" + UUID.randomUUID(), r.getId(), victim.getId()));
        UUID victimAptId = UUID.fromString(victimApt.get("id").asText());
        // Admin creates a building under the victim apartment.
        var bres = mockMvc.perform(post("/api/v1/apartments/" + victimAptId + "/buildings").with(asCognito(adminSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Block A"))))
                .andExpect(status().isOk()).andReturn();
        UUID buildingId = UUID.fromString(objectMapper.readTree(bres.getResponse().getContentAsString())
                .at("/data/id").asText());

        // FO of a different area tries to mutate that building → denied.
        String foSub = "bld-idor-fo-" + UUID.randomUUID();
        OpsUser fo = fx.createUserWithSub("fo", foSub, "ACTIVE");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        fx.assignFieldOfficer(mine.getId(), fo.getId(), "PRIMARY");
        mockMvc.perform(put("/api/v1/buildings/" + buildingId).with(asCognito(foSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Hacked"))))
                .andExpect(status().isForbidden());
    }

    // ─── Lifecycle: invalid activation, suspend ──────────────────────

    @Test
    void activateWithoutVerifiedOnboardingReturns409() throws Exception {
        String sub = admin("apt-act-admin-" + UUID.randomUUID());
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        JsonNode apt = postApartment(sub, createReq("AC-" + UUID.randomUUID(), r.getId(), a.getId()));
        UUID id = UUID.fromString(apt.get("id").asText());
        mockMvc.perform(post("/api/v1/apartments/" + id + "/activate").with(asCognito(sub)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ONBOARDING_INCOMPLETE"));
    }

    // ─── Stale version → 409 ─────────────────────────────────────────

    @Test
    void staleVersionUpdateReturns409() throws Exception {
        String sub = admin("apt-ver-admin-" + UUID.randomUUID());
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        JsonNode apt = postApartment(sub, createReq("VR-" + UUID.randomUUID(), r.getId(), a.getId()));
        UUID id = UUID.fromString(apt.get("id").asText());
        long v0 = apt.get("version").asLong();

        mockMvc.perform(put("/api/v1/apartments/" + id).with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "First", "version", v0))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/apartments/" + id).with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Second", "version", v0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENCY_CONFLICT"));
    }

    // ─── Idempotency ─────────────────────────────────────────────────

    @Test
    void duplicateCreateKeyRejected() throws Exception {
        String sub = admin("apt-idem-admin-" + UUID.randomUUID());
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        String key = "create-key-" + UUID.randomUUID();
        // Same key + same successful create → second is rejected as a duplicate.
        // (Uses a distinct apartment code per request; the idempotency key is the guard.)
        var body1 = createReq("IDEM1-" + UUID.randomUUID(), r.getId(), a.getId());
        mockMvc.perform(post("/api/v1/apartments").with(asCognito(sub))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body1)))
                .andExpect(status().isOk());
        var body2 = createReq("IDEM2-" + UUID.randomUUID(), r.getId(), a.getId());
        mockMvc.perform(post("/api/v1/apartments").with(asCognito(sub))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_REQUEST"));
    }

    // ─── Disabled user ───────────────────────────────────────────────

    @Test
    void disabledUserCannotMutate() throws Exception {
        String sub = "apt-disabled-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub("disabled", sub, "DISABLED");
        fx.assignRole(u.getId(), "MANAFY_ADMIN");
        fx.grantGlobalScope(u.getId());
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        mockMvc.perform(post("/api/v1/apartments").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq("D", r.getId(), a.getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH_DISABLED"));
    }
}
