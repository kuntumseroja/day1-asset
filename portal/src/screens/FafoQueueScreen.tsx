import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
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

// KF: BC-01.03 — FAFO settlement queue (strict FIFO, live status chips)
export function FafoQueueScreen() {
  const [queue, setQueue] = useState<QueueItem[]>([]);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      const items = await api.getQueue();
      setQueue(items);
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

  const rows = queue.map((item) => ({
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

  return (
    <Tile>
      <h2>FAFO Settlement Queue</h2>
      <p>Strictly FIFO — {loading ? 'loading…' : `${queue.length} pending items`}</p>
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
                {rows.map((row) => {
                  const original = queue.find((q) => q.uetr === row.id);
                  return (
                    <TableRow {...getRowProps({ row })} key={row.id}>
                      {row.cells.map((cell) => (
                        <TableCell key={cell.id}>
                          {cell.info.header === 'UETR' ? (
                            <Link to={`/transactions/${row.id}`}>{cell.value}</Link>
                          ) : cell.info.header === 'Status' && original ? (
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
                })}
              </TableBody>
            </Table>
          )}
        </DataTable>
      </TableContainer>
    </Tile>
  );
}
