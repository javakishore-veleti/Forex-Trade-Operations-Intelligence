package com.fxtradeops.reconciliation.domain.action;

import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.reconciliation.domain.canonical.DerivationResult;
import com.fxtradeops.reconciliation.domain.model.Divergence;
import com.fxtradeops.reconciliation.domain.model.DivergenceClassification;
import com.fxtradeops.reconciliation.domain.model.SourceId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PermittedActionPolicy:
 * - Representative divergences → expected actions
 * - Every emitted action ∈ catalogue
 * - No execution side effect
 */
class PermittedActionPolicyTest {

    private PermittedActionPolicy policy;
    private static final Set<PermittedAction> CATALOGUE = EnumSet.allOf(PermittedAction.class);

    @BeforeEach
    void setUp() {
        policy = new PermittedActionPolicy();
    }

    @Test
    @DisplayName("STALE cache → REFRESH_CACHE")
    void staleCachePermitsRefresh() {
        List<Divergence> divs = List.of(
                new Divergence(SourceId.CACHE, TradeStatus.CAPTURED, TradeStatus.BOOKED, DivergenceClassification.STALE)
        );

        Set<PermittedAction> actions = policy.permit(divs, DerivationResult.complete(TradeStatus.BOOKED));

        assertTrue(actions.contains(PermittedAction.REFRESH_CACHE));
        assertCatalogueOnly(actions);
    }

    @Test
    @DisplayName("STALE event stream → REPLAY_EVENT")
    void staleEventStreamPermitsReplay() {
        List<Divergence> divs = List.of(
                new Divergence(SourceId.EVENT_STREAM, TradeStatus.VALIDATED, TradeStatus.BOOKED, DivergenceClassification.STALE)
        );

        Set<PermittedAction> actions = policy.permit(divs, DerivationResult.complete(TradeStatus.BOOKED));

        assertTrue(actions.contains(PermittedAction.REPLAY_EVENT));
        assertCatalogueOnly(actions);
    }

    @Test
    @DisplayName("STALE document → RESYNC_DOCUMENT_STORE")
    void staleDocumentPermitsResync() {
        List<Divergence> divs = List.of(
                new Divergence(SourceId.DOCUMENT, TradeStatus.ENRICHED, TradeStatus.BOOKED, DivergenceClassification.STALE)
        );

        Set<PermittedAction> actions = policy.permit(divs, DerivationResult.complete(TradeStatus.BOOKED));

        assertTrue(actions.contains(PermittedAction.RESYNC_DOCUMENT_STORE));
        assertCatalogueOnly(actions);
    }

    @Test
    @DisplayName("STALE relational → RESYNC_RELATIONAL_STORE")
    void staleRelationalPermitsResync() {
        List<Divergence> divs = List.of(
                new Divergence(SourceId.RELATIONAL, TradeStatus.VALIDATED, TradeStatus.BOOKED, DivergenceClassification.STALE)
        );

        Set<PermittedAction> actions = policy.permit(divs, DerivationResult.complete(TradeStatus.BOOKED));

        assertTrue(actions.contains(PermittedAction.RESYNC_RELATIONAL_STORE));
        assertCatalogueOnly(actions);
    }

    @Test
    @DisplayName("CONFLICTING → OPEN_RECONCILIATION_CASE")
    void conflictingPermitsOpenCase() {
        List<Divergence> divs = List.of(
                new Divergence(SourceId.RELATIONAL, TradeStatus.CANCELLED, TradeStatus.BOOKED, DivergenceClassification.CONFLICTING)
        );

        Set<PermittedAction> actions = policy.permit(divs, DerivationResult.complete(TradeStatus.BOOKED));

        assertTrue(actions.contains(PermittedAction.OPEN_RECONCILIATION_CASE));
        assertCatalogueOnly(actions);
    }

    @Test
    @DisplayName("No divergences → NO_ACTION")
    void noDivergencesPermitsNoAction() {
        Set<PermittedAction> actions = policy.permit(List.of(), DerivationResult.complete(TradeStatus.BOOKED));

        assertEquals(Set.of(PermittedAction.NO_ACTION), actions);
        assertCatalogueOnly(actions);
    }

    @Test
    @DisplayName("Every emitted action must be in the fixed catalogue")
    void allActionsInCatalogue() {
        List<Divergence> divs = List.of(
                new Divergence(SourceId.CACHE, TradeStatus.CAPTURED, TradeStatus.BOOKED, DivergenceClassification.STALE),
                new Divergence(SourceId.RELATIONAL, TradeStatus.CANCELLED, TradeStatus.BOOKED, DivergenceClassification.CONFLICTING),
                new Divergence(SourceId.DOCUMENT, TradeStatus.VALIDATED, TradeStatus.BOOKED, DivergenceClassification.STALE)
        );

        Set<PermittedAction> actions = policy.permit(divs, DerivationResult.complete(TradeStatus.BOOKED));

        assertCatalogueOnly(actions);
    }

    private void assertCatalogueOnly(Set<PermittedAction> actions) {
        for (PermittedAction action : actions) {
            assertTrue(CATALOGUE.contains(action),
                    "Action " + action + " not in catalogue");
        }
    }
}
