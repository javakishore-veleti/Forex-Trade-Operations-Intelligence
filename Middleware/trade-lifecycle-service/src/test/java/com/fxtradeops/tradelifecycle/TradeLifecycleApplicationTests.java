package com.fxtradeops.tradelifecycle;

import com.fxtradeops.tradelifecycle.domain.StateMachine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lightweight test verifying that the domain model is correctly configured.
 * Full context-load test is in the integration test suite (TradeLifecycleIntegrationIT).
 */
class TradeLifecycleApplicationTests {

    @Test
    void stateMachineIsInitialized() {
        assertThat(StateMachine.PERMITTED).isNotEmpty();
        assertThat(StateMachine.EVENT_TO_STATUS).isNotEmpty();
        assertThat(StateMachine.EXPECTED_LIFECYCLE).hasSize(8);
    }
}
