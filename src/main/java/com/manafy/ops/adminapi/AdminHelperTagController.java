package com.manafy.ops.adminapi;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.maidapp.entity.HelperTag;
import com.manafy.ops.maidapp.repository.HelperTagRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin Helper-Tags (ManafyOps compatibility layer). Resident "tag your helper"
 * leads for ops follow-up. Serves the exact shape the HelperTagsScreen expects.
 */
@RestController
@RequestMapping("/api/v1/admin/helper-tags")
public class AdminHelperTagController {

    private final HelperTagRepository repo;

    public AdminHelperTagController(HelperTagRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String status,
                                                        @RequestParam(required = false, defaultValue = "30") int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Map<String, Object>> out = new ArrayList<>();
        for (HelperTag t : repo.findByCreatedAtAfterAndDeletedFalseOrderByCreatedAtDesc(since)) {
            if (status != null && !status.isBlank() && !status.equalsIgnoreCase(t.getStatus())) continue;
            out.add(toMap(t));
        }
        return ApiResponse.ok(out);
    }

    @GetMapping("/counts")
    public ApiResponse<Map<String, Long>> counts(@RequestParam(required = false, defaultValue = "30") int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<HelperTag> tags = repo.findByCreatedAtAfterAndDeletedFalseOrderByCreatedAtDesc(since);
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("ALL", (long) tags.size());
        for (String s : List.of("PENDING", "CONTACTED", "ONBOARDED", "REJECTED")) {
            counts.put(s, tags.stream().filter(t -> s.equals(t.getStatus())).count());
        }
        return ApiResponse.ok(counts);
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Map<String, String>> updateStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        HelperTag t = repo.findById(id)
                .orElseThrow(() -> new BusinessException("TAG_404", "Helper tag not found", HttpStatus.NOT_FOUND));
        if (body.get("status") != null) t.setStatus(body.get("status").toUpperCase());
        t.setUpdatedBy("ADMIN");
        repo.save(t);
        return ApiResponse.ok(Map.of("status", t.getStatus()));
    }

    private Map<String, Object> toMap(HelperTag t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId().toString());
        m.put("helperName", t.getHelperName());
        m.put("helperMobile", t.getHelperMobile());
        m.put("customerName", t.getCustomerName());
        m.put("customerMobile", t.getCustomerMobile());
        m.put("apartmentName", t.getApartmentName());
        m.put("flatNumber", t.getFlatNumber());
        m.put("status", t.getStatus());
        m.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
        return m;
    }
}
