package com.manafy.ops.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.manafy.ops.identity.entity.OpsUser;
import com.manafy.ops.org.entity.Area;
import com.manafy.ops.org.entity.Region;
import com.manafy.ops.servicerequest.entity.ServiceRequest;
import com.manafy.ops.support.AuthzFixtures;
import com.manafy.ops.support.DispatchFixtures;
import com.manafy.ops.support.IntegrationTestBase;
import com.manafy.ops.workforce.entity.Technician;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 4B security tests: RBAC, area isolation, IDOR, disabled user, inactive/wrong
 * technician. Assignment scope is resolved through the parent request's area, so a
 * Field Officer or coordinator cannot reach an assignment outside their scope by id.
 */
class DispatchSecurityIT extends IntegrationTestBase {

    @Autowired AuthzFixtures fx;
    @Autowired DispatchFixtures df;

    private String globalCoordinator(String tag) {
        String sub = "sec-opc-" + tag + "-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub("opc", sub, "ACTIVE");
        fx.assignRole(u.getId(), "OPERATIONS_COORDINATOR");
        fx.grantGlobalScope(u.getId());
        return sub;
    }

    private JsonNode data(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
    }

    private record Ctx(Region region, Area area, ServiceRequest sr, Technician tech) {}

    private Ctx ctxInArea(Area a, Region r) {
        var apt = df.apartment(r.getId(), a.getId());
        ServiceRequest sr = df.serviceRequest(apt.getId(), a.getId(), r.getId(),
                fx.createActiveUser("req").getId(), "PLUMBING");
        Technician tech = df.eligibleTechnician(a.getId(), "PLUMBING");
        return new Ctx(r, a, sr, tech);
    }

    private UUID createAssignment(String sub, UUID srId, UUID techId) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/service-requests/" + srId + "/assignments").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("technicianId", techId.toString()))))
                .andExpect(status().isOk()).andReturn();
        return UUID.fromString(data(res).get("id").asText());
    }

    // ─── RBAC: unauthorized role ─────────────────────────────────────

    @Test
    void financeCannotCreateAssignment() throws Exception {
        String adminSub = globalCoordinator("fin-admin");
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        Ctx c = ctxInArea(a, r);

        String sub = "sec-fin-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub("fin", sub, "ACTIVE");
        fx.assignRole(u.getId(), "FINANCE");   // no ASSIGNMENT_* perms
        fx.grantGlobalScope(u.getId());
        mockMvc.perform(post("/api/v1/service-requests/" + c.sr().getId() + "/assignments").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("technicianId", c.tech().getId().toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    // ─── Area isolation: FO of another area ──────────────────────────

    @Test
    void fieldOfficerCannotViewAssignmentOutsideArea() throws Exception {
        String adminSub = globalCoordinator("fo-view");
        Region r = fx.region("R"); Area areaA = fx.area("A", r.getId()); Area areaB = fx.area("B", r.getId());
        Ctx c = ctxInArea(areaA, r);
        UUID asgId = createAssignment(adminSub, c.sr().getId(), c.tech().getId());

        // FO owns area B → cannot view an assignment for a request in area A.
        String foSub = "sec-fo-" + UUID.randomUUID();
        OpsUser fo = fx.createUserWithSub("fo", foSub, "ACTIVE");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        fx.assignFieldOfficer(areaB.getId(), fo.getId(), "PRIMARY");
        mockMvc.perform(get("/api/v1/assignments/" + asgId).with(asCognito(foSub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SCOPE_DENIED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void fieldOfficerCannotActOnAssignmentOutsideArea() throws Exception {
        String adminSub = globalCoordinator("fo-act");
        Region r = fx.region("R"); Area areaA = fx.area("A", r.getId()); Area areaB = fx.area("B", r.getId());
        Ctx c = ctxInArea(areaA, r);
        UUID asgId = createAssignment(adminSub, c.sr().getId(), c.tech().getId());

        String foSub = "sec-fo2-" + UUID.randomUUID();
        OpsUser fo = fx.createUserWithSub("fo", foSub, "ACTIVE");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        fx.assignFieldOfficer(areaB.getId(), fo.getId(), "PRIMARY");
        // FO of area B tries to accept an area-A assignment → SCOPE_DENIED.
        mockMvc.perform(post("/api/v1/assignments/" + asgId + "/accept").with(asCognito(foSub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SCOPE_DENIED"));
    }

    @Test
    void fieldOfficerCanActInOwnArea() throws Exception {
        String adminSub = globalCoordinator("fo-own");
        Region r = fx.region("R"); Area areaA = fx.area("A", r.getId());
        Ctx c = ctxInArea(areaA, r);
        UUID asgId = createAssignment(adminSub, c.sr().getId(), c.tech().getId());

        String foSub = "sec-fo3-" + UUID.randomUUID();
        OpsUser fo = fx.createUserWithSub("fo", foSub, "ACTIVE");
        fx.assignRole(fo.getId(), "FIELD_OFFICER");
        fx.assignFieldOfficer(areaA.getId(), fo.getId(), "PRIMARY");
        // FO of area A may accept an area-A assignment (ASSIGNMENT_ACCEPT + scope).
        mockMvc.perform(post("/api/v1/assignments/" + asgId + "/accept").with(asCognito(foSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
    }

    // ─── IDOR: assignment id cannot expose out-of-scope request ──────

    @Test
    void assignmentIdorAcrossAreaDenied() throws Exception {
        String adminSub = globalCoordinator("idor");
        Region r = fx.region("R"); Area areaA = fx.area("A", r.getId()); Area areaB = fx.area("B", r.getId());
        Ctx victim = ctxInArea(areaA, r);
        UUID victimAsg = createAssignment(adminSub, victim.sr().getId(), victim.tech().getId());

        // A coordinator scoped only to area B cannot read the area-A assignment by id.
        String subB = "sec-idor-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub("opcB", subB, "ACTIVE");
        fx.assignRole(u.getId(), "OPERATIONS_COORDINATOR");
        fx.grantAreaScope(u.getId(), areaB.getId());
        mockMvc.perform(get("/api/v1/assignments/" + victimAsg).with(asCognito(subB)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SCOPE_DENIED"));
    }

    // ─── Disabled user ───────────────────────────────────────────────

    @Test
    void disabledUserCannotCreateAssignment() throws Exception {
        String adminSub = globalCoordinator("dis-admin");
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        Ctx c = ctxInArea(a, r);

        String sub = "sec-disabled-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub("disabled", sub, "DISABLED");
        fx.assignRole(u.getId(), "OPERATIONS_COORDINATOR");
        fx.grantGlobalScope(u.getId());
        mockMvc.perform(post("/api/v1/service-requests/" + c.sr().getId() + "/assignments").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("technicianId", c.tech().getId().toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH_DISABLED"));
    }

    // ─── Inactive / invalid technician ───────────────────────────────

    @Test
    void inactiveTechnicianCannotBeAssigned() throws Exception {
        String sub = globalCoordinator("inactive");
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        var apt = df.apartment(r.getId(), a.getId());
        ServiceRequest sr = df.serviceRequest(apt.getId(), a.getId(), r.getId(),
                fx.createActiveUser("req").getId(), "PLUMBING");
        Technician t = df.technician("SUSPENDED", "AVAILABLE");
        df.coverArea(t.getId(), a.getId());
        df.grantSkill(t.getId(), df.skill("PLUMBING").getId());
        mockMvc.perform(post("/api/v1/service-requests/" + sr.getId() + "/assignments").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("technicianId", t.getId().toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void nonexistentTechnicianRejected() throws Exception {
        String sub = globalCoordinator("nulltech");
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        var apt = df.apartment(r.getId(), a.getId());
        ServiceRequest sr = df.serviceRequest(apt.getId(), a.getId(), r.getId(),
                fx.createActiveUser("req").getId(), "PLUMBING");
        mockMvc.perform(post("/api/v1/service-requests/" + sr.getId() + "/assignments").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("technicianId", UUID.randomUUID().toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}
