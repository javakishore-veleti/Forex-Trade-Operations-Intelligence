import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { RiskClient } from '../../core/api/risk.client';
import { RiskAggregation } from '../../core/models';
import { AppError } from '../../core/http/error.interceptor';

@Component({
  selector: 'app-risk-aggregation',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './risk-aggregation.component.html',
  styleUrl: './risk-aggregation.component.css'
})
export class RiskAggregationComponent implements OnInit {
  regionAggregations: RiskAggregation[] = [];
  bookAggregations: RiskAggregation[] = [];
  loading = true;
  error: string | null = null;

  constructor(private readonly riskClient: RiskClient) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    this.error = null;

    forkJoin({
      byRegion: this.riskClient.getAggregationsByRegion(),
      byBook: this.riskClient.getAggregationsByBook()
    }).subscribe({
      next: (result) => {
        this.regionAggregations = result.byRegion;
        this.bookAggregations = result.byBook;
        this.loading = false;
      },
      error: (err: AppError) => {
        this.error = err.message;
        this.loading = false;
      }
    });
  }

  isBreach(agg: RiskAggregation): boolean {
    return agg.totalRiskAmount > agg.limit;
  }

  retry(): void {
    this.loadData();
  }
}
