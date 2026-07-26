import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CounterpartyClient } from '../../core/api/clients';
import { AppConfigService } from '../../core/config/app-config.service';
import { CounterpartyExposure, SettlementTrade } from '../../core/models';
import { AppError } from '../../core/http/interceptors';

@Component({
  selector: 'app-counterparty-exposure',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './counterparty-exposure.component.html',
  styleUrl: './counterparty-exposure.component.css'
})
export class CounterpartyExposureComponent implements OnInit {
  counterparties: CounterpartyExposure[] = [];
  loading = true;
  error: string | null = null;
  expandedCounterparty: string | null = null;
  expandedTrades: SettlementTrade[] = [];
  expandLoading = false;
  lastUpdated: string | null = null;

  constructor(
    private readonly counterpartyClient: CounterpartyClient,
    readonly config: AppConfigService
  ) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    this.error = null;

    this.counterpartyClient.getCounterpartyExposures().subscribe({
      next: (counterparties) => {
        this.counterparties = counterparties;
        this.loading = false;
        this.lastUpdated = counterparties.length > 0
          ? counterparties[0].lastCalculatedAt
          : null;
      },
      error: (err: AppError) => {
        this.error = err.message;
        this.loading = false;
      }
    });
  }

  isWarning(cp: CounterpartyExposure): boolean {
    return cp.utilizationPercent >= this.config.counterpartyWarningPercent
      && cp.utilizationPercent < this.config.counterpartyBreachPercent;
  }

  isBreach(cp: CounterpartyExposure): boolean {
    return cp.utilizationPercent >= this.config.counterpartyBreachPercent;
  }

  toggleExpand(counterpartyId: string): void {
    if (this.expandedCounterparty === counterpartyId) {
      this.expandedCounterparty = null;
      this.expandedTrades = [];
      return;
    }

    this.expandedCounterparty = counterpartyId;
    this.expandLoading = true;

    this.counterpartyClient.getCounterpartyTrades(counterpartyId).subscribe({
      next: (trades) => {
        this.expandedTrades = trades;
        this.expandLoading = false;
      },
      error: () => {
        this.expandedTrades = [];
        this.expandLoading = false;
      }
    });
  }

  retry(): void {
    this.loadData();
  }
}
