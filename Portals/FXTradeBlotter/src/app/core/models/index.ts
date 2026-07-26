export type TradeStatus =
  | 'TRADE_RECEIVED'
  | 'TRADE_VALIDATED'
  | 'TRADE_ENRICHED'
  | 'RISK_CALCULATED'
  | 'TRADE_CONFIRMED'
  | 'TRADE_SETTLED'
  | 'TRADE_FAILED'
  | 'TRADE_CANCELLED';

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface LivePosition {
  currencyPair: string;
  netLongNotional: number;
  netShortNotional: number;
  netPosition: number;
  tradeCount: number;
  currency: string;
}

export interface ExposureGroup {
  groupKey: string;
  groupType: 'CURRENCY_PAIR' | 'REGION';
  totalRiskAmount: number;
  riskCurrency: string;
  riskLevel: RiskLevel;
  limit?: number;
  utilizationPercent?: number;
  lastCalculatedAt: string;
}

export interface SettlementTrade {
  tradeId: string;
  currencyPair: string;
  notionalAmount: number;
  direction: 'BUY' | 'SELL';
  counterpartyId: string;
  status: TradeStatus;
  valueDate: string;
  hasDlqFlag: boolean;
  hasAnomalyFlag: boolean;
  atSettlementRisk: boolean;
}

export interface CounterpartyExposure {
  counterpartyId: string;
  counterpartyName: string;
  aggregateRiskAmount: number;
  riskCurrency: string;
  limit: number;
  utilizationPercent: number;
  riskLevel: RiskLevel;
  tradeCount: number;
  lastCalculatedAt: string;
  trades?: SettlementTrade[];
}

export interface PagedResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}
