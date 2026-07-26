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

export interface TradeDetail {
  tradeId: string;
  currencyPair: string;
  notionalAmount: number;
  direction: 'BUY' | 'SELL';
  tradeDate: string;
  valueDate: string;
  regionCode: string;
  tradingBookId: string;
  counterpartyId: string;
  status: TradeStatus;
}

export interface LifecycleEvent {
  eventId: string;
  tradeId: string;
  eventType: string;
  timestamp: string;
  status: TradeStatus;
  details?: Record<string, unknown>;
}

export interface RiskResult {
  tradeId: string;
  riskAmount: number;
  riskCurrency: string;
  riskLevel: RiskLevel;
  ruleVersion: string;
  contributingFactors: ContributingFactor[];
  rulesFired: string[];
  calculatedAt: string;
}

export interface ContributingFactor {
  factorName: string;
  contributionAmount: number;
  currency: string;
}

export interface PriorRiskComparison {
  current: RiskResult;
  prior?: RiskResult;
  changedFactors: FactorChange[];
}

export interface FactorChange {
  factorName: string;
  previousAmount: number;
  currentAmount: number;
  changeAmount: number;
  currency: string;
}

export interface PositionGroup {
  groupKey: string;
  groupType: 'CURRENCY_PAIR' | 'REGION';
  totalNotional: number;
  totalRiskAmount: number;
  riskCurrency: string;
  riskLevel: RiskLevel;
  tradeCount: number;
  lastCalculatedAt: string;
}

export interface TradingBookEntry {
  tradeId: string;
  currencyPair: string;
  notionalAmount: number;
  direction: 'BUY' | 'SELL';
  tradeDate: string;
  status: TradeStatus;
  riskLevel: RiskLevel;
}

export interface PagedResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}
