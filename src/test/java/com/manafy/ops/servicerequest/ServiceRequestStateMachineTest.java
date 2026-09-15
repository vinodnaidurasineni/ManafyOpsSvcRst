package com.manafy.ops.servicerequest;

import com.manafy.ops.common.exception.BusinessException;
import com.manafy.ops.servicerequest.domain.ServiceRequestStateMachine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the Phase 4A service request state machine (no Spring context). */
class ServiceRequestStateMachineTest {

    @Test
    void newCanBeCancelled() {
        assertThat(ServiceRequestStateMachine.canTransition("NEW", "CANCELLED")).isTrue();
    }

    @Test
    void cancelledIsTerminal() {
        assertThat(ServiceRequestStateMachine.canTransition("CANCELLED", "NEW")).isFalse();
        assertThat(ServiceRequestStateMachine.canTransition("CANCELLED", "CANCELLED")).isFalse();
    }

    @Test
    void onlyValidProjectionTransitionsAllowed() {
        // Phase 4B added the assignment-driven projection: NEW → ASSIGNED is now valid,
        // but NEW cannot jump straight to mid/terminal work states.
        assertThat(ServiceRequestStateMachine.canTransition("NEW", "ASSIGNED")).isTrue();
        assertThat(ServiceRequestStateMachine.canTransition("NEW", "IN_PROGRESS")).isFalse();
        assertThat(ServiceRequestStateMachine.canTransition("NEW", "COMPLETED")).isFalse();
    }

    @Test
    void requireTransitionThrows409OnInvalid() {
        assertThatThrownBy(() -> ServiceRequestStateMachine.requireTransition("CANCELLED", "CANCELLED"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INVALID_STATE_TRANSITION");
    }

    @Test
    void requireTransitionPassesForValid() {
        ServiceRequestStateMachine.requireTransition("NEW", "CANCELLED"); // no throw
    }
}
