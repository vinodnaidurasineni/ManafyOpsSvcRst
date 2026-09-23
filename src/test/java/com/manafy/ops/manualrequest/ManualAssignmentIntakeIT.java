package com.manafy.ops.manualrequest;

import com.manafy.ops.manualrequest.entity.ManualAssignmentRequest;
import com.manafy.ops.manualrequest.repository.ManualAssignmentRequestRepository;
import com.manafy.ops.support.IntegrationTestBase;
import com.manafy.ops.workforce.entity.Helper;
import com.manafy.ops.workforce.repository.HelperRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end intake behaviour through the real controller + DB:
 *  - a MAID request is AUTO-ASSIGNED to the lowest-daily-workload available maid,
 *  - a PLUMBER request is NOT auto-assigned (stays QUEUED for manual handling),
 *  - an UNAVAILABLE maid is never chosen.
 *
 * Intake is service-to-service (X-Service-Token), so no Ops JWT is needed here.
 */
class ManualAssignmentIntakeIT extends IntegrationTestBase {

    private static final String SVC_TOKEN = "manafy-community-ops-shared-secret-dev";

    @Autowired HelperRepository helperRepo;
    @Autowired ManualAssignmentRequestRepository requestRepo;

    private Helper seedHelper(String code, String name, String category, String availability) {
        Helper h = new Helper();
        h.setCode(code + "-" + UUID.randomUUID());
        h.setName(name);
        h.setCategory(category);
        h.setPhone("9900000000");
        h.setRelationship("MANAFY");
        h.setStatus("ACTIVE");
        h.setAvailabilityStatus(availability);
        return helperRepo.save(h);
    }

    /** Give a helper `count` completed assignments dated today (raises daily workload). */
    private void loadToday(Helper h, int count) {
        for (int i = 0; i < count; i++) {
            ManualAssignmentRequest r = new ManualAssignmentRequest();
            r.setReferenceNo("MR-LOAD-" + UUID.randomUUID());
            r.setSourceSystem("TEST");
            r.setSourceType("RECURRING_HELPER_REQUEST");
            r.setSourceId("LOAD-" + UUID.randomUUID());
            r.setServiceType(h.getCategory());
            r.setStartDate(LocalDate.now());
            r.setStatus("COMPLETED");
            r.setAssigneeType("OPS_WORKFORCE");
            r.setAssigneeRef(h.getId().toString());
            r.setAssigneeName(h.getName());
            requestRepo.save(r);
        }
    }

    private String intakeBody(String sourceId, String serviceType) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "sourceSystem", "COMMUNITY",
                "sourceType", "RECURRING_HELPER_REQUEST",
                "sourceId", sourceId,
                "serviceType", serviceType,
                "frequency", "DAILY",
                "startDate", LocalDate.now().toString(),
                "timeSlot", "MORNING",
                "residentName", "Test Resident",
                "contactNumber", "9845000000"));
    }

    @Test
    void maidRequestAutoAssignsLowestWorkloadAvailableMaid() throws Exception {
        Helper busy = seedHelper("H-MAID-BUSY", "BusyMaid", "MAID", "AVAILABLE");
        Helper free = seedHelper("H-MAID-FREE", "FreeMaid", "MAID", "AVAILABLE");
        loadToday(busy, 4);   // 4 today
        loadToday(free, 1);   // 1 today → should win

        mockMvc.perform(post("/api/v1/manual-requests/intake")
                        .header("X-Service-Token", SVC_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(intakeBody("IT-MAID-" + UUID.randomUUID(), "MAID")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.assigneeName").value("FreeMaid"))
                .andExpect(jsonPath("$.data.assigneeType").value("OPS_WORKFORCE"));
    }

    @Test
    void unavailableMaidIsNeverAutoAssigned() throws Exception {
        // Only maid is UNAVAILABLE → nothing eligible → request stays QUEUED.
        seedHelper("H-MAID-OFF", "OfflineMaid", "MAID", "UNAVAILABLE");

        mockMvc.perform(post("/api/v1/manual-requests/intake")
                        .header("X-Service-Token", SVC_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(intakeBody("IT-MAID-OFF-" + UUID.randomUUID(), "MAID")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.assigneeName").doesNotExist());
    }

    @Test
    void plumberRequestIsNotAutoAssigned() throws Exception {
        seedHelper("H-PLUMB", "AvailablePlumber", "PLUMBER", "AVAILABLE");

        mockMvc.perform(post("/api/v1/manual-requests/intake")
                        .header("X-Service-Token", SVC_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(intakeBody("IT-PLUMB-" + UUID.randomUUID(), "PLUMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    void intakeRejectsMissingServiceToken() throws Exception {
        mockMvc.perform(post("/api/v1/manual-requests/intake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(intakeBody("IT-NOAUTH-" + UUID.randomUUID(), "MAID")))
                .andExpect(status().isForbidden());
    }
}
