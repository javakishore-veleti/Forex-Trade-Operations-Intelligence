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

export type RegionalCloseState = 'IN_PROGRESS' | 'READY' | 'BLOCKED' | 'CLOSED';

export type GlobalConsolidationState = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'BLOCKED';

export type ExceptionType = 'LATE_TRADE' | 'BRANCH_BLOCKER' | 'DLQ_MESSAGE' | 'POISON_MESSAGE' | 'SEQUENCE_VIOLATION';

export type ExceptionStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'ESCALATED';

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export type RiskClassification = 'M' | 'H';

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

export interface SequenceViolation {
  tradeId: string;
  expectedStatus: TradeStatus;
  actualStatus: TradeStatus;
  detectedAt: string;
  description: string;
}

export interface DlqEntry {
  tradeId: string;
  topic: string;
  failureReason: string;
  timestamp: string;
  retryCount: number;
}

export interface ReconciliationResult {
  tradeId: string;
  status: string;
  violatedInvariants: string[];
  permittedActions: string[];
  reconciledAt: string;
}

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

export interface TradeInvestigation {
  trade: TradeDetail;
  lifecycleEvents: LifecycleEvent[];
  riskResult?: RiskResult;
  sequenceViolations: SequenceViolation[];
  dlqEntries: DlqEntry[];
  reconciliation?: ReconciliationResult;
}

export interface RegionalCloseStatus {
  regionCode: string;
  state: RegionalCloseState;
  startedAt: string;
  blockerCode?: string;
  blockerDescription?: string;
}

export interface EodStatus {
  globalBusinessDate: string;
  globalConsolidationStatus: GlobalConsolidationState;
  regions: RegionalCloseStatus[];
}

export interface RiskAggregation {
  groupKey: string;
  groupType: 'REGION' | 'TRADING_BOOK';
  totalRiskAmount: number;
  riskCurrency: string;
  limit: number;
  utilizationPercent: number;
  riskLevel: RiskLevel;
  tradeCount: number;
  lastCalculatedAt: string;
}

export interface ExceptionEntry {
  exceptionId: string;
  type: ExceptionType;
  tradeId?: string;
  regionCode: string;
  createdAt: string;
  status: ExceptionStatus;
  description: string;
  permittedActions: string[];
  isPoisonMessage: boolean;
}

export interface ApprovalRequest {
  approvalReference: string;
  agentName: string;
  proposedAction: string;
  impactReport: string;
  riskClassification: RiskClassification;
  createdAt: string;
  status: ApprovalStatus;
}

export interface ApprovalDecision {
  approvalReference: string;
  decision: 'APPROVED' | 'REJECTED';
  operatorNote?: string;
}
