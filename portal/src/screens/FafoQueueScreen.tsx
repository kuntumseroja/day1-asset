import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Button,
  DataTable,
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

const STATUS_COLORS: Record<TransactionStatus, 'blue' | 'green' | 'red' | 'gray' | 'purple' | 'teal'> = {
  QUEUED: 'blue',
  FUNDING: 'teal',
  MINTING: 'purple',
  SETTLED: 'green',
  DENIED: 'red',
  DUPLICATE_SUPPRESSED: 'gray',
  COMPENSATED: 'red',
};

function StatusChip({ status, duplicate }: { status: TransactionStatus; duplicate?: boolean }) {
  return (
    <span style={{ display: 'inline-flex', gap: '0.25rem' }}>
      <Tag type={STATUS_COLORS[status] ?? 'gray'} size="sm">
        {status}
      </Tag>
      {duplicate && (
        <Tag type="gray" size="sm">
          duplicate suppressed
        </Tag>
      )}
    </span>
  );
}

function formatRp(amount: number): string {
  return `Rp ${amount.toLocaleString('id-ID')}`;
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

function QueueTable({
  rows,
  queue,
}: {
  rows: ReturnType<typeof mapRows>;
  queue: QueueItem[];
}) {
  const navigate = useNavigate();

  return (
    <TableContainer>
      <DataTable rows={rows} headers={headers} size="md">
        {({ rows, headers, getTableProps, getHeaderProps, getRowProps }) => (
          <Table {...getTableProps()}>
            <TableHead>
              <TableRow>
                {headers.map((header) => (
                  <TableHeader {...getHeaderProps({ header })} key={header.key}>
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
                  return (
                    <TableRow
                      {...getRowProps({ row })}
                      key={row.id}
                      style={{ cursor: 'pointer' }}
                      onClick={() => navigate(`/transactions/${row.id}`)}
                    >
                      {row.cells.map((cell) => (
                        <TableCell key={cell.id}>
                          {cell.info.header === 'uetr' ? (
                            <Link
                              to={`/transactions/${row.id}`}
                              className="cds--link"
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
  );
}

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

// KF: BC-01.03 — FAFO settlement queue (strict FIFO, live status chips)
export function FafoQueueScreen() {
  const [queue, setQueue] = useState<QueueItem[]>([]);
  const [activity, setActivity] = useState<QueueItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [resetting, setResetting] = useState(false);

  const refresh = useCallback(async () => {
    try {
      const [pending, all] = await Promise.all([api.getQueue(), api.getActivity()]);
      setQueue(pending);
      setActivity(all);
    } catch {
      setQueue([]);
      setActivity([]);
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
    } finally {
      setResetting(false);
    }
  };

  const terminal = activity.filter((item) => !PENDING_STATUSES.includes(item.status));

  return (
    <Tile>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '1rem' }}>
        <h2 style={{ margin: 0 }}>FAFO Settlement Queue</h2>
        <Button kind="tertiary" size="sm" onClick={() => void handleResetDemo()} disabled={resetting}>
          {resetting ? 'Loading demo…' : 'Load scripted day'}
        </Button>
      </div>
      <p>Strictly FIFO — {loading ? 'loading…' : `${queue.length} pending items`}</p>
      <QueueTable rows={mapRows(queue)} queue={queue} />

      <h3 style={{ marginTop: '2rem' }}>Today&apos;s activity</h3>
      <p>Settled, denied, and duplicate-suppressed items (terminal states).</p>
      <QueueTable rows={mapRows(terminal)} queue={terminal} />
    </Tile>
  );
}
