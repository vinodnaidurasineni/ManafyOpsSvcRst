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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 4A RBAC tests: authorized roles can create/view; unauthorized roles get 403;
 * disabled users are blocked. SERVICE_REQUEST_* is granted to
 * OPERATIONS_COORDINATOR / MANAFY_ADMIN / FIELD_OFFICER / AREA_OPERATIONS_MANAGER /
 * SUPPORT_AGENT (view+create); FINANCE / HR / REPORTING get none.
 */
class ServiceRequestRbacIT extends IntegrationTestBase {

    @Autowired AuthzFixtures fx;

    private String admin(String tag) {
        String sub = "sr-rbac-admin-" + tag + "-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub("admin", sub, "ACTIVE");
        fx.assignRole(u.getId(), "MANAFY_ADMIN");
        fx.grantGlobalScope(u.getId());
        return sub;
    }

    private JsonNode data(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
    }

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
        m.put("category", "GENERAL");
        m.put("description", "Test request");
        return m;
    }

    // ─── Authorized create ───────────────────────────────────────────

    @Test
    void operationsCoordinatorCanCreate() throws Exception {
        String adminSub = admin("opc");
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        UUID aptId = createApartment(adminSub, r.getId(), a.getId());

        String sub = "sr-opc-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub("opc", sub, "ACTIVE");
        fx.assignRole(u.getId(), "OPERATIONS_COORDINATOR");
        fx.grantGlobalScope(u.getId());
        mockMvc.perform(post("/api/v1/service-requests").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(srBody(aptId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEW"));
    }

    // ─── Unauthorized create ─────────────────────────────────────────

    @Test
    void financeCannotCreate() throws Exception {
        String adminSub = admin("fin");
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        UUID aptId = createApartment(adminSub, r.getId(), a.getId());

        // FINANCE has NO service-request permission in Phase 4A.
        String sub = "sr-fin-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub("fin", sub, "ACTIVE");
        fx.assignRole(u.getId(), "FINANCE");
        fx.grantGlobalScope(u.getId());
        mockMvc.perform(post("/api/v1/service-requests").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(srBody(aptId))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void userWithoutRoleCannotView() throws Exception {
        String adminSub = admin("norole");
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        UUID aptId = createApartment(adminSub, r.getId(), a.getId());
        UUID srId = UUID.fromString(data(mockMvc.perform(post("/api/v1/service-requests").with(asCognito(adminSub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(srBody(aptId))))
                .andExpect(status().isOk()).andReturn()).get("id").asText());

        String sub = "sr-norole-" + UUID.randomUUID();
        fx.createUserWithSub("nobody", sub, "ACTIVE"); // no roles
        mockMvc.perform(get("/api/v1/service-requests/" + srId).with(asCognito(sub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    // ─── Authorized view ─────────────────────────────────────────────

    @Test
    void supportAgentCanViewButNotCancel() throws Exception {
        String adminSub = admin("sup");
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        UUID aptId = createApartment(adminSub, r.getId(), a.getId());
        UUID srId = UUID.fromString(data(mockMvc.perform(post("/api/v1/service-requests").with(asCognito(adminSub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(srBody(aptId))))
                .andExpect(status().isOk()).andReturn()).get("id").asText());

        String sub = "sr-sup-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub("sup", sub, "ACTIVE");
        fx.assignRole(u.getId(), "SUPPORT_AGENT");
        fx.grantGlobalScope(u.getId());
        // Support can view (has SERVICE_REQUEST_VIEW).
        mockMvc.perform(get("/api/v1/service-requests/" + srId).with(asCognito(sub)))
                .andExpect(status().isOk());
        // Support CANNOT cancel (no SERVICE_REQUEST_CANCEL) → 403.
        mockMvc.perform(post("/api/v1/service-requests/" + srId + "/cancel").with(asCognito(sub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    // ─── Disabled user ───────────────────────────────────────────────

    @Test
    void disabledUserCannotCreate() throws Exception {
        String adminSub = admin("dis");
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        UUID aptId = createApartment(adminSub, r.getId(), a.getId());

        String sub = "sr-disabled-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub("disabled", sub, "DISABLED");
        fx.assignRole(u.getId(), "MANAFY_ADMIN");
        fx.grantGlobalScope(u.getId());
        mockMvc.perform(post("/api/v1/service-requests").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(srBody(aptId))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH_DISABLED"));
    }
}
