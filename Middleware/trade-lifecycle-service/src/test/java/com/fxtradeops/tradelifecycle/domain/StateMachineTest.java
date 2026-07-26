package com.fxtradeops.tradelifecycle.domain;

import com.fxtradeops.domain.event.TradeEventType;
import com.fxtradeops.domain.trade.TradeStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the lifecycle StateMachine — transition table and event-to-status mapping.
 */
class StateMachineTest {

    @Nested
    @DisplayName("Permitted forward transitions")
    class PermittedTransitions {

        @ParameterizedTest
        @CsvSource({
                "CAPTURED, VALIDATED",
                "VALIDATED, ENRICHED",
                "ENRICHED, RISK_CALCULATED",
                "RISK_CALCULATED, BOOKED",
                "BOOKED, ALLOCATED",
                "ALLOCATED, CONFIRMED",
                "CONFIRMED, SETTLED"
        })
        void happyPathTransitionsArePermitted(TradeStatus from, TradeStatus to) {
            assertThat(StateMachine.canTransition(from, to)).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
                "CAPTURED, CANCELLED",
                "VALIDATED, CANCELLED",
                "ENRICHED, CANCELLED",
                "RISK_CALCULATED, CANCELLED",
                "BOOKED, CANCELLED",
                "ALLOCATED, CANCELLED",
                "CONFIRMED, CANCELLED"
        })
        void cancellationFromNonTerminalIsPermitted(TradeStatus from, TradeStatus to) {
            assertThat(StateMachine.canTransition(from, to)).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
                "CAPTURED, AMENDED",
                "VALIDATED, AMENDED",
                "ENRICHED, AMENDED",
                "RISK_CALCULATED, AMENDED",
                "BOOKED, AMENDED"
        })
        void amendmentFromAmendableStatusIsPermitted(TradeStatus from, TradeStatus to) {
            assertThat(StateMachine.canTransition(from, to)).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
                "CAPTURED, FAILED",
                "VALIDATED, FAILED",
                "ENRICHED, FAILED",
                "RISK_CALCULATED, FAILED",
                "BOOKED, FAILED",
                "ALLOCATED, FAILED",
                "CONFIRMED, FAILED"
        })
        void failedFromNonTerminalIsPermitted(TradeStatus from, TradeStatus to) {
            assertThat(StateMachine.canTransition(from, to)).isTrue();
        }
    }

    @Nested
    @DisplayName("Illegal transitions")
    class IllegalTransitions {

        @ParameterizedTest
        @CsvSource({
                "SETTLED, CANCELLED",
                "SETTLED, VALIDATED",
                "CANCELLED, SETTLED",
                "CANCELLED, VALIDATED",
                "FAILED, SETTLED",
                "FAILED, CAPTURED",
                "VALIDATED, CAPTURED",
                "BOOKED, VALIDATED",
                "ALLOCATED, AMENDED",
                "CONFIRMED, AMENDED"
        })
        void illegalTransitionsAreRejected(TradeStatus from, TradeStatus to) {
            assertThat(StateMachine.canTransition(from, to)).isFalse();
        }

        @Test
        void terminalStatusesHaveNoForwardTransitions() {
            assertThat(StateMachine.PERMITTED.get(TradeStatus.SETTLED)).isEmpty();
            assertThat(StateMachine.PERMITTED.get(TradeStatus.CANCELLED)).isEmpty();
            assertThat(StateMachine.PERMITTED.get(TradeStatus.FAILED)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Event type to status mapping")
    class EventToStatusMapping {

        @ParameterizedTest
        @CsvSource({
                "TRADE_CAPTURED, CAPTURED",
                "TRADE_VALIDATED, VALIDATED",
                "TRADE_ENRICHED, ENRICHED",
                "RISK_CALCULATION_COMPLETED, RISK_CALCULATED",
                "TRADE_BOOKED, BOOKED",
                "TRADE_ALLOCATED, ALLOCATED",
                "TRADE_CONFIRMED, CONFIRMED",
                "TRADE_SETTLED, SETTLED",
                "TRADE_CANCELLED, CANCELLED",
                "TRADE_AMENDED, AMENDED",
                "TRADE_FAILED, FAILED"
        })
        void eventTypeMapsToCorrectStatus(TradeEventType eventType, TradeStatus expectedStatus) {
            Optional<TradeStatus> result = StateMachine.targetFor(eventType);
            assertThat(result).isPresent().contains(expectedStatus);
        }

        @Test
        void unmappedEventTypeReturnsEmpty() {
            Optional<TradeStatus> result = StateMachine.targetFor(TradeEventType.EVENT_REPLAYED);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Terminal status detection")
    class TerminalDetection {

        @Test
        void settledIsTerminal() {
            assertThat(StateMachine.isTerminal(TradeStatus.SETTLED)).isTrue();
        }

        @Test
        void cancelledIsTerminal() {
            assertThat(StateMachine.isTerminal(TradeStatus.CANCELLED)).isTrue();
        }

        @Test
        void failedIsTerminal() {
            assertThat(StateMachine.isTerminal(TradeStatus.FAILED)).isTrue();
        }

        @Test
        void capturedIsNotTerminal() {
            assertThat(StateMachine.isTerminal(TradeStatus.CAPTURED)).isFalse();
        }
    }
}
