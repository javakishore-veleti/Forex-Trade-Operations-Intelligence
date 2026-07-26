package com.fxtradeops.tradelifecycle.api;

import com.fxtradeops.domain.trade.TradeStatus;
import com.fxtradeops.tradelifecycle.api.dto.ExpectedLifecycleView;
import com.fxtradeops.tradelifecycle.api.dto.ExpectedLifecycleView.LifecycleStepView;
import com.fxtradeops.tradelifecycle.api.dto.StateView;
import com.fxtradeops.tradelifecycle.api.dto.TimelineEntryView;
import com.fxtradeops.tradelifecycle.domain.StateMachine;
import com.fxtradeops.tradelifecycle.persistence.document.AuditEntryDocument;
import com.fxtradeops.tradelifecycle.persistence.document.AuditRepository;
import com.fxtradeops.tradelifecycle.persistence.relational.TradeCurrentStateEntity;
import com.fxtradeops.tradelifecycle.persistence.relational.TradeCurrentStateRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only REST API for trade lifecycle queries.
 */
@RestController
@RequestMapping("/api/v1/trades/{tradeId}")
public class LifecycleQueryController {

    private final TradeCurrentStateRepository stateRepository;
    private final AuditRepository auditRepository;

    public LifecycleQueryController(TradeCurrentStateRepository stateRepository,
                                     AuditRepository auditRepository) {
        this.stateRepository = stateRepository;
        this.auditRepository = auditRepository;
    }

    @GetMapping("/state")
    public ResponseEntity<StateView> getState(@PathVariable String tradeId) {
        Optional<TradeCurrentStateEntity> entity = stateRepository.findById(tradeId);
        if (entity.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        TradeCurrentStateEntity state = entity.get();
        return ResponseEntity.ok(new StateView(
                state.getTradeId(),
                state.getStatus().name(),
                state.getUpdatedAt(),
                state.getVersion()));
    }

    @GetMapping("/timeline")
    public ResponseEntity<List<TimelineEntryView>> getTimeline(@PathVariable String tradeId) {
        if (!stateRepository.existsById(tradeId) && !auditRepository.existsByTradeId(tradeId)) {
            return ResponseEntity.notFound().build();
        }
        List<AuditEntryDocument> entries =
                auditRepository.findByTradeIdOrderByOccurredAtAscRecordedAtAsc(tradeId);
        List<TimelineEntryView> timeline = entries.stream()
                .map(e -> new TimelineEntryView(
                        e.getEventId(),
                        e.getEventType(),
                        e.getFromStatus(),
                        e.getToStatus(),
                        e.isRejected(),
                        e.isNoop(),
                        e.isOrphan(),
                        e.getSourceService(),
                        e.getOccurredAt(),
                        e.getRecordedAt()))
                .toList();
        return ResponseEntity.ok(timeline);
    }

    @GetMapping("/expected-lifecycle")
    public ResponseEntity<ExpectedLifecycleView> getExpectedLifecycle(@PathVariable String tradeId) {
        Optional<TradeCurrentStateEntity> entityOpt = stateRepository.findById(tradeId);
        if (entityOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        TradeCurrentStateEntity state = entityOpt.get();

        // Determine which statuses have been reached from the timeline
        List<AuditEntryDocument> entries =
                auditRepository.findByTradeIdOrderByOccurredAtAscRecordedAtAsc(tradeId);
        Set<String> reachedStatuses = entries.stream()
                .filter(e -> !e.isRejected() && !e.isNoop() && !e.isOrphan())
                .map(AuditEntryDocument::getToStatus)
                .collect(Collectors.toSet());

        List<LifecycleStepView> steps = StateMachine.EXPECTED_LIFECYCLE.stream()
                .map(status -> new LifecycleStepView(
                        status.name(),
                        reachedStatuses.contains(status.name())))
                .toList();

        return ResponseEntity.ok(new ExpectedLifecycleView(tradeId, steps));
    }
}
