package com.manafy.ops.manualrequest;

import com.manafy.ops.manualrequest.repository.ManualAssignmentRequestRepository;
import com.manafy.ops.manualrequest.service.HelperAssignmentService;
import com.manafy.ops.workforce.entity.Helper;
import com.manafy.ops.workforce.repository.HelperRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the helper eligibility + daily-workload balancing engine — the
 * shared core of both automatic maid assignment and the manual candidate list.
 * Repositories are mocked so this runs without Spring/DB.
 */
class HelperAssignmentServiceTest {

    private final HelperRepository helperRepo = mock(HelperRepository.class);
    private final ManualAssignmentRequestRepository requestRepo = mock(ManualAssignmentRequestRepository.class);
    private final HelperAssignmentService svc = new HelperAssignmentService(helperRepo, requestRepo);

    private Helper helper(String name, String category, String status, String availability) {
        Helper h = new Helper();
        h.setId(UUID.randomUUID());
        h.setCode("H-" + name);
        h.setName(name);
        h.setCategory(category);
        h.setStatus(status);
        h.setAvailabilityStatus(availability);
        return h;
    }

    /** Stub today's workload for a specific helper id. */
    private void workload(Helper h, LocalDate day, long count) {
        when(requestRepo.countDailyWorkload(eq(h.getId().toString()), eq(day))).thenReturn(count);
    }

    @Test
    void ranksEligibleByLowestDailyWorkloadThenName() {
        LocalDate day = LocalDate.of(2026, 9, 19);
        Helper anita = helper("Anita", "MAID", "ACTIVE", "AVAILABLE");
        Helper priya = helper("Priya", "MAID", "ACTIVE", "AVAILABLE");
        Helper kavita = helper("Kavita", "MAID", "ACTIVE", "AVAILABLE");
        when(helperRepo.findByCategoryAndStatusAndAvailabilityStatusAndDeletedFalse("MAID", "ACTIVE", "AVAILABLE"))
                .thenReturn(List.of(priya, kavita, anita)); // deliberately unsorted
        workload(anita, day, 1);
        workload(priya, day, 3);
        workload(kavita, day, 5);

        List<HelperAssignmentService.RankedHelper> ranked = svc.rankedEligible("MAID", day);

        // Order must be Anita(1) < Priya(3) < Kavita(5).
        assertThat(ranked).extracting(rh -> rh.helper().getName())
                .containsExactly("Anita", "Priya", "Kavita");
        assertThat(ranked.get(0).todaysAssignments()).isEqualTo(1);
    }

    @Test
    void selectForAutoPicksLowestWorkloadAvailableHelper() {
        LocalDate day = LocalDate.of(2026, 9, 19);
        Helper anita = helper("Anita", "MAID", "ACTIVE", "AVAILABLE");
        Helper kavita = helper("Kavita", "MAID", "ACTIVE", "AVAILABLE");
        when(helperRepo.findByCategoryAndStatusAndAvailabilityStatusAndDeletedFalse("MAID", "ACTIVE", "AVAILABLE"))
                .thenReturn(List.of(kavita, anita));
        workload(anita, day, 2);
        workload(kavita, day, 4);

        HelperAssignmentService.RankedHelper best = svc.selectForAuto("MAID", day);

        assertThat(best).isNotNull();
        assertThat(best.helper().getName()).isEqualTo("Anita");
    }

    @Test
    void deterministicTiebreakByNameWhenWorkloadEqual() {
        LocalDate day = LocalDate.of(2026, 9, 19);
        Helper zara = helper("Zara", "COOK", "ACTIVE", "AVAILABLE");
        Helper deepa = helper("Deepa", "COOK", "ACTIVE", "AVAILABLE");
        when(helperRepo.findByCategoryAndStatusAndAvailabilityStatusAndDeletedFalse("COOK", "ACTIVE", "AVAILABLE"))
                .thenReturn(List.of(zara, deepa));
        workload(zara, day, 2);
        workload(deepa, day, 2); // tie → name order: Deepa before Zara

        HelperAssignmentService.RankedHelper best = svc.selectForAuto("COOK", day);
        assertThat(best.helper().getName()).isEqualTo("Deepa");
    }

    @Test
    void categoryFilterUsesTheRequestedCategoryPoolOnly() {
        LocalDate day = LocalDate.of(2026, 9, 19);
        Helper plumber = helper("Ravi", "PLUMBER", "ACTIVE", "AVAILABLE");
        when(helperRepo.findByCategoryAndStatusAndAvailabilityStatusAndDeletedFalse("PLUMBER", "ACTIVE", "AVAILABLE"))
                .thenReturn(List.of(plumber));
        workload(plumber, day, 0);

        // Case-insensitive category normalization.
        List<HelperAssignmentService.RankedHelper> ranked = svc.rankedEligible("plumber", day);
        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).helper().getCategory()).isEqualTo("PLUMBER");
    }

    @Test
    void noEligibleHelperReturnsNullForAutoAndEmptyForRanked() {
        LocalDate day = LocalDate.of(2026, 9, 19);
        when(helperRepo.findByCategoryAndStatusAndAvailabilityStatusAndDeletedFalse(any(), any(), any()))
                .thenReturn(List.of());

        assertThat(svc.selectForAuto("MAID", day)).isNull();
        assertThat(svc.rankedEligible("MAID", day)).isEmpty();
    }

    @Test
    void nullServiceTypeIsNotEligible() {
        assertThat(svc.rankedEligible(null, LocalDate.now())).isEmpty();
        assertThat(svc.selectForAuto(null, LocalDate.now())).isNull();
    }
}
