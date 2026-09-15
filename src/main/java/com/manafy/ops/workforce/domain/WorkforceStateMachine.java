package com.manafy.ops.workforce.domain;

import com.manafy.ops.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Set;

/**
 * Workforce lifecycle state machine (Phase 3 §4). Shared by technician, helper,
 * vendor and employee. Transitions are explicit actions (design DD-07); arbitrary
 * status updates are impossible. Illegal transitions → 409 INVALID_STATE_TRANSITION.
 *
 * DRAFT → ACTIVE → (SUSPENDED ↔ ACTIVE) → INACTIVE → ACTIVE
 *   any non-terminal → TERMINATED (terminal).
 */
public final class WorkforceStateMachine {

    private WorkforceStateMachine() {}

    public static final String DRAFT = "DRAFT";
    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String TERMINATED = "TERMINATED";

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            DRAFT,      Set.of(ACTIVE, TERMINATED),
            ACTIVE,     Set.of(SUSPENDED, INACTIVE, TERMINATED),
            SUSPENDED,  Set.of(ACTIVE, INACTIVE, TERMINATED),
            INACTIVE,   Set.of(ACTIVE, TERMINATED),
            TERMINATED, Set.of()   // terminal
    );

    public static boolean canTransition(String from, String to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransition(String from, String to) {
        if (!canTransition(from, to)) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Cannot transition workforce from " + from + " to " + to, HttpStatus.CONFLICT);
        }
    }

    /** True when a workforce member in this status may receive operational assignments (dispatch readiness §31). */
    public static boolean isOperational(String status) {
        return ACTIVE.equals(status);
    }
}
