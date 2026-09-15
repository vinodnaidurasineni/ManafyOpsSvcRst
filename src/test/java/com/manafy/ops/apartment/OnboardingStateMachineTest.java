package com.manafy.ops.apartment;

import com.manafy.ops.apartment.domain.OnboardingStateMachine;
import com.manafy.ops.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the onboarding state machine (no Spring context). */
class OnboardingStateMachineTest {

    @Test
    void validTransitions() {
        assertThat(OnboardingStateMachine.canTransition("DRAFT", "SUBMITTED")).isTrue();
        assertThat(OnboardingStateMachine.canTransition("SUBMITTED", "VERIFIED")).isTrue();
        assertThat(OnboardingStateMachine.canTransition("SUBMITTED", "REJECTED")).isTrue();
        assertThat(OnboardingStateMachine.canTransition("REJECTED", "RESUBMITTED")).isTrue();
        assertThat(OnboardingStateMachine.canTransition("RESUBMITTED", "VERIFIED")).isTrue();
        assertThat(OnboardingStateMachine.canTransition("VERIFIED", "ACTIVE")).isTrue();
        assertThat(OnboardingStateMachine.canTransition("ACTIVE", "SUSPENDED")).isTrue();
        assertThat(OnboardingStateMachine.canTransition("SUSPENDED", "ACTIVE")).isTrue();
    }

    @Test
    void invalidTransitionsRejected() {
        assertThat(OnboardingStateMachine.canTransition("DRAFT", "VERIFIED")).isFalse();
        assertThat(OnboardingStateMachine.canTransition("DRAFT", "ACTIVE")).isFalse();
        assertThat(OnboardingStateMachine.canTransition("VERIFIED", "REJECTED")).isFalse();
        assertThat(OnboardingStateMachine.canTransition("ACTIVE", "DRAFT")).isFalse();
        assertThat(OnboardingStateMachine.canTransition("REJECTED", "VERIFIED")).isFalse();
    }

    @Test
    void requireTransitionThrows409OnInvalid() {
        assertThatThrownBy(() -> OnboardingStateMachine.requireTransition("DRAFT", "ACTIVE"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INVALID_STATE_TRANSITION");
    }
}
