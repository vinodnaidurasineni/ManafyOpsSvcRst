package com.manafy.ops.apartment.domain;

import com.manafy.ops.common.exception.BusinessException;

import java.util.Map;
import java.util.Set;

/**
 * Onboarding lifecycle state machine (Phase 2 §5). Validates transitions so
 * arbitrary status updates are impossible (design DD-07); only the defined
 * transitions are allowed. Illegal transitions throw 409 INVALID_STATE_TRANSITION.
 *
 * DRAFT → SUBMITTED → UNDER_REVIEW → (REJECTED → RESUBMITTED → UNDER_REVIEW …)
 *        → VERIFIED → ACTIVE ; ACTIVE ↔ SUSPENDED.
 *
 * Actions map to transitions:
 *   submit:   DRAFT|REJECTED|RESUBMITTED → SUBMITTED
 *   verify(begin review): SUBMITTED|RESUBMITTED → UNDER_REVIEW  (implicit on verify call start)
 *   verify(pass): SUBMITTED|UNDER_REVIEW|RESUBMITTED → VERIFIED
 *   reject:   SUBMITTED|UNDER_REVIEW|RESUBMITTED → REJECTED
 *   resubmit: REJECTED → RESUBMITTED
 *   activate: VERIFIED → ACTIVE
 *   suspend:  ACTIVE → SUSPENDED
 */
public final class OnboardingStateMachine {

    private OnboardingStateMachine() {}

    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String UNDER_REVIEW = "UNDER_REVIEW";
    public static final String REJECTED = "REJECTED";
    public static final String RESUBMITTED = "RESUBMITTED";
    public static final String VERIFIED = "VERIFIED";
    public static final String ACTIVE = "ACTIVE";
    public static final String SUSPENDED = "SUSPENDED";

    /** Allowed target states per source state. */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            DRAFT,       Set.of(SUBMITTED),
            SUBMITTED,   Set.of(UNDER_REVIEW, VERIFIED, REJECTED),
            UNDER_REVIEW,Set.of(VERIFIED, REJECTED),
            REJECTED,    Set.of(RESUBMITTED),
            RESUBMITTED, Set.of(UNDER_REVIEW, VERIFIED, REJECTED, SUBMITTED),
            VERIFIED,    Set.of(ACTIVE),
            ACTIVE,      Set.of(SUSPENDED),
            SUSPENDED,   Set.of(ACTIVE)
    );

    public static boolean canTransition(String from, String to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** @throws BusinessException 409 INVALID_STATE_TRANSITION if not allowed. */
    public static void requireTransition(String from, String to) {
        if (!canTransition(from, to)) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Cannot transition onboarding from " + from + " to " + to,
                    org.springframework.http.HttpStatus.CONFLICT);
        }
    }
}
