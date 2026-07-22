const POLICY_URL = (process.env.POLICY_URL || 'http://localhost:8084').replace(/\/$/, '');

const DEFAULT_CAPS = {
  perIssuanceCap: 500_000_000,
  dailyCumulativeCap: 5_000_000_000,
};

/** Extract cap thresholds from active Drools rule bodies. */
export function parseCapsFromRules(rules) {
  const caps = { ...DEFAULT_CAPS };

  for (const rule of rules) {
    if (rule.name === 'per_issuance_cap') {
      const match = rule.drlContent?.match(/amount\s*>\s*(\d+)/);
      if (match) caps.perIssuanceCap = Number(match[1]);
    }
    if (rule.name === 'daily_cumulative_cap') {
      const match = rule.drlContent?.match(/dailyCumulative\s*\+\s*amount\s*>=\s*(\d+)L?/);
      if (match) caps.dailyCumulativeCap = Number(match[1]);
    }
  }

  return caps;
}

export async function fetchActiveCaps() {
  const res = await fetch(`${POLICY_URL}/api/v1/rules?status=ACTIVE`);
  if (!res.ok) {
    throw new Error(`policy rules HTTP ${res.status}`);
  }
  return parseCapsFromRules(await res.json());
}

export async function evaluatePolicy({ participantId, tier, amount, dailyCumulative }) {
  const res = await fetch(`${POLICY_URL}/api/v1/policy/evaluate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      participantId,
      tier,
      amount,
      dailyCumulative,
      timestamp: new Date().toISOString(),
    }),
  });
  if (!res.ok) {
    throw new Error(`policy evaluate HTTP ${res.status}`);
  }
  return res.json();
}
