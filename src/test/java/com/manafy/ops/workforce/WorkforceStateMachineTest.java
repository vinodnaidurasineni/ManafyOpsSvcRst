package com.manafy.ops.workforce;

import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.workforce.domain.WorkforceStateMachine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the workforce lifecycle state machine (no Spring context). */
class WorkforceStateMachineTest {

    @Test
    void validTransitions() {
        assertThat(WorkforceStateMachine.canTransition("DRAFT", "ACTIVE")).isTrue();
        assertThat(WorkforceStateMachine.canTransition("ACTIVE", "SUSPENDED")).isTrue();
        assertThat(WorkforceStateMachine.canTransition("SUSPENDED", "ACTIVE")).isTrue();
        assertThat(WorkforceStateMachine.canTransition("ACTIVE", "INACTIVE")).isTrue();
        assertThat(WorkforceStateMachine.canTransition("INACTIVE", "ACTIVE")).isTrue();
        assertThat(WorkforceStateMachine.canTransition("ACTIVE", "TERMINATED")).isTrue();
        assertThat(WorkforceStateMachine.canTransition("DRAFT", "TERMINATED")).isTrue();
        assertThat(WorkforceStateMachine.canTransition("SUSPENDED", "TERMINATED")).isTrue();
    }

    @Test
    void invalidTransitionsRejected() {
        assertThat(WorkforceStateMachine.canTransition("DRAFT", "SUSPENDED")).isFalse();
        assertThat(WorkforceStateMachine.canTransition("DRAFT", "INACTIVE")).isFalse();
        assertThat(WorkforceStateMachine.canTransition("TERMINATED", "ACTIVE")).isFalse();
        assertThat(WorkforceStateMachine.canTransition("TERMINATED", "DRAFT")).isFalse();
        assertThat(WorkforceStateMachine.canTransition("INACTIVE", "SUSPENDED")).isFalse();
    }

    @Test
    void terminatedIsTerminal() {
        assertThat(WorkforceStateMachine.canTransition("TERMINATED", "ACTIVE")).isFalse();
        assertThat(WorkforceStateMachine.canTransition("TERMINATED", "TERMINATED")).isFalse();
    }

    @Test
    void requireTransitionThrows409OnInvalid() {
        assertThatThrownBy(() -> WorkforceStateMachine.requireTransition("DRAFT", "SUSPENDED"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INVALID_STATE_TRANSITION");
    }

    @Test
    void onlyActiveIsOperational() {
        assertThat(WorkforceStateMachine.isOperational("ACTIVE")).isTrue();
        assertThat(WorkforceStateMachine.isOperational("DRAFT")).isFalse();
        assertThat(WorkforceStateMachine.isOperational("SUSPENDED")).isFalse();
        assertThat(WorkforceStateMachine.isOperational("INACTIVE")).isFalse();
        assertThat(WorkforceStateMachine.isOperational("TERMINATED")).isFalse();
    }
}
