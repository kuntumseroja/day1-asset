import { KeyboardEvent, useCallback, useEffect, useId, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Button,
  DataTable,
  InlineNotification,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  Tag,
  Tile,
} from '@carbon/react';
import { api } from '../api/client';
import type { QueueItem, TransactionStatus } from '../api/types';
import { useSettlementEvents } from '../hooks/useSettlementEvents';
import { formatRp } from '../utils/format';

const STATUS_COLORS: Record<TransactionStatus, 'blue' | 'green' | 'red' | 'gray' | 'purple' | 'teal'> = {
  QUEUED: 'blue',
  FUNDING: 'teal',
  MINTING: 'purple',
  SETTLED: 'green',
  DENIED: 'red',
  DUPLICATE_SUPPRESSED: 'gray',
  COMPENSATED: 'red',
};

const STATUS_LABELS: Record<TransactionStatus, string> = {
  QUEUED: 'Queued',
  FUNDING: 'Funding in progress',
  MINTING: 'Minting',
  SETTLED: 'Settled',
  DENIED: 'Denied',
  DUPLICATE_SUPPRESSED: 'Duplicate suppressed',
  COMPENSATED: 'Compensated',
};

function StatusChip({ status, duplicate }: { status: TransactionStatus; duplicate?: boolean }) {
  const label = STATUS_LABELS[status] ?? status;
  return (
    <span style={{ display: 'inline-flex', gap: '0.25rem', flexWrap: 'wrap' }}>
      <Tag type={STATUS_COLORS[status] ?? 'gray'} size="sm" title={label}>
        <span aria-label={`Status: ${label}`}>{status}</span>
      </Tag>
      {duplicate && (
        <Tag type="gray" size="sm">
          <span aria-label="Duplicate suppressed">duplicate suppressed</span>
        </Tag>
      )}
    </span>
  );
}

const headers = [
  { key: 'queuePosition', header: 'Pos' },
  { key: 'uetr', header: 'UETR' },
  { key: 'type', header: 'Type' },
  { key: 'amount', header: 'Amount' },
  { key: 'participantId', header: 'Participant' },
  { key: 'status', header: 'Status' },
];

const PENDING_STATUSES: TransactionStatus[] = ['QUEUED', 'FUNDING', 'MINTING'];

function mapRows(queue: QueueItem[]) {
  return queue.map((item) => ({
    id: item.uetr,
    queuePosition: String(item.queuePosition),
    uetr: item.uetr.slice(0, 8) + '…',
    type: item.type,
    amount: formatRp(item.amount),
    participantId: item.participantId,
    status: item.status,
    duplicate: item.duplicateSuppressed,
    fullUetr: item.uetr,
  }));
}

function handleRowKeyDown(
  event: KeyboardEvent<HTMLTableRowElement>,
  uetr: string,
  navigate: ReturnType<typeof useNavigate>,
) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    navigate(`/transactions/${uetr}`);
  }
}

function QueueTable({
  rows,
  queue,
  caption,
  ariaLabel,
}: {
  rows: ReturnType<typeof mapRows>;
  queue: QueueItem[];
  caption: string;
  ariaLabel: string;
}) {
  const navigate = useNavigate();

  return (
    <div style={{ overflowX: 'auto' }}>
      <TableContainer title={caption}>
        <DataTable rows={rows} headers={headers} size="md" aria-label={ariaLabel}>
          {({ rows, headers, getTableProps, getHeaderProps, getRowProps }) => (
            <Table {...getTableProps()}>
              <caption className="cds--visually-hidden">{caption}</caption>
              <TableHead>
                <TableRow>
                  {headers.map((header) => (
                    <TableHeader {...getHeaderProps({ header })} key={header.key} scope="col">
                      {header.header}
                    </TableHeader>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={headers.length}>No items</TableCell>
                  </TableRow>
                ) : (
                  rows.map((row) => {
                    const original = queue.find((q) => q.uetr === row.id);
                    const fullUetr = original?.uetr ?? row.id;
                    return (
                      <TableRow
                        {...getRowProps({ row })}
                        key={row.id}
                        tabIndex={0}
                        role="link"
                        aria-label={`View transaction ${fullUetr}, position ${original?.queuePosition ?? '—'}, status ${original?.status ?? 'unknown'}`}
                        style={{ cursor: 'pointer' }}
                        onClick={() => navigate(`/transactions/${row.id}`)}
                        onKeyDown={(event) => handleRowKeyDown(event, row.id, navigate)}
                      >
                        {row.cells.map((cell) => (
                          <TableCell key={cell.id}>
                            {cell.info.header === 'uetr' ? (
                              <Link
                                to={`/transactions/${row.id}`}
                                className="cds--link"
                                title={fullUetr}
                                aria-label={`UETR ${fullUetr}`}
                                onClick={(event) => event.stopPropagation()}
                              >
                                {cell.value}
                              </Link>
                            ) : cell.info.header === 'status' && original ? (
                              <StatusChip
                                status={original.status}
                                duplicate={original.duplicateSuppressed}
                              />
                            ) : (
                              cell.value
                            )}
                          </TableCell>
                        ))}
                      </TableRow>
                    );
                  })
                )}
              </TableBody>
            </Table>
          )}
        </DataTable>
      </TableContainer>
    </div>
  );
}

// KF: BC-01.03 — FAFO settlement queue (strict FIFO, live status chips)
export function FafoQueueScreen() {
  const [queue, setQueue] = useState<QueueItem[]>([]);
  const [activity, setActivity] = useState<QueueItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [resetting, setResetting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const pendingCountId = useId();
  const headingId = useId();

  const refresh = useCallback(async () => {
    try {
      const [pending, all] = await Promise.all([api.getQueue(), api.getActivity()]);
      setQueue(pending);
      setActivity(all);
      setError(null);
    } catch (err) {
      setQueue([]);
      setActivity([]);
      setError(err instanceof Error ? err.message : 'Failed to load queue');
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

  const handleResetDemo = async () => {
    setResetting(true);
    try {
      await api.resetDemo();
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load demo data');
    } finally {
      setResetting(false);
    }
  };

  const terminal = activity.filter((item) => !PENDING_STATUSES.includes(item.status));

  return (
    <Tile aria-labelledby={headingId}>
      <Stack gap={7}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
            gap: '1rem',
            flexWrap: 'wrap',
          }}
        >
          <header>
            <h2 id={headingId} style={{ margin: 0 }}>
              FAFO Settlement Queue
            </h2>
            <p style={{ marginTop: '0.5rem', maxWidth: '40rem' }}>
              Strictly first-in-first-out processing. Select a row or UETR link to open transaction
              detail.
            </p>
          </header>
          <Button
            kind="tertiary"
            size="sm"
            onClick={() => void handleResetDemo()}
            disabled={resetting || loading}
            aria-busy={resetting}
          >
            {resetting ? 'Loading demo…' : 'Load scripted day'}
          </Button>
        </div>

        {error && (
          <InlineNotification kind="error" title="Queue error" subtitle={error} hideCloseButton role="alert" />
        )}

        <section aria-labelledby="pending-queue-heading">
          <Stack gap={4}>
            <div>
              <h3 id="pending-queue-heading" style={{ margin: 0 }}>
                Pending queue
              </h3>
              <p
                id={pendingCountId}
                aria-live="polite"
                aria-busy={loading}
                style={{ marginTop: '0.25rem' }}
              >
                {loading ? 'Loading pending items…' : `Strictly FIFO — ${queue.length} pending items`}
              </p>
            </div>
            <QueueTable
              rows={mapRows(queue)}
              queue={queue}
              caption="Pending FAFO settlement queue in FIFO order"
              ariaLabel="Pending settlement queue"
            />
          </Stack>
        </section>

        <section aria-labelledby="activity-heading">
          <Stack gap={4}>
            <div>
              <h3 id="activity-heading" style={{ margin: 0 }}>
                Today&apos;s activity
              </h3>
              <p style={{ marginTop: '0.25rem' }}>
                Settled, denied, and duplicate-suppressed items (terminal states).
              </p>
            </div>
            <QueueTable
              rows={mapRows(terminal)}
              queue={terminal}
              caption="Terminal settlement activity for today"
              ariaLabel="Terminal settlement activity"
            />
          </Stack>
        </section>
      </Stack>
    </Tile>
  );
}
