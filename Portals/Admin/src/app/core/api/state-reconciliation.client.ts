import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';
import { ReconciliationResult, SequenceViolation, DlqEntry } from '../models';

@Injectable({ providedIn: 'root' })
export class StateReconciliationClient {
  constructor(
    private readonly http: HttpClient,
    private readonly config: AppConfigService
  ) {}

  getReconciliation(tradeId: string): Observable<ReconciliationResult> {
    return this.http.get<ReconciliationResult>(
      `${this.config.apiBaseUrl}/reconciliation/trades/${tradeId}`
    );
  }

  getSequenceViolations(tradeId: string): Observable<SequenceViolation[]> {
    return this.http.get<SequenceViolation[]>(
      `${this.config.apiBaseUrl}/reconciliation/trades/${tradeId}/violations`
    );
  }

  getDlqEntries(tradeId: string): Observable<DlqEntry[]> {
    return this.http.get<DlqEntry[]>(
      `${this.config.apiBaseUrl}/dlq/trades/${tradeId}`
    );
  }

  executeAction(tradeId: string, action: string): Observable<void> {
    return this.http.post<void>(
      `${this.config.apiBaseUrl}/reconciliation/trades/${tradeId}/actions`,
      { action }
    );
  }
}
