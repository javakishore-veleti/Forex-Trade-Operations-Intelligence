import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Subject, timer } from 'rxjs';
import { switchMap, takeUntil } from 'rxjs/operators';
import { EodClient } from '../../core/api/eod.client';
import { AppConfigService } from '../../core/config/app-config.service';
import { EodStatus, RegionalCloseStatus } from '../../core/models';
import { AppError } from '../../core/http/error.interceptor';

@Component({
  selector: 'app-eod-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './eod-dashboard.component.html',
  styleUrl: './eod-dashboard.component.css'
})
export class EodDashboardComponent implements OnInit, OnDestroy {
  eodStatus: EodStatus | null = null;
  loading = true;
  error: string | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly eodClient: EodClient,
    private readonly config: AppConfigService
  ) {}

  ngOnInit(): void {
    timer(0, this.config.eodDashboardIntervalMs)
      .pipe(
        switchMap(() => this.eodClient.getEodStatus()),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: (status) => {
          this.eodStatus = status;
          this.loading = false;
          this.error = null;
        },
        error: (err: AppError) => {
          this.error = err.message;
          this.loading = false;
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  getElapsedTime(startedAt: string): string {
    const start = new Date(startedAt).getTime();
    const now = Date.now();
    const diffMs = now - start;
    const minutes = Math.floor(diffMs / 60000);
    const seconds = Math.floor((diffMs % 60000) / 1000);
    return `${minutes}m ${seconds}s`;
  }

  getStateIcon(state: string): string {
    switch (state) {
      case 'CLOSED': return '✓';
      case 'READY': return '●';
      case 'IN_PROGRESS': return '◐';
      case 'BLOCKED': return '✗';
      default: return '?';
    }
  }

  retry(): void {
    this.loading = true;
    this.error = null;
    this.eodClient.getEodStatus().subscribe({
      next: (status) => {
        this.eodStatus = status;
        this.loading = false;
      },
      error: (err: AppError) => {
        this.error = err.message;
        this.loading = false;
      }
    });
  }
}
