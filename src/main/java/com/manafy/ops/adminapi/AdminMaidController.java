package com.manafy.ops.adminapi;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.maidapp.repository.BookingAssignmentRepository;
import com.manafy.ops.workforce.entity.Helper;
import com.manafy.ops.workforce.repository.HelperRepository;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin maid-management endpoints (ManafyOps compatibility layer).
 *
 * The ManafyOps admin app treats "maids" as workforce Helpers (category MAID)
 * and also uses the generic employees API. This controller serves the exact
 * paths + JSON shapes the app expects (/admin/employees, /admin/maids), backed
 * by the ops Helper workforce. Fields are mapped to the app's contract
 * (firstName/lastName/employeeCode/mobileNumber/status).
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminMaidController {

    private final HelperRepository helperRepository;
    private final BookingAssignmentRepository assignmentRepository;

    public AdminMaidController(HelperRepository helperRepository,
                               BookingAssignmentRepository assignmentRepository) {
        this.helperRepository = helperRepository;
        this.assignmentRepository = assignmentRepository;
    }

    // ─── Employees (app: adminApi.getEmployees / getEmployee / createEmployee / updateEmployee) ───

    @GetMapping("/employees")
    public ApiResponse<List<Map<String, Object>>> listEmployees(@RequestParam(required = false) String role,
                                                                @RequestParam(required = false) String status) {
        // ManageMaids filters role=HELPER; we surface the MAID helper workforce.
        List<Helper> helpers = helperRepository.findByCategoryAndDeletedFalse("MAID");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Helper h : helpers) {
            if (status != null && !status.isBlank() && !mapStatus(h.getStatus()).equals(status)) continue;
            out.add(toEmployee(h));
        }
        return ApiResponse.ok(out);
    }

    @GetMapping("/employees/{id}")
    public ApiResponse<Map<String, Object>> getEmployee(@PathVariable UUID id) {
        return ApiResponse.ok(helperRepository.findByIdAndDeletedFalse(id)
                .map(this::toEmployee).orElse(Map.of()));
    }

    @PostMapping("/employees")
    public ApiResponse<Map<String, Object>> createEmployee(@RequestBody Map<String, Object> body) {
        Helper h = new Helper();
        String first = str(body.get("firstName"));
        String last = str(body.get("lastName"));
        h.setName(((first == null ? "" : first) + " " + (last == null ? "" : last)).trim());
        h.setPhone(str(body.getOrDefault("mobileNumber", body.get("phone"))));
        h.setCategory("MAID");
        h.setRelationship("MANAFY");
        h.setStatus("ACTIVE");
        h.setAvailabilityStatus("AVAILABLE");
        String code = str(body.get("employeeCode"));
        h.setCode(code != null ? code : "H-MAID-" + UUID.randomUUID().toString().substring(0, 8));
        h.setCreatedBy("ADMIN");
        Helper saved = helperRepository.save(h);
        return ApiResponse.ok(toEmployee(saved));
    }

    @PutMapping("/employees/{id}")
    public ApiResponse<Map<String, Object>> updateEmployee(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Helper h = helperRepository.findByIdAndDeletedFalse(id).orElse(null);
        if (h == null) return ApiResponse.ok(Map.of());
        if (body.get("status") != null) {
            String s = str(body.get("status"));
            h.setStatus("ACTIVE".equals(s) ? "ACTIVE" : "INACTIVE");
        }
        if (body.get("firstName") != null || body.get("lastName") != null) {
            String first = str(body.getOrDefault("firstName", ""));
            String last = str(body.getOrDefault("lastName", ""));
            h.setName((first + " " + last).trim());
        }
        if (body.get("mobileNumber") != null) h.setPhone(str(body.get("mobileNumber")));
        h.setUpdatedBy("ADMIN");
        return ApiResponse.ok(toEmployee(helperRepository.save(h)));
    }

    @DeleteMapping("/employees/{id}")
    public ApiResponse<Map<String, String>> deactivateEmployee(@PathVariable UUID id) {
        helperRepository.findByIdAndDeletedFalse(id).ifPresent(h -> {
            h.setStatus("INACTIVE");
            helperRepository.save(h);
        });
        return ApiResponse.ok(Map.of("status", "INACTIVE"));
    }

    // ─── /admin/maids (app: adminApi.getMaids / getMaidDetail / activate / deactivate / delete) ───

    @GetMapping("/maids")
    public ApiResponse<List<Map<String, Object>>> getMaids(@RequestParam(required = false) String status) {
        return listEmployees("HELPER", status);
    }

    @GetMapping("/maids/{maidId}")
    public ApiResponse<Map<String, Object>> getMaid(@PathVariable UUID maidId) {
        return getEmployee(maidId);
    }

    @PostMapping("/maids/{maidId}/activate")
    public ApiResponse<Map<String, String>> activateMaid(@PathVariable UUID maidId) {
        setStatus(maidId, "ACTIVE");
        return ApiResponse.ok(Map.of("status", "ACTIVE"));
    }

    @PostMapping("/maids/{maidId}/deactivate")
    public ApiResponse<Map<String, String>> deactivateMaid(@PathVariable UUID maidId) {
        setStatus(maidId, "INACTIVE");
        return ApiResponse.ok(Map.of("status", "INACTIVE"));
    }

    @DeleteMapping("/maids/{maidId}")
    public ApiResponse<Map<String, Object>> deleteMaid(@PathVariable UUID maidId,
                                                       @RequestBody(required = false) Map<String, Object> body) {
        helperRepository.findByIdAndDeletedFalse(maidId).ifPresent(h -> {
            h.setStatus("TERMINATED");
            h.setDeleted(true);
            helperRepository.save(h);
        });
        return ApiResponse.ok(Map.of("status", "DELETED", "archived", true));
    }

    @GetMapping("/maids/{maidId}/active-assignments")
    public ApiResponse<Map<String, Object>> activeAssignments(@PathVariable UUID maidId) {
        long count = assignmentRepository.findByMaidIdAndDeletedFalse(maidId).stream()
                .filter(a -> !"CANCELLED".equals(a.getAssignmentStatus()) && !"COMPLETED".equals(a.getAssignmentStatus()))
                .count();
        return ApiResponse.ok(Map.of("activeAssignments", count, "canDelete", count == 0));
    }

    // These sub-resources are accepted as no-ops for the maid slice (skills/apartments/
    // availability are managed on the workforce Helper/Technician endpoints).
    @PostMapping("/maids/{maidId}/skills")
    public ApiResponse<Map<String, Boolean>> addSkills(@PathVariable UUID maidId, @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(Map.of("updated", true));
    }

    @PostMapping("/maids/{maidId}/apartments")
    public ApiResponse<Map<String, Boolean>> mapApartments(@PathVariable UUID maidId, @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(Map.of("updated", true));
    }

    @PostMapping("/maids/{maidId}/availability")
    public ApiResponse<Map<String, Boolean>> setAvailability(@PathVariable UUID maidId, @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(Map.of("updated", true));
    }

    // ─── helpers ─────────────────────────────────────────────────────

    private void setStatus(UUID id, String status) {
        helperRepository.findByIdAndDeletedFalse(id).ifPresent(h -> {
            h.setStatus(status);
            helperRepository.save(h);
        });
    }

    /** Map a Helper to the app's employee/maid JSON shape. */
    private Map<String, Object> toEmployee(Helper h) {
        String name = h.getName() == null ? "" : h.getName();
        String first = name;
        String last = "";
        int sp = name.indexOf(' ');
        if (sp > 0) { first = name.substring(0, sp); last = name.substring(sp + 1); }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", h.getId().toString());
        m.put("firstName", first);
        m.put("lastName", last);
        m.put("employeeCode", h.getCode());
        m.put("mobileNumber", h.getPhone());
        m.put("role", "HELPER");
        m.put("category", h.getCategory());
        m.put("status", mapStatus(h.getStatus()));
        m.put("availabilityStatus", h.getAvailabilityStatus());
        m.put("city", "Bengaluru");
        return m;
    }

    /** Helper status → app's ACTIVE/INACTIVE vocabulary. */
    private String mapStatus(String status) {
        return "ACTIVE".equals(status) ? "ACTIVE" : "INACTIVE";
    }

    private String str(Object o) { return o == null ? null : o.toString(); }
}
