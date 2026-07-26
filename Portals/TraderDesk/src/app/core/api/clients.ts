import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';
import {
  TradeDetail,
  LifecycleEvent,
  RiskResult,
  PriorRiskComparison,
  PositionGroup,
  TradingBookEntry,
  PagedResult
} from '../models';

@Injectable({ providedIn: 'root' })
export class TradeLifecycleClient {
  constructor(
    private readonly http: HttpClient,
    private readonly config: AppConfigService
  ) {}

  getTradeDetail(tradeId: string): Observable<TradeDetail> {
    return this.http.get<TradeDetail>(
      `${this.config.apiBaseUrl}/trades/${tradeId}`
    );
  }

  getLifecycleEvents(tradeId: string): Observable<LifecycleEvent[]> {
    return this.http.get<LifecycleEvent[]>(
      `${this.config.apiBaseUrl}/trades/${tradeId}/lifecycle`
    );
  }
}

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

  getRiskComparison(tradeId: string): Observable<PriorRiskComparison> {
    return this.http.get<PriorRiskComparison>(
      `${this.config.apiBaseUrl}/risk/trades/${tradeId}/comparison`
    );
  }

  getPositionsByPair(): Observable<PositionGroup[]> {
    return this.http.get<PositionGroup[]>(
      `${this.config.apiBaseUrl}/risk/positions/by-pair`
    );
  }

  getPositionsByRegion(): Observable<PositionGroup[]> {
    return this.http.get<PositionGroup[]>(
      `${this.config.apiBaseUrl}/risk/positions/by-region`
    );
  }
}

@Injectable({ providedIn: 'root' })
export class TradingBookClient {
  constructor(
    private readonly http: HttpClient,
    private readonly config: AppConfigService
  ) {}

  getTrades(
    bookId: string,
    page: number,
    size: number,
    sortBy?: string,
    sortDir?: string,
    statusFilter?: string,
    riskLevelFilter?: string
  ): Observable<PagedResult<TradingBookEntry>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (sortBy) params = params.set('sortBy', sortBy);
    if (sortDir) params = params.set('sortDir', sortDir);
    if (statusFilter) params = params.set('status', statusFilter);
    if (riskLevelFilter) params = params.set('riskLevel', riskLevelFilter);

    return this.http.get<PagedResult<TradingBookEntry>>(
      `${this.config.apiBaseUrl}/books/${bookId}/trades`,
      { params }
    );
  }
}
