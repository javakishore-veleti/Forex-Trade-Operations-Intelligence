import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Subject, timer } from 'rxjs';
import { switchMap, takeUntil } from 'rxjs/operators';
import { PositionClient } from '../../core/api/clients';
import { AppConfigService } from '../../core/config/app-config.service';
import { LivePosition } from '../../core/models';
import { AppError } from '../../core/http/interceptors';

@Component({
  selector: 'app-live-position',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './live-position.component.html',
  styleUrl: './live-position.component.css'
})
export class LivePositionComponent implements OnInit, OnDestroy {
  positions: LivePosition[] = [];
  loading = true;
  error: string | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly positionClient: PositionClient,
    readonly config: AppConfigService
  ) {}

  ngOnInit(): void {
    timer(0, this.config.livePositionIntervalMs)
      .pipe(
        switchMap(() => this.positionClient.getLivePositions()),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: (positions) => {
          this.positions = positions;
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

  exceedsThreshold(position: LivePosition): boolean {
    return Math.abs(position.netPosition) > this.config.positionDisplayThreshold;
  }

  retry(): void {
    this.loading = true;
    this.positionClient.getLivePositions().subscribe({
      next: (positions) => {
        this.positions = positions;
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
