export const environment = {
  production: true,
  apiBaseUrl: '/api',
  traderDeskBaseUrl: '/traderdesk',
  polling: {
    livePositionIntervalMs: 30000,
    exposureIntervalMs: 30000
  },
  thresholds: {
    counterpartyWarningPercent: 80,
    counterpartyBreachPercent: 100,
    positionDisplayThreshold: 10000000
  },
  pagination: {
    defaultPageSize: 25
  }
};
