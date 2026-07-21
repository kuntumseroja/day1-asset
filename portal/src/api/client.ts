import type {
  ApiError,
  IssuanceRequest,
  LimitsDashboard,
  QueueItem,
  Transaction,
  TransactionDetail,
  UserProfile,
} from './types';

const API_BASE = 'http://localhost:8093/api/v1';

function getToken(): string | null {
  return sessionStorage.getItem('detp_token');
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init?.headers as Record<string, string>),
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const res = await fetch(`${API_BASE}${path}`, { ...init, headers });

  if (!res.ok) {
    const body = (await res.json().catch(() => ({ error: res.statusText }))) as ApiError;
    throw new Error(body.reason ?? body.error ?? `HTTP ${res.status}`);
  }

  return res.json() as Promise<T>;
}

export const api = {
  getCurrentUser: () => request<UserProfile>('/auth/me'),

  submitIssuance: (body: IssuanceRequest) =>
    request<Transaction>('/issuance', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  getQueue: () => request<QueueItem[]>('/queue'),

  getLimits: () => request<LimitsDashboard>('/limits'),

  getTransaction: (uetr: string) => request<TransactionDetail>(`/transactions/${uetr}`),
};

export const WS_URL = 'ws://localhost:8093/api/v1/ws';
