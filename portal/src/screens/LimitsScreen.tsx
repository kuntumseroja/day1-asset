import { useCallback, useEffect, useState } from 'react';
import { ProgressBar, Stack, Tile } from '@carbon/react';
import { api } from '../api/client';
import type { LimitsDashboard } from '../api/types';
import { useSettlementEvents } from '../hooks/useSettlementEvents';

function formatRp(amount: number): string {
  return `Rp ${amount.toLocaleString('id-ID')}`;
}

function pct(used: number, cap: number): number {
  if (cap <= 0) return 0;
  return Math.min(100, Math.round((used / cap) * 100));
}

// KF: BC-01.04 — Limits dashboard (per-issuance & daily cumulative gauges)
export function LimitsScreen() {
  const [limits, setLimits] = useState<LimitsDashboard | null>(null);

  const refresh = useCallback(async () => {
    setLimits(await api.getLimits());
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useSettlementEvents(() => {
    void refresh();
  });

  if (!limits) {
    return <Tile>Loading limits…</Tile>;
  }

  return (
    <Tile>
      <Stack gap={7}>
        <h2>Limit Consumption</h2>

        <div>
          <p>
            Per-issuance cap: {formatRp(limits.perIssuanceUsed)} / {formatRp(limits.perIssuanceCap)}
          </p>
          <ProgressBar
            label="Per-issuance"
            helperText={`${pct(limits.perIssuanceUsed, limits.perIssuanceCap)}% of cap`}
            value={pct(limits.perIssuanceUsed, limits.perIssuanceCap)}
            max={100}
          />
        </div>

        <div>
          <p>
            Daily cumulative: {formatRp(limits.dailyCumulativeUsed)} /{' '}
            {formatRp(limits.dailyCumulativeCap)}
          </p>
          <ProgressBar
            label="Daily cumulative"
            helperText={`${pct(limits.dailyCumulativeUsed, limits.dailyCumulativeCap)}% of cap`}
            value={pct(limits.dailyCumulativeUsed, limits.dailyCumulativeCap)}
            max={100}
          />
        </div>
      </Stack>
    </Tile>
  );
}
