const POLICY_URL = (process.env.POLICY_URL || 'http://localhost:8084').replace(/\/$/, '');

const DEFAULT_CAPS = {
  perIssuanceCap: 500_000_000,
  dailyCumulativeCap: 5_000_000_000,
};

/** Keep the highest-version row when multiple ACTIVE versions exist for one rule name. */
export function parseCapsFromRules(rules) {
  const caps = { ...DEFAULT_CAPS };
  const latestByName = new Map();

  for (const rule of rules) {
    const prev = latestByName.get(rule.name);
    if (!prev || (rule.version ?? 0) > (prev.version ?? 0)) {
      latestByName.set(rule.name, rule);
    }
  }

  for (const [name, rule] of latestByName) {
    if (name === 'per_issuance_cap') {
      const match = rule.drlContent?.match(/amount\s*>\s*(\d+)/);
      if (match) caps.perIssuanceCap = Number(match[1]);
    }
    if (name === 'daily_cumulative_cap') {
      const match = rule.drlContent?.match(/dailyCumulative\s*\+\s*amount\s*>=\s*(\d+)L?/);
      if (match) caps.dailyCumulativeCap = Number(match[1]);
    }
  }

  return caps;
}

export async function fetchActiveCaps() {
  const capsRes = await fetch(`${POLICY_URL}/api/v1/policy/caps`);
  if (capsRes.ok) {
    const caps = await capsRes.json();
    return {
      perIssuanceCap: Number(caps.perIssuanceCap),
      dailyCumulativeCap: Number(caps.dailyCumulativeCap),
      source: 'policy-caps',
    };
  }

  const rulesRes = await fetch(`${POLICY_URL}/api/v1/rules?status=ACTIVE`);
  if (!rulesRes.ok) {
    throw new Error(`policy caps HTTP ${capsRes.status}, rules HTTP ${rulesRes.status}`);
  }
  return { ...parseCapsFromRules(await rulesRes.json()), source: 'policy-rules' };
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

export async function checkPolicyHealth() {
  try {
    const res = await fetch(`${POLICY_URL}/actuator/health`);
    return res.ok;
  } catch {
    return false;
  }
}
