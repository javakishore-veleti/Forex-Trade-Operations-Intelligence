import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';
import { TradeDetail, LifecycleEvent } from '../models';

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
