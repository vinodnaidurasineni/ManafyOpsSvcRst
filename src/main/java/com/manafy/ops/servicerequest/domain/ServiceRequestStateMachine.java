package com.manafy.ops.servicerequest.domain;

import com.manafy.ops.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Set;

/**
 * Service Request lifecycle state machine.
 *
 * Phase 4A introduced NEW → CANCELLED. Phase 4B (Operations & Dispatch, DD-36) adds
 * the projection states driven by assignment activity — the request status is a
 * projection of the active assignment, never set arbitrarily:
 *
 *   NEW → ASSIGNED         (an assignment is created)
 *   ASSIGNED → NEW         (assignment declined/cancelled/no-show with no replacement → back to dispatchable)
 *   ASSIGNED → IN_PROGRESS (technician starts work)
 *   IN_PROGRESS → ASSIGNED (assignment cancelled/no-show mid-work → back to dispatchable)
 *   IN_PROGRESS → COMPLETED
 *   COMPLETED → REWORK     (completed work re-opened)
 *   REWORK → ASSIGNED      (a new assignment is created for the rework)
 *   NEW | ASSIGNED → CANCELLED (request cancelled)
 *
 * Illegal transitions raise 409 INVALID_STATE_TRANSITION; arbitrary status updates
 * are impossible.
 */
public final class ServiceRequestStateMachine {

    private ServiceRequestStateMachine() {}

    public static final String NEW = "NEW";
    public static final String ASSIGNED = "ASSIGNED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String REWORK = "REWORK";
    public static final String CANCELLED = "CANCELLED";

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            NEW,         Set.of(ASSIGNED, CANCELLED),
            ASSIGNED,    Set.of(NEW, IN_PROGRESS, CANCELLED),
            IN_PROGRESS, Set.of(ASSIGNED, COMPLETED),
            COMPLETED,   Set.of(REWORK),
            REWORK,      Set.of(ASSIGNED),
            CANCELLED,   Set.of()   // terminal
    );

    public static boolean canTransition(String from, String to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransition(String from, String to) {
        if (!canTransition(from, to)) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Cannot transition service request from " + from + " to " + to, HttpStatus.CONFLICT);
        }
    }
}
