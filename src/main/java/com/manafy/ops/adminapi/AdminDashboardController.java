package com.manafy.ops.adminapi;

import com.manafy.ops.common.dto.ApiResponse;
import com.manafy.ops.maidapp.repository.BookingAssignmentRepository;
import com.manafy.ops.workforce.repository.HelperRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin dashboard summary (ManafyOps compatibility layer). Aggregates workforce
 * + assignment counts for the admin home screen.
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

    private final HelperRepository helperRepository;
    private final BookingAssignmentRepository assignmentRepository;

    public AdminDashboardController(HelperRepository helperRepository,
                                    BookingAssignmentRepository assignmentRepository) {
        this.helperRepository = helperRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> summary() {
        var maids = helperRepository.findByCategoryAndDeletedFalse("MAID");
        long activeMaids = maids.stream().filter(h -> "ACTIVE".equals(h.getStatus())).count();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalMaids", maids.size());
        m.put("activeMaids", activeMaids);
        m.put("inactiveMaids", maids.size() - activeMaids);
        m.put("availableMaids", maids.stream()
                .filter(h -> "ACTIVE".equals(h.getStatus()) && "AVAILABLE".equals(h.getAvailabilityStatus()))
                .count());
        // Category breakdown across the workforce.
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (String cat : List.of("MAID", "COOK", "PLUMBER", "ELECTRICIAN", "CLEANER", "CARPENTER")) {
            byCategory.put(cat, (long) helperRepository.findByCategoryAndDeletedFalse(cat).size());
        }
        m.put("workforceByCategory", byCategory);
        return ApiResponse.ok(m);
    }
}
