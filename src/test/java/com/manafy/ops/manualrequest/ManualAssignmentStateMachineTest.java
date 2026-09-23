package com.manafy.ops.manualrequest;

import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.manualrequest.domain.ManualAssignmentStateMachine;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static com.manafy.ops.manualrequest.domain.ManualAssignmentStateMachine.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lifecycle guarantees for the manual (Recurring Helper) request. These are the
 * core invariant of the Ops side: a Field Officer coordinates a person manually,
 * and technician-dispatch states (ACCEPTED/EN_ROUTE/ARRIVED) must NOT exist here.
 */
class ManualAssignmentStateMachineTest {

    @Test
    void intakeQueueAndAssignPathIsAllowed() {
        assertThat(canTransition(OPEN, QUEUED)).isTrue();
        assertThat(canTransition(QUEUED, ACKED_BY_FO)).isTrue();
        assertThat(canTransition(QUEUED, ASSIGNED)).isTrue();
        assertThat(canTransition(ACKED_BY_FO, ASSIGNED)).isTrue();
        assertThat(canTransition(ASSIGNED, IN_PROGRESS)).isTrue();
        assertThat(canTransition(IN_PROGRESS, COMPLETED)).isTrue();
    }

    @Test
    void reassignmentKeepsSameStateAssigned() {
        // Reassignment is a self-transition: still ASSIGNED, new assignee.
        assertThat(canTransition(ASSIGNED, ASSIGNED)).isTrue();
    }

    @Test
    void anyNonTerminalCanBeCancelled() {
        assertThat(canTransition(OPEN, CANCELLED)).isTrue();
        assertThat(canTransition(QUEUED, CANCELLED)).isTrue();
        assertThat(canTransition(ACKED_BY_FO, CANCELLED)).isTrue();
        assertThat(canTransition(ASSIGNED, CANCELLED)).isTrue();
        assertThat(canTransition(IN_PROGRESS, CANCELLED)).isTrue();
    }

    @Test
    void terminalStatesHaveNoOutgoingTransitions() {
        assertThat(isTerminal(COMPLETED)).isTrue();
        assertThat(isTerminal(CANCELLED)).isTrue();
        assertThat(canTransition(COMPLETED, IN_PROGRESS)).isFalse();
        assertThat(canTransition(COMPLETED, CANCELLED)).isFalse();
        assertThat(canTransition(CANCELLED, ASSIGNED)).isFalse();
    }

    @Test
    void cannotSkipStraightToCompleted() {
        assertThat(canTransition(QUEUED, COMPLETED)).isFalse();
        assertThat(canTransition(ASSIGNED, COMPLETED)).isFalse();
        assertThat(canTransition(OPEN, IN_PROGRESS)).isFalse();
    }

    @Test
    void technicianDispatchStatesAreNotPartOfThisLifecycle() {
        // These belong to the technician workflow and must be rejected here.
        assertThat(canTransition(ASSIGNED, "ACCEPTED")).isFalse();
        assertThat(canTransition(ASSIGNED, "EN_ROUTE")).isFalse();
        assertThat(canTransition(ASSIGNED, "ARRIVED")).isFalse();
    }

    @Test
    void requireTransitionThrows409OnIllegalMove() {
        assertThatThrownBy(() -> requireTransition(COMPLETED, IN_PROGRESS))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo("INVALID_STATE_TRANSITION");
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    void requireTransitionPassesOnLegalMove() {
        // Should not throw.
        requireTransition(QUEUED, ASSIGNED);
        requireTransition(ASSIGNED, IN_PROGRESS);
    }
}
