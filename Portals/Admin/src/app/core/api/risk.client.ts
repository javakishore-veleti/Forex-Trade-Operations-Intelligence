import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';
import { RiskResult, RiskAggregation } from '../models';

@Injectable({ providedIn: 'root' })
export class RiskClient {
  constructor(
    private readonly http: HttpClient,
    private readonly config: AppConfigService
  ) {}

  getRiskResult(tradeId: string): Observable<RiskResult> {
    return this.http.get<RiskResult>(
      `${this.config.apiBaseUrl}/risk/trades/${tradeId}`
    );
  }

  getAggregationsByRegion(): Observable<RiskAggregation[]> {
    return this.http.get<RiskAggregation[]>(
      `${this.config.apiBaseUrl}/risk/aggregations/by-region`
    );
  }

  getAggregationsByBook(): Observable<RiskAggregation[]> {
    return this.http.get<RiskAggregation[]>(
      `${this.config.apiBaseUrl}/risk/aggregations/by-book`
    );
  }
}
