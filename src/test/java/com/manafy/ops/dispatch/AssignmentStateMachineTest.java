package com.manafy.ops.dispatch;

import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.dispatch.domain.AssignmentStateMachine;
import com.manafy.ops.servicerequest.domain.ServiceRequestStateMachine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the Phase 4B assignment state machine + the SR projection graph. */
class AssignmentStateMachineTest {

    @Test
    void happyPathTransitions() {
        assertThat(AssignmentStateMachine.canTransition("ASSIGNED", "ACCEPTED")).isTrue();
        assertThat(AssignmentStateMachine.canTransition("ACCEPTED", "EN_ROUTE")).isTrue();
        assertThat(AssignmentStateMachine.canTransition("EN_ROUTE", "ARRIVED")).isTrue();
        assertThat(AssignmentStateMachine.canTransition("ARRIVED", "IN_PROGRESS")).isTrue();
        assertThat(AssignmentStateMachine.canTransition("IN_PROGRESS", "COMPLETED")).isTrue();
        assertThat(AssignmentStateMachine.canTransition("COMPLETED", "REWORK")).isTrue();
    }

    @Test
    void alternateTransitions() {
        assertThat(AssignmentStateMachine.canTransition("ASSIGNED", "DECLINED")).isTrue();
        assertThat(AssignmentStateMachine.canTransition("ASSIGNED", "CANCELLED")).isTrue();
        assertThat(AssignmentStateMachine.canTransition("ACCEPTED", "NO_SHOW")).isTrue();
        assertThat(AssignmentStateMachine.canTransition("ARRIVED", "NO_SHOW")).isTrue();
        assertThat(AssignmentStateMachine.canTransition("IN_PROGRESS", "CANCELLED")).isTrue();
    }

    @Test
    void illegalTransitionsRejected() {
        // Cannot skip acceptance to start.
        assertThat(AssignmentStateMachine.canTransition("ASSIGNED", "IN_PROGRESS")).isFalse();
        // Cannot complete before starting.
        assertThat(AssignmentStateMachine.canTransition("ACCEPTED", "COMPLETED")).isFalse();
        // Cannot go backwards.
        assertThat(AssignmentStateMachine.canTransition("IN_PROGRESS", "ACCEPTED")).isFalse();
        // Cannot resurrect a cancelled assignment.
        assertThat(AssignmentStateMachine.canTransition("CANCELLED", "ASSIGNED")).isFalse();
    }

    @Test
    void terminalStates() {
        assertThat(AssignmentStateMachine.isTerminal("DECLINED")).isTrue();
        assertThat(AssignmentStateMachine.isTerminal("CANCELLED")).isTrue();
        assertThat(AssignmentStateMachine.isTerminal("NO_SHOW")).isTrue();
        assertThat(AssignmentStateMachine.isTerminal("REWORK")).isTrue();
        assertThat(AssignmentStateMachine.isTerminal("ASSIGNED")).isFalse();
        assertThat(AssignmentStateMachine.isTerminal("IN_PROGRESS")).isFalse();
    }

    @Test
    void requireTransitionThrows409() {
        assertThatThrownBy(() -> AssignmentStateMachine.requireTransition("ASSIGNED", "COMPLETED"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INVALID_STATE_TRANSITION");
    }

    // ─── Service request projection graph (extended in Phase 4B) ─────

    @Test
    void serviceRequestProjectionTransitions() {
        assertThat(ServiceRequestStateMachine.canTransition("NEW", "ASSIGNED")).isTrue();
        assertThat(ServiceRequestStateMachine.canTransition("ASSIGNED", "IN_PROGRESS")).isTrue();
        assertThat(ServiceRequestStateMachine.canTransition("IN_PROGRESS", "COMPLETED")).isTrue();
        assertThat(ServiceRequestStateMachine.canTransition("COMPLETED", "REWORK")).isTrue();
        assertThat(ServiceRequestStateMachine.canTransition("REWORK", "ASSIGNED")).isTrue();
        assertThat(ServiceRequestStateMachine.canTransition("ASSIGNED", "NEW")).isTrue();
        // Phase 4A behaviour preserved.
        assertThat(ServiceRequestStateMachine.canTransition("NEW", "CANCELLED")).isTrue();
        // Completed cannot jump straight back to NEW.
        assertThat(ServiceRequestStateMachine.canTransition("COMPLETED", "NEW")).isFalse();
    }
}
