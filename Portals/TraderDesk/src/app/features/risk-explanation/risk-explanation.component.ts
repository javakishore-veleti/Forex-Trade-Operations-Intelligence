import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { RiskClient } from '../../core/api/clients';
import { PriorRiskComparison } from '../../core/models';
import { AppError } from '../../core/http/interceptors';

@Component({
  selector: 'app-risk-explanation',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './risk-explanation.component.html',
  styleUrl: './risk-explanation.component.css'
})
export class RiskExplanationComponent implements OnInit {
  comparison: PriorRiskComparison | null = null;
  loading = true;
  error: string | null = null;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly riskClient: RiskClient
  ) {}

  ngOnInit(): void {
    const tradeId = this.route.snapshot.paramMap.get('tradeId');
    if (tradeId) {
      this.loadRisk(tradeId);
    }
  }

  loadRisk(tradeId: string): void {
    this.loading = true;
    this.error = null;

    this.riskClient.getRiskComparison(tradeId).subscribe({
      next: (comparison) => {
        this.comparison = comparison;
        this.loading = false;
      },
      error: (err: AppError) => {
        this.error = err.message;
        this.loading = false;
      }
    });
  }

  getFactorPercent(contributionAmount: number): number {
    if (!this.comparison) return 0;
    const total = this.comparison.current.riskAmount;
    return total > 0 ? (contributionAmount / total) * 100 : 0;
  }

  retry(): void {
    const tradeId = this.route.snapshot.paramMap.get('tradeId');
    if (tradeId) {
      this.loadRisk(tradeId);
    }
  }
}
