import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';
import { EodStatus } from '../models';

@Injectable({ providedIn: 'root' })
export class EodClient {
  constructor(
    private readonly http: HttpClient,
    private readonly config: AppConfigService
  ) {}

  getEodStatus(): Observable<EodStatus> {
    return this.http.get<EodStatus>(
      `${this.config.apiBaseUrl}/eod/status`
    );
  }
}
