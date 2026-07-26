import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { SettlementClient } from '../../core/api/clients';
import { AppConfigService } from '../../core/config/app-config.service';
import { SettlementTrade, PagedResult } from '../../core/models';
import { AppError } from '../../core/http/interceptors';

@Component({
  selector: 'app-settlement-status',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './settlement-status.component.html',
  styleUrl: './settlement-status.component.css'
})
export class SettlementStatusComponent implements OnInit {
  trades: SettlementTrade[] = [];
  totalElements = 0;
  totalPages = 0;
  currentPage = 0;
  pageSize: number;

  filterValueDate = '';
  filterStatus = '';
  filterCurrencyPair = '';

  loading = true;
  error: string | null = null;

  constructor(
    private readonly settlementClient: SettlementClient,
    private readonly config: AppConfigService,
    private readonly route: ActivatedRoute
  ) {
    this.pageSize = this.config.defaultPageSize;
  }

  ngOnInit(): void {
    const pairParam = this.route.snapshot.queryParamMap.get('currencyPair');
    if (pairParam) {
      this.filterCurrencyPair = pairParam;
    }
    this.loadTrades();
  }

  loadTrades(): void {
    this.loading = true;
    this.error = null;

    this.settlementClient.getSettlementTrades(
      this.currentPage,
      this.pageSize,
      this.filterValueDate || undefined,
      this.filterStatus || undefined,
      this.filterCurrencyPair || undefined
    ).subscribe({
      next: (result: PagedResult<SettlementTrade>) => {
        this.trades = result.content;
        this.totalElements = result.totalElements;
        this.totalPages = result.totalPages;
        this.loading = false;
      },
      error: (err: AppError) => {
        this.error = err.message;
        this.loading = false;
      }
    });
  }

  applyFilters(): void {
    this.currentPage = 0;
    this.loadTrades();
  }

  clearFilters(): void {
    this.filterValueDate = '';
    this.filterStatus = '';
    this.filterCurrencyPair = '';
    this.currentPage = 0;
    this.loadTrades();
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages) {
      this.currentPage = page;
      this.loadTrades();
    }
  }

  isAtRisk(trade: SettlementTrade): boolean {
    return trade.atSettlementRisk;
  }

  getTraderDeskLink(tradeId: string): string {
    return `${this.config.traderDeskBaseUrl}/trades/${tradeId}`;
  }

  retry(): void {
    this.loadTrades();
  }
}
