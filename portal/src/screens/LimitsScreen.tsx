import { useCallback, useEffect, useId, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Column,
  Grid,
  InlineNotification,
  ProgressBar,
  Stack,
  Tag,
  Tile,
} from '@carbon/react';
import { api } from '../api/client';
import type { LimitsDashboard } from '../api/types';
import { useSettlementEvents } from '../hooks/useSettlementEvents';
import { formatRp, limitStatus, pct } from '../utils/format';

type LimitMetricProps = {
  title: string;
  used: number;
  cap: number;
  description: string;
};

const STATUS_TAG: Record<
  ReturnType<typeof limitStatus>,
  { label: string; type: 'green' | 'blue' | 'red' }
> = {
  healthy: { label: 'Healthy', type: 'green' },
  warning: { label: 'Approaching limit', type: 'blue' },
  critical: { label: 'Near cap', type: 'red' },
};

function LimitMetric({ title, used, cap, description }: LimitMetricProps) {
  const percent = pct(used, cap);
  const remaining = Math.max(0, cap - used);
  const status = limitStatus(percent);
  const tag = STATUS_TAG[status];
  const progressStatus = status === 'critical' ? 'error' : status === 'warning' ? 'active' : 'finished';

  return (
    <Tile style={{ height: '100%' }}>
      <Stack gap={5}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
            gap: '0.75rem',
            flexWrap: 'wrap',
          }}
        >
          <div>
            <h3 style={{ margin: 0 }}>{title}</h3>
            <p style={{ margin: '0.25rem 0 0', fontSize: '0.875rem' }}>{description}</p>
          </div>
          <Tag type={tag.type} size="sm">
            {tag.label}
          </Tag>
        </div>

        <dl style={{ margin: 0, display: 'grid', gap: '0.75rem' }}>
          <div>
            <dt style={{ fontSize: '0.75rem', margin: 0, opacity: 0.8 }}>Used</dt>
            <dd style={{ margin: '0.125rem 0 0', fontSize: '1.25rem', fontWeight: 600 }}>
              {formatRp(used)}
            </dd>
          </div>
          <div>
            <dt style={{ fontSize: '0.75rem', margin: 0, opacity: 0.8 }}>Remaining</dt>
            <dd style={{ margin: '0.125rem 0 0', fontSize: '1.125rem' }}>{formatRp(remaining)}</dd>
          </div>
          <div>
            <dt style={{ fontSize: '0.75rem', margin: 0, opacity: 0.8 }}>Cap</dt>
            <dd style={{ margin: '0.125rem 0 0' }}>{formatRp(cap)}</dd>
          </div>
        </dl>

        <ProgressBar
          label={`${percent}% consumed`}
          helperText={
            status === 'critical'
              ? 'Critical — issuance may be denied near this threshold'
              : status === 'warning'
                ? 'Approaching policy limit — plan remaining volume carefully'
                : `${formatRp(remaining)} headroom remaining`
          }
          value={percent}
          max={100}
          status={progressStatus}
          aria-label={`${title}: ${percent}% of ${formatRp(cap)} used`}
        />
      </Stack>
    </Tile>
  );
}

// KF: BC-01.04 — Limits dashboard (per-issuance & daily cumulative gauges)
export function LimitsScreen() {
  const [limits, setLimits] = useState<LimitsDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const headingId = useId();

  const refresh = useCallback(async () => {
    try {
      setLimits(await api.getLimits());
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load limits');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useSettlementEvents(() => {
    void refresh();
  });

  if (loading) {
    return (
      <Tile aria-busy="true" aria-label="Loading limits dashboard">
        <p style={{ margin: 0 }}>Loading limits…</p>
      </Tile>
    );
  }

  if (error || !limits) {
    return (
      <InlineNotification
        kind="error"
        title="Limits unavailable"
        subtitle={error ?? 'No limit data returned'}
        hideCloseButton
        role="alert"
      />
    );
  }

  const dailyPercent = pct(limits.dailyCumulativeUsed, limits.dailyCumulativeCap);

  return (
    <Stack gap={6}>
      <Tile aria-labelledby={headingId}>
        <Stack gap={4}>
          <header>
            <h2 id={headingId} style={{ margin: 0 }}>
              Limit Consumption
            </h2>
            <p style={{ margin: '0.5rem 0 0', maxWidth: '42rem' }}>
              Live policy caps synced on each refresh. Updated automatically when settlement events
              arrive over WebSocket.
            </p>
          </header>
          <p style={{ margin: 0 }}>
            <Link to="/issuance" className="cds--link">
              Submit new issuance
            </Link>
          </p>
        </Stack>
      </Tile>

      <Grid fullWidth narrow>
        <Column lg={8} md={4} sm={4}>
          <LimitMetric
            title="Per-issuance cap"
            description="Maximum amount for a single issuance request"
            used={limits.perIssuanceUsed}
            cap={limits.perIssuanceCap}
          />
        </Column>
        <Column lg={8} md={4} sm={4}>
          <LimitMetric
            title="Daily cumulative cap"
            description="Total issuance volume allowed within the current business day"
            used={limits.dailyCumulativeUsed}
            cap={limits.dailyCumulativeCap}
          />
        </Column>
      </Grid>

      {dailyPercent >= 80 && (
        <InlineNotification
          kind={dailyPercent >= 95 ? 'error' : 'warning'}
          title={dailyPercent >= 95 ? 'Daily cap nearly exhausted' : 'Daily cap approaching limit'}
          subtitle={`${formatRp(limits.dailyCumulativeUsed)} of ${formatRp(limits.dailyCumulativeCap)} used (${dailyPercent}%)`}
          hideCloseButton
          lowContrast
        />
      )}
    </Stack>
  );
}
