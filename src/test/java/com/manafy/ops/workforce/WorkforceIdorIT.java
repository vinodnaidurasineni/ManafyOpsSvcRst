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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 3 IDOR + cross-area scope tests. A technician is anchored to the areas it
 * covers (workforce_area). A user scoped to a different area (or with no coverage)
 * must not be able to read it by id — enforced fail-closed with SCOPE_DENIED and no
 * response body. PII is masked for callers lacking TECHNICIAN_PII_VIEW.
 */
class WorkforceIdorIT extends IntegrationTestBase {

    @Autowired AuthzFixtures fx;

    private String hrGlobal(String tag) {
        String sub = "wf-idor-hr-" + tag + "-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub("hr", sub, "ACTIVE");
        fx.assignRole(u.getId(), "HR");
        fx.grantGlobalScope(u.getId());
        return sub;
    }

    private JsonNode data(org.springframework.test.web.servlet.MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
    }

    /** HR creates an ACTIVE technician anchored to the given area. Returns technician id. */
    private UUID createTechnicianInArea(String hrSub, UUID areaId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "T-" + UUID.randomUUID());
        body.put("name", "Tech");
        var res = mockMvc.perform(post("/api/v1/technicians").with(asCognito(hrSub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
        UUID techId = UUID.fromString(data(res).get("id").asText());
        mockMvc.perform(post("/api/v1/technicians/" + techId + "/activate").with(asCognito(hrSub)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/technicians/" + techId + "/areas").with(asCognito(hrSub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("areaId", areaId.toString()))))
                .andExpect(status().isOk());
        return techId;
    }

    // ─── Cross-area technician read denial ───────────────────────────

    @Test
    void fieldOfficerCannotReadTechnicianOutsideArea() throws Exception {
        String hrSub = hrGlobal("cross");
        Region r = fx.region("R");
        Area areaA = fx.area("A", r.getId());
        Area areaB = fx.area("B", r.getId());
        UUID techInA = createTechnicianInArea(hrSub, areaA.getId());

        // FO of area B (scope = area B) tries to read a technician anchored to area A.
        String foSub = "wf-idor-fo-" + UUID.randomUUID();
        OpsUser fo = fx.createUserWithSub("fo", foSub, "ACTIVE");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        fx.grantAreaScope(fo.getId(), areaB.getId());

        mockMvc.perform(get("/api/v1/technicians/" + techInA).with(asCognito(foSub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SCOPE_DENIED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void fieldOfficerCanReadTechnicianInOwnArea() throws Exception {
        String hrSub = hrGlobal("inarea");
        Region r = fx.region("R");
        Area areaA = fx.area("A", r.getId());
        UUID techInA = createTechnicianInArea(hrSub, areaA.getId());

        String foSub = "wf-idor-fo2-" + UUID.randomUUID();
        OpsUser fo = fx.createUserWithSub("fo", foSub, "ACTIVE");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        fx.grantAreaScope(fo.getId(), areaA.getId());

        // FO of area A may read; but lacks PII_VIEW so PII is masked.
        mockMvc.perform(get("/api/v1/technicians/" + techInA).with(asCognito(foSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.piiMasked").value(true));
    }

    @Test
    void areaScopedUserCannotReadUnanchoredTechnician() throws Exception {
        String hrSub = hrGlobal("unanchored");
        // Technician with NO area anchor — only GLOBAL scope should pass.
        Map<String, Object> body = Map.of("code", "T-" + UUID.randomUUID(), "name", "Tech");
        var res = mockMvc.perform(post("/api/v1/technicians").with(asCognito(hrSub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
        UUID techId = UUID.fromString(data(res).get("id").asText());

        Region r = fx.region("R");
        Area area = fx.area("A", r.getId());
        String foSub = "wf-idor-fo3-" + UUID.randomUUID();
        OpsUser fo = fx.createUserWithSub("fo", foSub, "ACTIVE");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        fx.grantAreaScope(fo.getId(), area.getId());

        mockMvc.perform(get("/api/v1/technicians/" + techId).with(asCognito(foSub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SCOPE_DENIED"));
    }

    // ─── PII masking for global HR without PII_VIEW is NOT the case ──

    @Test
    void hrSeesUnmaskedPii() throws Exception {
        String hrSub = hrGlobal("pii");
        Map<String, Object> body = new HashMap<>();
        body.put("code", "T-" + UUID.randomUUID());
        body.put("name", "Tech");
        body.put("phone", "9998887777");
        var res = mockMvc.perform(post("/api/v1/technicians").with(asCognito(hrSub))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
        UUID techId = UUID.fromString(data(res).get("id").asText());

        // HR holds TECHNICIAN_PII_VIEW → unmasked.
        mockMvc.perform(get("/api/v1/technicians/" + techId).with(asCognito(hrSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.piiMasked").value(false))
                .andExpect(jsonPath("$.data.phone").value("9998887777"));
    }

    // ─── Document IDOR: not-found for missing document ───────────────

    @Test
    void unknownDocumentReturnsNotFound() throws Exception {
        String hrSub = hrGlobal("nodoc");
        mockMvc.perform(get("/api/v1/workforce-documents/" + UUID.randomUUID()).with(asCognito(hrSub)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }
}
