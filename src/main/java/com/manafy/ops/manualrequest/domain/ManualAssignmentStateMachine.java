package com.manafy.ops.manualrequest.domain;

import com.manafy.ops.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Set;

/**
 * Manual assignment request lifecycle — driven by a Field Officer's manual
 * decisions, NOT by technician assignment events (no ACCEPTED/EN_ROUTE/ARRIVED).
 *
 *   OPEN → QUEUED            (intake accepted; on the FO area queue)
 *   QUEUED → ACKED_BY_FO     (an FO picks it up)
 *   QUEUED|ACKED_BY_FO → ASSIGNED   (FO records the chosen assignee)
 *   ASSIGNED → IN_PROGRESS   (work started)
 *   IN_PROGRESS → COMPLETED  (done; terminal)
 *   ASSIGNED → ASSIGNED      (reassignment — same state, new assignee, history kept)
 *   any non-terminal → CANCELLED (terminal)
 *
 * Illegal transitions raise 409 INVALID_STATE_TRANSITION.
 */
public final class ManualAssignmentStateMachine {

    private ManualAssignmentStateMachine() {}

    public static final String OPEN = "OPEN";
    public static final String QUEUED = "QUEUED";
    public static final String ACKED_BY_FO = "ACKED_BY_FO";
    public static final String ASSIGNED = "ASSIGNED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            OPEN,        Set.of(QUEUED, ASSIGNED, CANCELLED),
            QUEUED,      Set.of(ACKED_BY_FO, ASSIGNED, CANCELLED),
            ACKED_BY_FO, Set.of(ASSIGNED, CANCELLED),
            ASSIGNED,    Set.of(ASSIGNED, IN_PROGRESS, CANCELLED),
            IN_PROGRESS, Set.of(COMPLETED, CANCELLED),
            COMPLETED,   Set.of(),   // terminal
            CANCELLED,   Set.of()    // terminal
    );

    public static boolean canTransition(String from, String to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransition(String from, String to) {
        if (!canTransition(from, to)) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Cannot transition manual request from " + from + " to " + to, HttpStatus.CONFLICT);
        }
    }

    public static boolean isTerminal(String status) {
        return COMPLETED.equals(status) || CANCELLED.equals(status);
    }
}
