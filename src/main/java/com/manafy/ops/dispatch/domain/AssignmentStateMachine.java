package com.manafy.ops.dispatch.domain;

import com.manafy.ops.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Set;

/**
 * Assignment lifecycle state machine (Phase 4B §5). Transitions are explicit actions;
 * arbitrary status updates are impossible. Illegal transitions raise 409
 * INVALID_STATE_TRANSITION (the project's existing error-code mechanism).
 *
 * Happy path:
 *   ASSIGNED → ACCEPTED → EN_ROUTE → ARRIVED → IN_PROGRESS → COMPLETED
 *
 * Alternates:
 *   ASSIGNED  → DECLINED | CANCELLED
 *   ACCEPTED  → EN_ROUTE | CANCELLED | NO_SHOW
 *   EN_ROUTE  → ARRIVED  | CANCELLED | NO_SHOW
 *   ARRIVED   → IN_PROGRESS | NO_SHOW | CANCELLED
 *   IN_PROGRESS → COMPLETED | CANCELLED
 *   COMPLETED → REWORK      (re-open completed work)
 *
 * Terminal: DECLINED, CANCELLED, NO_SHOW, REWORK. When an assignment leaves the happy
 * path (declined/cancelled/no-show) or is reworked, a NEW assignment row is created
 * for any continued work — the historical row is preserved, never rewritten.
 */
public final class AssignmentStateMachine {

    private AssignmentStateMachine() {}

    public static final String ASSIGNED = "ASSIGNED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String EN_ROUTE = "EN_ROUTE";
    public static final String ARRIVED = "ARRIVED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String DECLINED = "DECLINED";
    public static final String CANCELLED = "CANCELLED";
    public static final String NO_SHOW = "NO_SHOW";
    public static final String REWORK = "REWORK";

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            ASSIGNED,    Set.of(ACCEPTED, DECLINED, CANCELLED),
            ACCEPTED,    Set.of(EN_ROUTE, CANCELLED, NO_SHOW),
            EN_ROUTE,    Set.of(ARRIVED, CANCELLED, NO_SHOW),
            ARRIVED,     Set.of(IN_PROGRESS, NO_SHOW, CANCELLED),
            IN_PROGRESS, Set.of(COMPLETED, CANCELLED),
            COMPLETED,   Set.of(REWORK),
            DECLINED,    Set.of(),
            CANCELLED,   Set.of(),
            NO_SHOW,     Set.of(),
            REWORK,      Set.of()
    );

    public static boolean canTransition(String from, String to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransition(String from, String to) {
        if (!canTransition(from, to)) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Cannot transition assignment from " + from + " to " + to, HttpStatus.CONFLICT);
        }
    }

    /** True when the assignment is still open (not in a terminal state). */
    public static boolean isTerminal(String status) {
        return ALLOWED.getOrDefault(status, Set.of()).isEmpty();
    }
}
