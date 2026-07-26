import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AppConfigService {
  get apiBaseUrl(): string {
    return environment.apiBaseUrl;
  }

  get traderDeskBaseUrl(): string {
    return environment.traderDeskBaseUrl;
  }

  get livePositionIntervalMs(): number {
    return environment.polling.livePositionIntervalMs;
  }

  get exposureIntervalMs(): number {
    return environment.polling.exposureIntervalMs;
  }

  get counterpartyWarningPercent(): number {
    return environment.thresholds.counterpartyWarningPercent;
  }

  get counterpartyBreachPercent(): number {
    return environment.thresholds.counterpartyBreachPercent;
  }

  get positionDisplayThreshold(): number {
    return environment.thresholds.positionDisplayThreshold;
  }

  get defaultPageSize(): number {
    return environment.pagination.defaultPageSize;
  }
}
