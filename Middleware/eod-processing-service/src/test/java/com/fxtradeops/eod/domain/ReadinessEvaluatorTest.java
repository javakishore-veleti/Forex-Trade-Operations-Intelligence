package com.fxtradeops.eod.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ReadinessEvaluator — pure function, no I/O.
 * Uses FX- prefixed synthetic IDs and fictional region names.
 */
class ReadinessEvaluatorTest {

    @Test
    @DisplayName("All inputs satisfied → READY")
    void allSatisfied_yieldsReady() {
        ReadinessInputs inputs = new ReadinessInputs(
                "APAC", true, "", 0, 0, false, true, List.of()
        );

        ReadinessResult result = ReadinessEvaluator.evaluate(inputs);

        assertThat(result.isReady()).isTrue();
        assertThat(result.status()).isEqualTo(RegionalCloseStatus.READY);
        assertThat(result.unmet()).isEmpty();
    }

    @Test
    @DisplayName("Incomplete branches → BLOCKED with INCOMPLETE_BRANCH")
    void incompleteBranches_yieldsBlocked() {
        ReadinessInputs inputs = new ReadinessInputs(
                "EMEA", false, "FX-BR-001,FX-BR-002", 0, 0, false, true, List.of()
        );

        ReadinessResult result = ReadinessEvaluator.evaluate(inputs);

        assertThat(result.isReady()).isFalse();
        assertThat(result.status()).isEqualTo(RegionalCloseStatus.BLOCKED);
        assertThat(result.unmet()).anyMatch(b -> b.type() == BlockerType.INCOMPLETE_BRANCH);
    }

    @Test
    @DisplayName("Unprocessed trades above tolerance → BLOCKED")
    void unprocessedTrades_aboveTolerance_yieldsBlocked() {
        ReadinessInputs inputs = new ReadinessInputs(
                "AMERICAS", true, "", 5, 2, false, true, List.of()
        );

        ReadinessResult result = ReadinessEvaluator.evaluate(inputs);

        assertThat(result.isReady()).isFalse();
        assertThat(result.unmet()).anyMatch(b -> b.type() == BlockerType.UNPROCESSED_TRADES);
    }

    @Test
    @DisplayName("Unprocessed trades above tolerance with approved exception → READY (not blocked by trades)")
    void unprocessedTrades_withApprovedException_yieldsReady() {
        ReadinessInputs inputs = new ReadinessInputs(
                "APAC", true, "", 5, 2, true, true, List.of()
        );

        ReadinessResult result = ReadinessEvaluator.evaluate(inputs);

        assertThat(result.isReady()).isTrue();
        assertThat(result.unmet()).isEmpty();
    }

    @Test
    @DisplayName("Missing risk snapshot → BLOCKED")
    void missingRiskSnapshot_yieldsBlocked() {
        ReadinessInputs inputs = new ReadinessInputs(
                "EMEA", true, "", 0, 0, false, false, List.of()
        );

        ReadinessResult result = ReadinessEvaluator.evaluate(inputs);

        assertThat(result.isReady()).isFalse();
        assertThat(result.unmet()).anyMatch(b -> b.type() == BlockerType.MISSING_RISK_SNAPSHOT);
    }

    @Test
    @DisplayName("Open blockers → BLOCKED")
    void openBlockers_yieldsBlocked() {
        List<Blocker> openBlockers = List.of(Blocker.of(BlockerType.LATE_TRADE, "FX-000123"));
        ReadinessInputs inputs = new ReadinessInputs(
                "AMERICAS", true, "", 0, 0, false, true, openBlockers
        );

        ReadinessResult result = ReadinessEvaluator.evaluate(inputs);

        assertThat(result.isReady()).isFalse();
        assertThat(result.unmet()).anyMatch(b -> b.type() == BlockerType.LATE_TRADE);
    }

    @Test
    @DisplayName("Multiple unmet conditions → all reported")
    void multipleUnmet_allReported() {
        List<Blocker> openBlockers = List.of(Blocker.of(BlockerType.LATE_TRADE, "FX-000456"));
        ReadinessInputs inputs = new ReadinessInputs(
                "APAC", false, "FX-BR-003", 10, 0, false, false, openBlockers
        );

        ReadinessResult result = ReadinessEvaluator.evaluate(inputs);

        assertThat(result.isReady()).isFalse();
        assertThat(result.unmet()).hasSize(4);
        assertThat(result.unmet().stream().map(Blocker::type)).containsExactlyInAnyOrder(
                BlockerType.INCOMPLETE_BRANCH,
                BlockerType.UNPROCESSED_TRADES,
                BlockerType.MISSING_RISK_SNAPSHOT,
                BlockerType.LATE_TRADE
        );
    }

    @Test
    @DisplayName("Determinism: same inputs → same result")
    void determinism_sameInputsSameResult() {
        ReadinessInputs inputs = new ReadinessInputs(
                "EMEA", true, "", 0, 0, false, true, List.of()
        );

        ReadinessResult result1 = ReadinessEvaluator.evaluate(inputs);
        ReadinessResult result2 = ReadinessEvaluator.evaluate(inputs);

        assertThat(result1).isEqualTo(result2);
    }

    @Test
    @DisplayName("Unprocessed trades at exactly tolerance → READY")
    void unprocessedTrades_atTolerance_yieldsReady() {
        ReadinessInputs inputs = new ReadinessInputs(
                "APAC", true, "", 2, 2, false, true, List.of()
        );

        ReadinessResult result = ReadinessEvaluator.evaluate(inputs);

        assertThat(result.isReady()).isTrue();
    }
}
