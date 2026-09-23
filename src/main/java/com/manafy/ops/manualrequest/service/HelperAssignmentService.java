package com.manafy.ops.manualrequest.service;

import com.manafy.ops.manualrequest.repository.ManualAssignmentRequestRepository;
import com.manafy.ops.workforce.entity.Helper;
import com.manafy.ops.workforce.repository.HelperRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Helper eligibility + daily-workload balancing for manual (Recurring Helper)
 * fulfillment. This is the single source of truth for BOTH automatic maid
 * assignment and the manually-ordered candidate list, so the two never diverge.
 *
 * Eligibility for a request's service category:
 *   1. helper.category matches the request serviceType (case-insensitive),
 *   2. helper.status == ACTIVE,
 *   3. helper.availabilityStatus == AVAILABLE.
 *
 * Ordering ("next free, least-loaded"):
 *   - primary:   ascending count of TODAY's assignments (derived from the
 *                manual_assignment_request rows, never a drifting counter),
 *   - secondary: deterministic tiebreak by name then id (never random).
 *
 * "Today" is the request's scheduled day (startDate) when known, else the current
 * date — so balancing is per calendar day and yesterday's load never sticks.
 */
@Service
public class HelperAssignmentService {

    private static final String ACTIVE = "ACTIVE";
    private static final String AVAILABLE = "AVAILABLE";

    private final HelperRepository helperRepo;
    private final ManualAssignmentRequestRepository requestRepo;

    public HelperAssignmentService(HelperRepository helperRepo,
                                   ManualAssignmentRequestRepository requestRepo) {
        this.helperRepo = helperRepo;
        this.requestRepo = requestRepo;
    }

    /** A helper paired with its computed workload for the relevant day. */
    public record RankedHelper(Helper helper, long todaysAssignments) {}

    private String norm(String s) {
        return s == null ? null : s.trim().toUpperCase(Locale.ROOT);
    }

    /** The day used for workload balancing: the request's start date, else today. */
    private LocalDate workloadDay(LocalDate startDate) {
        return startDate != null ? startDate : LocalDate.now();
    }

    /**
     * Eligible helpers for a service category, ranked by ascending daily workload
     * then a deterministic tiebreak. Empty if the category has no eligible helper.
     */
    public List<RankedHelper> rankedEligible(String serviceType, LocalDate startDate) {
        String category = norm(serviceType);
        if (category == null) return List.of();
        LocalDate day = workloadDay(startDate);

        List<Helper> pool = helperRepo
                .findByCategoryAndStatusAndAvailabilityStatusAndDeletedFalse(category, ACTIVE, AVAILABLE);

        return pool.stream()
                .map(h -> new RankedHelper(h, requestRepo.countDailyWorkload(h.getId().toString(), day)))
                .sorted(Comparator
                        .comparingLong(RankedHelper::todaysAssignments)
                        .thenComparing(rh -> safe(rh.helper().getName()))
                        .thenComparing(rh -> rh.helper().getId().toString()))
                .toList();
    }

    /**
     * The single best eligible helper for automatic assignment (lowest daily
     * workload, available, eligible), or null if none is available for the day.
     */
    public RankedHelper selectForAuto(String serviceType, LocalDate startDate) {
        List<RankedHelper> ranked = rankedEligible(serviceType, startDate);
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    private String safe(String s) { return s == null ? "" : s; }
}
