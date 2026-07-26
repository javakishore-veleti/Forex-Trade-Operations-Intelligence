import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { forkJoin } from 'rxjs';
import { ExposureClient } from '../../core/api/clients';
import { ExposureGroup } from '../../core/models';
import { AppError } from '../../core/http/interceptors';

@Component({
  selector: 'app-exposure-view',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './exposure-view.component.html',
  styleUrl: './exposure-view.component.css'
})
export class ExposureViewComponent implements OnInit {
  exposureByPair: ExposureGroup[] = [];
  exposureByRegion: ExposureGroup[] = [];
  loading = true;
  error: string | null = null;
  lastUpdated: string | null = null;

  constructor(private readonly exposureClient: ExposureClient) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    this.error = null;

    forkJoin({
      byPair: this.exposureClient.getExposureByPair(),
      byRegion: this.exposureClient.getExposureByRegion()
    }).subscribe({
      next: (result) => {
        this.exposureByPair = result.byPair;
        this.exposureByRegion = result.byRegion;
        this.loading = false;
        this.lastUpdated = this.getLatestTimestamp([...result.byPair, ...result.byRegion]);
      },
      error: (err: AppError) => {
        this.error = err.message;
        this.loading = false;
      }
    });
  }

  isBreach(group: ExposureGroup): boolean {
    return (group.limit != null) && (group.totalRiskAmount > group.limit);
  }

  private getLatestTimestamp(groups: ExposureGroup[]): string | null {
    if (groups.length === 0) return null;
    return groups.reduce((latest, g) =>
      g.lastCalculatedAt > latest ? g.lastCalculatedAt : latest,
      groups[0].lastCalculatedAt
    );
  }

  retry(): void {
    this.loadData();
  }
}
