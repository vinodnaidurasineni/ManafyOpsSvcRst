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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 4B dispatch HTTP integration tests: full assignment lifecycle, request↔
 * assignment projection, eligible technicians, queues, reassignment (history), and
 * scheduling validation — through the full controller + security + authz stack.
 */
class DispatchHttpIT extends IntegrationTestBase {

    @Autowired AuthzFixtures fx;
    @Autowired DispatchFixtures df;

    /** Operations coordinator with global scope — full dispatch persona. */
    private String coordinator(String tag) {
        String sub = "disp-opc-" + tag + "-" + UUID.randomUUID();
        OpsUser u = fx.createUserWithSub("opc", sub, "ACTIVE");
        fx.assignRole(u.getId(), "OPERATIONS_COORDINATOR");
        fx.grantGlobalScope(u.getId());
        return sub;
    }

    private JsonNode data(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
    }

    /** Build a NEW plumbing request with one eligible technician in the same area. */
    private Ctx ctx() {
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        var apt = df.apartment(r.getId(), a.getId());
        UUID requester = fx.createActiveUser("req").getId();
        ServiceRequest sr = df.serviceRequest(apt.getId(), a.getId(), r.getId(), requester, "PLUMBING");
        Technician tech = df.eligibleTechnician(a.getId(), "PLUMBING");
        return new Ctx(r, a, sr, tech);
    }

    private record Ctx(Region region, Area area, ServiceRequest sr, Technician tech) {}

    private UUID createAssignment(String sub, UUID srId, UUID techId) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/service-requests/" + srId + "/assignments").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("technicianId", techId.toString()))))
                .andExpect(status().isOk()).andReturn();
        return UUID.fromString(data(res).get("id").asText());
    }

    // ─── Eligible technicians ────────────────────────────────────────

    @Test
    void eligibleTechniciansListsMatch() throws Exception {
        String sub = coordinator("elig");
        Ctx c = ctx();
        mockMvc.perform(get("/api/v1/service-requests/" + c.sr().getId() + "/eligible-technicians").with(asCognito(sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].technicianId").value(c.tech().getId().toString()));
    }

    // ─── Create assignment + projection ──────────────────────────────

    @Test
    void createAssignmentProjectsRequestToAssigned() throws Exception {
        String sub = coordinator("create");
        Ctx c = ctx();
        UUID asgId = createAssignment(sub, c.sr().getId(), c.tech().getId());

        mockMvc.perform(get("/api/v1/assignments/" + asgId).with(asCognito(sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.active").value(true));
        // Request projected to ASSIGNED.
        mockMvc.perform(get("/api/v1/service-requests/" + c.sr().getId()).with(asCognito(sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"));
    }

    @Test
    void ineligibleTechnicianRejected() throws Exception {
        String sub = coordinator("inelig");
        Region r = fx.region("R"); Area a = fx.area("A", r.getId());
        var apt = df.apartment(r.getId(), a.getId());
        ServiceRequest sr = df.serviceRequest(apt.getId(), a.getId(), r.getId(),
                fx.createActiveUser("req").getId(), "PLUMBING");
        // Technician has no skill/area → ineligible.
        Technician tech = df.technician("ACTIVE", "AVAILABLE");
        mockMvc.perform(post("/api/v1/service-requests/" + sr.getId() + "/assignments").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("technicianId", tech.getId().toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // ─── Full happy-path lifecycle ───────────────────────────────────

    @Test
    void fullLifecycleAcceptToComplete() throws Exception {
        String sub = coordinator("full");
        Ctx c = ctx();
        UUID asgId = createAssignment(sub, c.sr().getId(), c.tech().getId());

        act(sub, asgId, "accept");
        act(sub, asgId, "en-route");
        act(sub, asgId, "arrive");
        act(sub, asgId, "start");
        // After start, the request is IN_PROGRESS.
        mockMvc.perform(get("/api/v1/service-requests/" + c.sr().getId()).with(asCognito(sub)))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        mockMvc.perform(post("/api/v1/assignments/" + asgId + "/complete").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("notes", "Fixed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        // Request projected to COMPLETED.
        mockMvc.perform(get("/api/v1/service-requests/" + c.sr().getId()).with(asCognito(sub)))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    private void act(String sub, UUID asgId, String action) throws Exception {
        mockMvc.perform(post("/api/v1/assignments/" + asgId + "/" + action).with(asCognito(sub)))
                .andExpect(status().isOk());
    }

    @Test
    void cannotStartBeforeAccept() throws Exception {
        String sub = coordinator("order");
        Ctx c = ctx();
        UUID asgId = createAssignment(sub, c.sr().getId(), c.tech().getId());
        // ASSIGNED → IN_PROGRESS is illegal (must accept/en-route/arrive first).
        mockMvc.perform(post("/api/v1/assignments/" + asgId + "/start").with(asCognito(sub)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATE_TRANSITION"));
    }

    // ─── Decline returns request to NEW ──────────────────────────────

    @Test
    void declineReturnsRequestToNew() throws Exception {
        String sub = coordinator("decline");
        Ctx c = ctx();
        UUID asgId = createAssignment(sub, c.sr().getId(), c.tech().getId());
        mockMvc.perform(post("/api/v1/assignments/" + asgId + "/decline").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Busy"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DECLINED"))
                .andExpect(jsonPath("$.data.active").value(false));
        mockMvc.perform(get("/api/v1/service-requests/" + c.sr().getId()).with(asCognito(sub)))
                .andExpect(jsonPath("$.data.status").value("NEW"));
    }

    // ─── Reassignment preserves history ──────────────────────────────

    @Test
    void reassignClosesOldAndCreatesNewPreservingHistory() throws Exception {
        String sub = coordinator("reassign");
        Ctx c = ctx();
        UUID asgId = createAssignment(sub, c.sr().getId(), c.tech().getId());
        Technician tech2 = df.eligibleTechnician(c.area().getId(), "PLUMBING");

        MvcResult res = mockMvc.perform(post("/api/v1/assignments/" + asgId + "/reassign").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("technicianId", tech2.getId().toString(),
                                "reason", "Original unavailable"))))
                .andExpect(status().isOk()).andReturn();
        JsonNode newAsg = data(res);
        assertThat(newAsg.get("previousAssignmentId").asText()).isEqualTo(asgId.toString());
        assertThat(newAsg.get("technicianId").asText()).isEqualTo(tech2.getId().toString());

        // Old assignment preserved: still shows the ORIGINAL technician, now CANCELLED/inactive.
        mockMvc.perform(get("/api/v1/assignments/" + asgId).with(asCognito(sub)))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.technicianId").value(c.tech().getId().toString()));

        // Request lists BOTH assignments (history preserved).
        mockMvc.perform(get("/api/v1/service-requests/" + c.sr().getId() + "/assignments").with(asCognito(sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ─── No-show returns to NEW ──────────────────────────────────────

    @Test
    void noShowReturnsRequestToNew() throws Exception {
        String sub = coordinator("noshow");
        Ctx c = ctx();
        UUID asgId = createAssignment(sub, c.sr().getId(), c.tech().getId());
        act(sub, asgId, "accept");
        mockMvc.perform(post("/api/v1/assignments/" + asgId + "/no-show").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Did not arrive"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NO_SHOW"));
        mockMvc.perform(get("/api/v1/service-requests/" + c.sr().getId()).with(asCognito(sub)))
                .andExpect(jsonPath("$.data.status").value("NEW"));
    }

    // ─── Rework ──────────────────────────────────────────────────────

    @Test
    void reworkReopensCompletedRequest() throws Exception {
        String sub = coordinator("rework");
        Ctx c = ctx();
        UUID asgId = createAssignment(sub, c.sr().getId(), c.tech().getId());
        act(sub, asgId, "accept"); act(sub, asgId, "en-route"); act(sub, asgId, "arrive"); act(sub, asgId, "start");
        mockMvc.perform(post("/api/v1/assignments/" + asgId + "/complete").with(asCognito(sub)))
                .andExpect(status().isOk());
        Technician tech2 = df.eligibleTechnician(c.area().getId(), "PLUMBING");
        mockMvc.perform(post("/api/v1/assignments/" + asgId + "/rework").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("technicianId", tech2.getId().toString(),
                                "reason", "Leak persists"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"));
        // Request came back to ASSIGNED (new rework assignment active).
        mockMvc.perform(get("/api/v1/service-requests/" + c.sr().getId()).with(asCognito(sub)))
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"));
    }

    // ─── Scheduling validation ───────────────────────────────────────

    @Test
    void rescheduleRejectsEndBeforeStart() throws Exception {
        String sub = coordinator("sched");
        Ctx c = ctx();
        UUID asgId = createAssignment(sub, c.sr().getId(), c.tech().getId());
        mockMvc.perform(post("/api/v1/assignments/" + asgId + "/reschedule").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "scheduledStart", "2026-01-02T10:00:00",
                                "scheduledEnd", "2026-01-02T09:00:00"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // ─── Duplicate active assignment blocked ─────────────────────────

    @Test
    void secondActiveAssignmentBlocked() throws Exception {
        String sub = coordinator("dup");
        Ctx c = ctx();
        createAssignment(sub, c.sr().getId(), c.tech().getId());
        Technician tech2 = df.eligibleTechnician(c.area().getId(), "PLUMBING");
        // Once assigned, the request is no longer awaiting assignment → 409. The
        // request-state guard fires first (INVALID_STATE_TRANSITION); the active-
        // assignment guard is defense-in-depth behind it.
        mockMvc.perform(post("/api/v1/service-requests/" + c.sr().getId() + "/assignments").with(asCognito(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("technicianId", tech2.getId().toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATE_TRANSITION"));
    }

    // ─── Queues ──────────────────────────────────────────────────────

    @Test
    void dispatchQueueShowsUnassigned() throws Exception {
        String sub = coordinator("dq");
        Ctx c = ctx();
        mockMvc.perform(get("/api/v1/operations/dispatch/queue").param("areaId", c.area().getId().toString())
                        .with(asCognito(sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].dispatchReason").value("UNASSIGNED"));
    }

    @Test
    void operationalQueueShowsAssignedWithTechnician() throws Exception {
        String sub = coordinator("oq");
        Ctx c = ctx();
        createAssignment(sub, c.sr().getId(), c.tech().getId());
        mockMvc.perform(get("/api/v1/operations/service-requests/queue")
                        .param("areaId", c.area().getId().toString())
                        .param("status", "ASSIGNED").with(asCognito(sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].technicianId").value(c.tech().getId().toString()));
    }

    // ─── Idempotency + concurrency ───────────────────────────────────

    @Test
    void duplicateCreateKeyRejected() throws Exception {
        String sub = coordinator("idem");
        Ctx c = ctx();
        String key = "asg-create-" + UUID.randomUUID();
        mockMvc.perform(post("/api/v1/service-requests/" + c.sr().getId() + "/assignments").with(asCognito(sub))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("technicianId", c.tech().getId().toString()))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/service-requests/" + c.sr().getId() + "/assignments").with(asCognito(sub))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("technicianId", c.tech().getId().toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_REQUEST"));
    }

    @Test
    void staleVersionAcceptReturns409() throws Exception {
        String sub = coordinator("ver");
        Ctx c = ctx();
        UUID asgId = createAssignment(sub, c.sr().getId(), c.tech().getId());
        mockMvc.perform(post("/api/v1/assignments/" + asgId + "/accept").param("version", "99").with(asCognito(sub)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENCY_CONFLICT"));
    }
}
