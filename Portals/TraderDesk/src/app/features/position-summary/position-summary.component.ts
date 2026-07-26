import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject, timer } from 'rxjs';
import { switchMap, takeUntil } from 'rxjs/operators';
import { RiskClient } from '../../core/api/clients';
import { AppConfigService } from '../../core/config/app-config.service';
import { PositionGroup } from '../../core/models';
import { AppError } from '../../core/http/interceptors';

@Component({
  selector: 'app-position-summary',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './position-summary.component.html',
  styleUrl: './position-summary.component.css'
})
export class PositionSummaryComponent implements OnInit, OnDestroy {
  positionsByPair: PositionGroup[] = [];
  positionsByRegion: PositionGroup[] = [];
  loading = true;
  error: string | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly riskClient: RiskClient,
    private readonly config: AppConfigService
  ) {}

  ngOnInit(): void {
    timer(0, this.config.positionSummaryIntervalMs)
      .pipe(
        switchMap(() => this.riskClient.getPositionsByPair()),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: (positions) => {
          this.positionsByPair = positions;
          this.loading = false;
          this.error = null;
        },
        error: (err: AppError) => {
          this.error = err.message;
          this.loading = false;
        }
      });

    this.riskClient.getPositionsByRegion().subscribe({
      next: (positions) => {
        this.positionsByRegion = positions;
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  retry(): void {
    this.loading = true;
    this.riskClient.getPositionsByPair().subscribe({
      next: (positions) => {
        this.positionsByPair = positions;
        this.loading = false;
        this.error = null;
      },
      error: (err: AppError) => {
        this.error = err.message;
        this.loading = false;
      }
    });
  }
}
