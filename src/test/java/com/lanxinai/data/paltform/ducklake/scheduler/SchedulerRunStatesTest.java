package com.lanxinai.data.paltform.ducklake.scheduler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchedulerRunStatesTest {

    @Test
    void normalizesAndClassifiesTerminalStates() {
        assertThat(SchedulerRunStates.normalize(" success ")).isEqualTo("SUCCESS");
        assertThat(SchedulerRunStates.isTerminal("failure")).isTrue();
        assertThat(SchedulerRunStates.isTerminal("READY_STOP")).isFalse();
        assertThat(SchedulerRunStates.isTerminal("RUNNING_EXECUTION")).isFalse();
    }

    @Test
    void rejectsUnboundedStateValues() {
        assertThatThrownBy(() -> SchedulerRunStates.normalize("x".repeat(51)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
