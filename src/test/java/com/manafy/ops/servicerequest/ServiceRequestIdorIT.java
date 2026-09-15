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
 * Phase 4A IDOR / scope tests. A service request is anchored to its apartment's
 * area+region (server-resolved). A Field Officer scoped to a different area must not
 * read or cancel it by changing the UUID — fail-closed with SCOPE_DENIED and no body.
 * A Field Officer of the owning area may access it.
 */
class ServiceRequestIdorIT extends IntegrationTestBase {

    @Autowired AuthzFixtures fx;

    private String admin(String tag) {
        String sub = "sr-idor-admin-" + tag + "-" + UUID.randomUUID();
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

    private UUID createServiceRequest(String sub, UUID apartmentId) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("apartmentId", apartmentId.toString());
        m.put("category", "ELECTRICAL");
        m.put("description", "Power outage");
        MvcResult res = mockMvc.perform(post("/api/v1/service-requests").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(m)))
                .andExpect(status().isOk()).andReturn();
        return UUID.fromString(data(res).get("id").asText());
    }

    private OpsUser fieldOfficerOfArea(String tag, UUID areaId) {
        String sub = "sr-fo-" + tag + "-" + UUID.randomUUID();
        OpsUser fo = fx.createUserWithSub("fo", sub, "ACTIVE");
        fo.setCognitoSub(sub);
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        fx.assignFieldOfficer(areaId, fo.getId(), "PRIMARY");
        return fo;
    }

    // ─── Cross-area read denial ──────────────────────────────────────

    @Test
    void fieldOfficerCannotReadRequestOutsideArea() throws Exception {
        String adminSub = admin("read");
        Region r = fx.region("R");
        Area areaA = fx.area("A", r.getId());
        Area areaB = fx.area("B", r.getId());
        UUID aptA = createApartment(adminSub, r.getId(), areaA.getId());
        UUID srInA = createServiceRequest(adminSub, aptA);

        // FO owns area B → cannot read a request anchored to area A.
        OpsUser foB = fieldOfficerOfArea("b", areaB.getId());
        mockMvc.perform(get("/api/v1/service-requests/" + srInA).with(asCognito(foB.getCognitoSub())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SCOPE_DENIED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void fieldOfficerCanReadRequestInOwnArea() throws Exception {
        String adminSub = admin("readown");
        Region r = fx.region("R");
        Area areaA = fx.area("A", r.getId());
        UUID aptA = createApartment(adminSub, r.getId(), areaA.getId());
        UUID srInA = createServiceRequest(adminSub, aptA);

        OpsUser foA = fieldOfficerOfArea("a", areaA.getId());
        mockMvc.perform(get("/api/v1/service-requests/" + srInA).with(asCognito(foA.getCognitoSub())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(srInA.toString()));
    }

    // ─── Cross-area cancel denial ────────────────────────────────────

    @Test
    void fieldOfficerCannotCancelRequestOutsideArea() throws Exception {
        String adminSub = admin("cancel");
        Region r = fx.region("R");
        Area areaA = fx.area("A", r.getId());
        Area areaB = fx.area("B", r.getId());
        UUID aptA = createApartment(adminSub, r.getId(), areaA.getId());
        UUID srInA = createServiceRequest(adminSub, aptA);

        OpsUser foB = fieldOfficerOfArea("b", areaB.getId());
        mockMvc.perform(post("/api/v1/service-requests/" + srInA + "/cancel").with(asCognito(foB.getCognitoSub())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SCOPE_DENIED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ─── List scope isolation ────────────────────────────────────────

    @Test
    void listExcludesOutOfScopeRequests() throws Exception {
        String adminSub = admin("listscope");
        Region r = fx.region("R");
        Area areaA = fx.area("A", r.getId());
        Area areaB = fx.area("B", r.getId());
        UUID aptA = createApartment(adminSub, r.getId(), areaA.getId());
        createServiceRequest(adminSub, aptA);

        // FO of area B lists → sees none of area A's requests.
        OpsUser foB = fieldOfficerOfArea("b", areaB.getId());
        mockMvc.perform(get("/api/v1/service-requests").with(asCognito(foB.getCognitoSub())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(0));
    }
}
