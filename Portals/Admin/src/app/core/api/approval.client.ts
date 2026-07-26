import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';
import { ApprovalRequest, ApprovalDecision } from '../models';

@Injectable({ providedIn: 'root' })
export class ApprovalClient {
  constructor(
    private readonly http: HttpClient,
    private readonly config: AppConfigService
  ) {}

  getPendingApprovals(): Observable<ApprovalRequest[]> {
    return this.http.get<ApprovalRequest[]>(
      `${this.config.apiBaseUrl}/approvals/pending`
    );
  }

  submitDecision(decision: ApprovalDecision): Observable<void> {
    return this.http.post<void>(
      `${this.config.agentWebhookUrl}/approvals/decision`,
      decision
    );
  }
}
