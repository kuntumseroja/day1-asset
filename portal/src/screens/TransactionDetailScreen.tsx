import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  InlineNotification,
  ProgressIndicator,
  ProgressStep,
  Stack,
  Tab,
  TabList,
  TabPanel,
  TabPanels,
  Tabs,
  Tag,
  Tile,
} from '@carbon/react';
import { api } from '../api/client';
import type { TransactionDetail, TransactionStatus } from '../api/types';
import { useSettlementEvents } from '../hooks/useSettlementEvents';

function formatRp(amount: number): string {
  return `Rp ${amount.toLocaleString('id-ID')}`;
}

function prettyPrintXml(xml: string): string {
  const lines = xml.trim().split('\n');
  return lines.map((l) => l.trim()).join('\n');
}

const STATUS_COLOR: Record<TransactionStatus, 'blue' | 'green' | 'red' | 'gray' | 'purple' | 'teal'> = {
  QUEUED: 'blue',
  FUNDING: 'teal',
  MINTING: 'purple',
  SETTLED: 'green',
  DENIED: 'red',
  DUPLICATE_SUPPRESSED: 'gray',
  COMPENSATED: 'red',
};

// KF: BC-01.05 — Transaction detail (timeline + ISO 20022 pacs.009 tab)
export function TransactionDetailScreen() {
  const { uetr } = useParams<{ uetr: string }>();
  const [tx, setTx] = useState<TransactionDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!uetr) return;
    try {
      setTx(await api.getTransaction(uetr));
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Not found');
    }
  }, [uetr]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useSettlementEvents((event) => {
    if (event.uetr === uetr) void refresh();
  });

  if (error) {
    return (
      <InlineNotification kind="error" title="Error" subtitle={error} hideCloseButton />
    );
  }

  if (!tx) {
    return <Tile>Loading transaction…</Tile>;
  }

  return (
    <Tile>
      <Stack gap={6}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <h2>Transaction {tx.uetr.slice(0, 8)}…</h2>
          <Tag type={STATUS_COLOR[tx.status] ?? 'gray'}>{tx.status}</Tag>
          {tx.duplicateSuppressed && (
            <Tag type="gray">duplicate suppressed</Tag>
          )}
        </div>

        <p>
          {tx.type} · {formatRp(tx.amount)} · Queue position {tx.queuePosition}
        </p>

        <Tabs>
          <TabList aria-label="Transaction detail tabs">
            <Tab>Timeline</Tab>
            <Tab>ISO 20022</Tab>
          </TabList>
          <TabPanels>
            <TabPanel>
              <ProgressIndicator vertical currentIndex={tx.timeline.length - 1}>
                {tx.timeline.map((entry, i) => (
                  <ProgressStep
                    key={`${entry.status}-${i}`}
                    label={entry.status}
                    description={new Date(entry.at).toLocaleString('id-ID')}
                    complete={i < tx.timeline.length - 1}
                    current={i === tx.timeline.length - 1}
                  />
                ))}
              </ProgressIndicator>
            </TabPanel>
            <TabPanel>
              <p>
                <strong>{tx.iso20022.messageType}</strong>
              </p>
              <pre
                style={{
                  background: '#f4f4f4',
                  padding: '1rem',
                  overflow: 'auto',
                  fontSize: '0.875rem',
                  lineHeight: 1.5,
                }}
              >
                {prettyPrintXml(tx.iso20022.xml)}
              </pre>
            </TabPanel>
          </TabPanels>
        </Tabs>
      </Stack>
    </Tile>
  );
}
