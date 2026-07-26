import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AppConfigService {
  get apiBaseUrl(): string {
    return environment.apiBaseUrl;
  }

  get agentWebhookUrl(): string {
    return environment.agentWebhookUrl;
  }

  get eodDashboardIntervalMs(): number {
    return environment.polling.eodDashboardIntervalMs;
  }

  get approvalInboxIntervalMs(): number {
    return environment.polling.approvalInboxIntervalMs;
  }

  get exceptionQueueIntervalMs(): number {
    return environment.polling.exceptionQueueIntervalMs;
  }
}
