export function formatRp(amount: number): string {
  return `Rp ${amount.toLocaleString('id-ID')}`;
}

export function pct(used: number, cap: number): number {
  if (cap <= 0) return 0;
  return Math.min(100, Math.round((used / cap) * 100));
}

export function limitStatus(pctUsed: number): 'healthy' | 'warning' | 'critical' {
  if (pctUsed >= 95) return 'critical';
  if (pctUsed >= 80) return 'warning';
  return 'healthy';
}
