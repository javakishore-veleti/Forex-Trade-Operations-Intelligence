import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ExceptionClient, ExceptionFilter } from '../../core/api/exception.client';
import { ExceptionEntry } from '../../core/models';
import { AppError } from '../../core/http/error.interceptor';

@Component({
  selector: 'app-exception-queue',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './exception-queue.component.html',
  styleUrl: './exception-queue.component.css'
})
export class ExceptionQueueComponent implements OnInit {
  exceptions: ExceptionEntry[] = [];
  loading = true;
  error: string | null = null;
  confirmingAction: { exceptionId: string; action: string } | null = null;

  // Filters
  filterType = '';
  filterRegion = '';
  filterTradeId = '';
  filterFromDate = '';
  filterToDate = '';

  constructor(
    private readonly exceptionClient: ExceptionClient,
    private readonly route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    const regionParam = this.route.snapshot.queryParamMap.get('region');
    if (regionParam) {
      this.filterRegion = regionParam;
    }
    this.loadExceptions();
  }

  loadExceptions(): void {
    this.loading = true;
    this.error = null;

    const filter: ExceptionFilter = {};
    if (this.filterType) filter.type = this.filterType;
    if (this.filterRegion) filter.regionCode = this.filterRegion;
    if (this.filterTradeId) filter.tradeId = this.filterTradeId;
    if (this.filterFromDate) filter.fromDate = this.filterFromDate;
    if (this.filterToDate) filter.toDate = this.filterToDate;

    this.exceptionClient.getExceptions(filter).subscribe({
      next: (exceptions) => {
        this.exceptions = exceptions;
        this.loading = false;
      },
      error: (err: AppError) => {
        this.error = err.message;
        this.loading = false;
      }
    });
  }

  applyFilters(): void {
    this.loadExceptions();
  }

  clearFilters(): void {
    this.filterType = '';
    this.filterRegion = '';
    this.filterTradeId = '';
    this.filterFromDate = '';
    this.filterToDate = '';
    this.loadExceptions();
  }

  confirmResolve(exceptionId: string, action: string): void {
    this.confirmingAction = { exceptionId, action };
  }

  cancelResolve(): void {
    this.confirmingAction = null;
  }

  executeResolve(): void {
    if (!this.confirmingAction) return;
    const { exceptionId, action } = this.confirmingAction;

    this.exceptionClient.resolveException(exceptionId, action).subscribe({
      next: () => {
        this.confirmingAction = null;
        this.loadExceptions();
      },
      error: (err: AppError) => {
        this.error = err.message;
        this.confirmingAction = null;
      }
    });
  }

  retry(): void {
    this.loadExceptions();
  }
}
