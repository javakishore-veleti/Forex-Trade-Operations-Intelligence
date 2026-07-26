import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AppConfigService {
  get apiBaseUrl(): string {
    return environment.apiBaseUrl;
  }

  get positionSummaryIntervalMs(): number {
    return environment.polling.positionSummaryIntervalMs;
  }
}
