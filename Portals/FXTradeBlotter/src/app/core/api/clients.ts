import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';
import {
  LivePosition,
  ExposureGroup,
  SettlementTrade,
  CounterpartyExposure,
  PagedResult
} from '../models';

@Injectable({ providedIn: 'root' })
export class PositionClient {
  constructor(
    private readonly http: HttpClient,
    private readonly config: AppConfigService
  ) {}

  getLivePositions(): Observable<LivePosition[]> {
    return this.http.get<LivePosition[]>(
      `${this.config.apiBaseUrl}/positions/live`
    );
  }
}

@Injectable({ providedIn: 'root' })
export class ExposureClient {
  constructor(
    private readonly http: HttpClient,
    private readonly config: AppConfigService
  ) {}

  getExposureByPair(): Observable<ExposureGroup[]> {
    return this.http.get<ExposureGroup[]>(
      `${this.config.apiBaseUrl}/exposure/by-pair`
    );
  }

  getExposureByRegion(): Observable<ExposureGroup[]> {
    return this.http.get<ExposureGroup[]>(
      `${this.config.apiBaseUrl}/exposure/by-region`
    );
  }
}

@Injectable({ providedIn: 'root' })
export class SettlementClient {
  constructor(
    private readonly http: HttpClient,
    private readonly config: AppConfigService
  ) {}

  getSettlementTrades(
    page: number,
    size: number,
    valueDate?: string,
    status?: string,
    currencyPair?: string
  ): Observable<PagedResult<SettlementTrade>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (valueDate) params = params.set('valueDate', valueDate);
    if (status) params = params.set('status', status);
    if (currencyPair) params = params.set('currencyPair', currencyPair);

    return this.http.get<PagedResult<SettlementTrade>>(
      `${this.config.apiBaseUrl}/settlement/trades`,
      { params }
    );
  }
}

@Injectable({ providedIn: 'root' })
export class CounterpartyClient {
  constructor(
    private readonly http: HttpClient,
    private readonly config: AppConfigService
  ) {}

  getCounterpartyExposures(): Observable<CounterpartyExposure[]> {
    return this.http.get<CounterpartyExposure[]>(
      `${this.config.apiBaseUrl}/counterparty/exposure`
    );
  }

  getCounterpartyTrades(counterpartyId: string): Observable<SettlementTrade[]> {
    return this.http.get<SettlementTrade[]>(
      `${this.config.apiBaseUrl}/counterparty/${counterpartyId}/trades`
    );
  }
}
