package com.manafy.ops.adminapi;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.maidapp.entity.Complaint;
import com.manafy.ops.maidapp.repository.ComplaintRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin complaints (ManafyOps compatibility layer) — GET list/detail,
 * PUT status/assign. Returns the exact shape the ComplaintsScreen consumes
 * (id, complaintType, complaintStatus, description, customerName/Mobile,
 * maidName/Mobile, adminNotes) + escalation metrics.
 */
@RestController
@RequestMapping("/api/v1/admin/complaints")
public class AdminComplaintController {

    private final ComplaintRepository complaintRepository;

    public AdminComplaintController(ComplaintRepository complaintRepository) {
        this.complaintRepository = complaintRepository;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String status) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Complaint c : complaintRepository.findByDeletedFalseOrderByCreatedAtDesc()) {
            if (status != null && !status.isBlank() && !status.equalsIgnoreCase(c.getComplaintStatus())) continue;
            out.add(toMap(c));
        }
        return ApiResponse.ok(out);
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable UUID id) {
        return ApiResponse.ok(toMap(get(id)));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Map<String, String>> updateStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        Complaint c = get(id);
        if (body.get("status") != null) c.setComplaintStatus(body.get("status").toUpperCase());
        if (body.get("adminNotes") != null) c.setAdminNotes(body.get("adminNotes"));
        c.setUpdatedBy("ADMIN");
        complaintRepository.save(c);
        return ApiResponse.ok(Map.of("status", c.getComplaintStatus()));
    }

    @PutMapping("/{id}/assign")
    public ApiResponse<Map<String, String>> assign(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body) {
        Complaint c = get(id);
        c.setComplaintStatus("IN_PROGRESS");
        if (body != null && body.get("adminNotes") != null) c.setAdminNotes(body.get("adminNotes"));
        c.setUpdatedBy("ADMIN");
        complaintRepository.save(c);
        return ApiResponse.ok(Map.of("status", "IN_PROGRESS", "complaintId", id.toString()));
    }

    private Complaint get(UUID id) {
        return complaintRepository.findById(id)
                .orElseThrow(() -> new BusinessException("COMPLAINT_001", "Complaint not found", HttpStatus.NOT_FOUND));
    }

    private Map<String, Object> toMap(Complaint c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId().toString());
        m.put("complaintType", c.getComplaintType());
        m.put("complaintStatus", c.getComplaintStatus());
        m.put("description", c.getDescription());
        m.put("adminNotes", c.getAdminNotes());
        m.put("customerName", c.getCustomerName());
        m.put("customerMobile", c.getCustomerMobile());
        m.put("maidName", c.getMaidName());
        m.put("maidMobile", c.getMaidMobile());
        m.put("currentEscalationLevel", c.getCurrentEscalationLevel());
        m.put("slaBreached", c.getSlaBreached());
        m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
        return m;
    }
}
