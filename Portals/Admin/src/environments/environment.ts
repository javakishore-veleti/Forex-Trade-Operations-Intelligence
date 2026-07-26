export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080/api',
  agentWebhookUrl: 'http://localhost:5678/webhook',
  polling: {
    eodDashboardIntervalMs: 30000,
    approvalInboxIntervalMs: 15000,
    exceptionQueueIntervalMs: 30000
  }
};
