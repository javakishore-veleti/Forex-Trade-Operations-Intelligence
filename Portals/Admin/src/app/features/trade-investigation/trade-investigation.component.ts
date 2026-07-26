import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { TradeLifecycleClient } from '../../core/api/trade-lifecycle.client';
import { RiskClient } from '../../core/api/risk.client';
import { StateReconciliationClient } from '../../core/api/state-reconciliation.client';
import {
  TradeDetail,
  LifecycleEvent,
  RiskResult,
  SequenceViolation,
  DlqEntry,
  ReconciliationResult
} from '../../core/models';
import { AppError } from '../../core/http/error.interceptor';

@Component({
  selector: 'app-trade-investigation',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './trade-investigation.component.html',
  styleUrl: './trade-investigation.component.css'
})
export class TradeInvestigationComponent implements OnInit {
  trade: TradeDetail | null = null;
  lifecycleEvents: LifecycleEvent[] = [];
  riskResult: RiskResult | null = null;
  sequenceViolations: SequenceViolation[] = [];
  dlqEntries: DlqEntry[] = [];
  reconciliation: ReconciliationResult | null = null;

  loading = true;
  error: string | null = null;
  actionConfirm: string | null = null;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly tradeClient: TradeLifecycleClient,
    private readonly riskClient: RiskClient,
    private readonly reconciliationClient: StateReconciliationClient
  ) {}

  ngOnInit(): void {
    const tradeId = this.route.snapshot.paramMap.get('tradeId');
    if (tradeId) {
      this.loadTradeData(tradeId);
    }
  }

  loadTradeData(tradeId: string): void {
    this.loading = true;
    this.error = null;

    forkJoin({
      trade: this.tradeClient.getTradeDetail(tradeId),
      lifecycle: this.tradeClient.getLifecycleEvents(tradeId),
      risk: this.riskClient.getRiskResult(tradeId).pipe(catchError(() => of(null))),
      violations: this.reconciliationClient.getSequenceViolations(tradeId).pipe(catchError(() => of([]))),
      dlq: this.reconciliationClient.getDlqEntries(tradeId).pipe(catchError(() => of([]))),
      reconciliation: this.reconciliationClient.getReconciliation(tradeId).pipe(catchError(() => of(null)))
    }).subscribe({
      next: (result) => {
        this.trade = result.trade;
        this.lifecycleEvents = result.lifecycle;
        this.riskResult = result.risk;
        this.sequenceViolations = result.violations as SequenceViolation[];
        this.dlqEntries = result.dlq as DlqEntry[];
        this.reconciliation = result.reconciliation;
        this.loading = false;
      },
      error: (err: AppError) => {
        this.error = err.message;
        this.loading = false;
      }
    });
  }

  isActionPermitted(action: string): boolean {
    return this.reconciliation?.permittedActions?.includes(action) ?? false;
  }

  confirmAction(action: string): void {
    this.actionConfirm = action;
  }

  cancelAction(): void {
    this.actionConfirm = null;
  }

  executeAction(action: string): void {
    if (!this.trade) return;
    this.reconciliationClient.executeAction(this.trade.tradeId, action).subscribe({
      next: () => {
        this.actionConfirm = null;
        this.loadTradeData(this.trade!.tradeId);
      },
      error: (err: AppError) => {
        this.error = err.message;
        this.actionConfirm = null;
      }
    });
  }

  retry(): void {
    if (this.trade) {
      this.loadTradeData(this.trade.tradeId);
    }
  }
}
