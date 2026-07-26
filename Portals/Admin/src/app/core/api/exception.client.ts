import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';
import { ExceptionEntry } from '../models';

export interface ExceptionFilter {
  type?: string;
  regionCode?: string;
  tradeId?: string;
  fromDate?: string;
  toDate?: string;
}

@Injectable({ providedIn: 'root' })
export class ExceptionClient {
  constructor(
    private readonly http: HttpClient,
    private readonly config: AppConfigService
  ) {}

  getExceptions(filter?: ExceptionFilter): Observable<ExceptionEntry[]> {
    let params = new HttpParams();
    if (filter?.type) params = params.set('type', filter.type);
    if (filter?.regionCode) params = params.set('regionCode', filter.regionCode);
    if (filter?.tradeId) params = params.set('tradeId', filter.tradeId);
    if (filter?.fromDate) params = params.set('fromDate', filter.fromDate);
    if (filter?.toDate) params = params.set('toDate', filter.toDate);

    return this.http.get<ExceptionEntry[]>(
      `${this.config.apiBaseUrl}/exceptions`,
      { params }
    );
  }

  resolveException(exceptionId: string, action: string): Observable<void> {
    return this.http.post<void>(
      `${this.config.apiBaseUrl}/exceptions/${exceptionId}/resolve`,
      { action }
    );
  }
}
