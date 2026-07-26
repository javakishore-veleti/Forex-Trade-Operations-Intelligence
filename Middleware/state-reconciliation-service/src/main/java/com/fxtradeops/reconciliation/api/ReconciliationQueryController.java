package com.fxtradeops.reconciliation.api;

import com.fxtradeops.reconciliation.api.dto.ReconciliationResultView;
import com.fxtradeops.reconciliation.api.dto.SweepRequest;
import com.fxtradeops.reconciliation.application.ReconciliationService;
import com.fxtradeops.reconciliation.application.SweepService;
import com.fxtradeops.reconciliation.domain.model.ReconciliationResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read-only query endpoints for trade reconciliation.
 * Side-effect free — no source is modified.
 */
@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationQueryController {

    private final ReconciliationService reconciliationService;
    private final SweepService sweepService;

    public ReconciliationQueryController(ReconciliationService reconciliationService,
                                         SweepService sweepService) {
        this.reconciliationService = reconciliationService;
        this.sweepService = sweepService;
    }

    /**
     * GET /api/v1/reconciliation/{tradeId}
     * Returns ReconciliationResultView for one trade.
     * 404 when trade is unknown to every source.
     */
    @GetMapping("/{tradeId}")
    public ResponseEntity<ReconciliationResultView> reconcile(@PathVariable String tradeId) {
        ReconciliationResult result = reconciliationService.reconcile(tradeId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ReconciliationResultView.from(result));
    }

    /**
     * POST /api/v1/reconciliation/sweep
     * Batch reconciliation — returns one result per known trade.
     * Read-only, side-effect free.
     */
    @PostMapping("/sweep")
    public ResponseEntity<List<ReconciliationResultView>> sweep(@Valid @RequestBody SweepRequest request) {
        List<ReconciliationResult> results = sweepService.sweep(request.tradeIds());
        List<ReconciliationResultView> views = results.stream()
                .map(ReconciliationResultView::from)
                .toList();
        return ResponseEntity.ok(views);
    }
}
