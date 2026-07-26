import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { TradeLifecycleClient } from '../../core/api/clients';
import { TradeDetail, LifecycleEvent, TradeStatus } from '../../core/models';
import { AppError } from '../../core/http/interceptors';

const LIFECYCLE_STAGES: TradeStatus[] = [
  'TRADE_RECEIVED',
  'TRADE_VALIDATED',
  'TRADE_ENRICHED',
  'RISK_CALCULATED',
  'TRADE_CONFIRMED',
  'TRADE_SETTLED'
];

@Component({
  selector: 'app-trade-status',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './trade-status.component.html',
  styleUrl: './trade-status.component.css'
})
export class TradeStatusComponent implements OnInit {
  trade: TradeDetail | null = null;
  lifecycleEvents: LifecycleEvent[] = [];
  loading = true;
  error: string | null = null;

  readonly stages = LIFECYCLE_STAGES;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly tradeClient: TradeLifecycleClient
  ) {}

  ngOnInit(): void {
    const tradeId = this.route.snapshot.paramMap.get('tradeId');
    if (tradeId) {
      this.loadTrade(tradeId);
    }
  }

  loadTrade(tradeId: string): void {
    this.loading = true;
    this.error = null;

    forkJoin({
      trade: this.tradeClient.getTradeDetail(tradeId),
      lifecycle: this.tradeClient.getLifecycleEvents(tradeId)
    }).subscribe({
      next: (result) => {
        this.trade = result.trade;
        this.lifecycleEvents = result.lifecycle;
        this.loading = false;
      },
      error: (err: AppError) => {
        this.error = err.message;
        this.loading = false;
      }
    });
  }

  getStageState(stage: TradeStatus): 'complete' | 'current' | 'pending' {
    if (!this.trade) return 'pending';
    const currentIndex = this.stages.indexOf(this.trade.status);
    const stageIndex = this.stages.indexOf(stage);

    if (this.trade.status === 'TRADE_FAILED' || this.trade.status === 'TRADE_CANCELLED') {
      // For failed/cancelled, mark everything up to last known step as complete
      const lastEvent = this.lifecycleEvents[this.lifecycleEvents.length - 1];
      const lastIndex = lastEvent ? this.stages.indexOf(lastEvent.status) : -1;
      if (stageIndex < lastIndex) return 'complete';
      if (stageIndex === lastIndex) return 'current';
      return 'pending';
    }

    if (stageIndex < currentIndex) return 'complete';
    if (stageIndex === currentIndex) return 'current';
    return 'pending';
  }

  isFailed(): boolean {
    return this.trade?.status === 'TRADE_FAILED';
  }

  hasRiskResult(): boolean {
    return this.lifecycleEvents.some(e => e.status === 'RISK_CALCULATED');
  }

  retry(): void {
    if (this.trade) {
      this.loadTrade(this.trade.tradeId);
    }
  }
}
