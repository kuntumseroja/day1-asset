export type UserRole = 'MAKER' | 'CHECKER' | 'OPERATOR';
export type UserTier = 'TIER_1' | 'TIER_2' | 'TIER_3';
export type TransactionType = 'ISSUANCE' | 'REDEMPTION' | 'TRANSFER';
export type TransactionStatus =
  | 'QUEUED'
  | 'FUNDING'
  | 'MINTING'
  | 'SETTLED'
  | 'DENIED'
  | 'DUPLICATE_SUPPRESSED'
  | 'COMPENSATED';

export type SettlementEventType =
  | 'QUEUE_UPDATE'
  | 'STATUS_TRANSITION'
  | 'SETTLEMENT_COMPLETED'
  | 'COMPENSATING_REFUND'
  | 'DUPLICATE_SUPPRESSED';

export interface UserProfile {
  participantId: string;
  role: UserRole;
  tier: UserTier;
  displayName: string;
}

export interface IssuanceRequest {
  amount: number;
  valueDate: string;
  fundingReference: string;
}

export interface Transaction {
  uetr: string;
  type: TransactionType;
  status: TransactionStatus;
  amount: number;
  queuePosition: number;
  duplicateSuppressed?: boolean;
}

export interface QueueItem extends Transaction {
  participantId: string;
  submittedAt: string;
}

export interface LimitsDashboard {
  perIssuanceCap: number;
  perIssuanceUsed: number;
  dailyCumulativeCap: number;
  dailyCumulativeUsed: number;
}

export interface TimelineEntry {
  status: string;
  at: string;
}

export interface TransactionDetail extends Transaction {
  timeline: TimelineEntry[];
  iso20022: {
    messageType: string;
    xml: string;
  };
}

export interface SettlementEvent {
  eventId: string;
  eventType: SettlementEventType;
  uetr: string;
  participantId: string;
  transactionType: TransactionType;
  status: TransactionStatus;
  amount: number;
  queuePosition: number | null;
  timestamp: string;
  duplicateSuppressed: boolean;
}

export interface ApiError {
  error: string;
  reason?: string;
}
