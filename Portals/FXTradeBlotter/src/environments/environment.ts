export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080/api',
  traderDeskBaseUrl: 'http://localhost:4201',
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
