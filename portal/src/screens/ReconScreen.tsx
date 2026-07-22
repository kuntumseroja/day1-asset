import { useCallback, useEffect, useState } from 'react';
import { Tag, Tile, DataTable, Table, TableHead, TableRow, TableHeader, TableBody, TableCell, Loading, InlineNotification } from '@carbon/react';
// KF: BC-12.01 — Reconciliation status tile + discrepancy case list
import { api } from '../api/client';

interface ReconStatus {
  status: 'GREEN' | 'RED';
  lastRunAt?: string;
  openCases: number;
}

interface DiscrepancyCase {
  id: string;
  status: string;
  delta: number;
  candidateUetrs?: string[];
  slaDeadline?: string;
}

export function ReconScreen() {
  const [status, setStatus] = useState<ReconStatus | null>(null);
  const [cases, setCases] = useState<DiscrepancyCase[]>([]);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const [s, c] = await Promise.all([
        api.getReconStatus(),
        api.getReconCases(),
      ]);
      setStatus(s);
      setCases(c);
      setError(null);
    } catch (err) {
      setStatus(null);
      setCases([]);
      setError(err instanceof Error ? err.message : 'Failed to load recon status');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const runRecon = async () => {
    setRunning(true);
    try {
      await api.runRecon();
      await refresh();
    } finally {
      setRunning(false);
    }
  };

  if (loading) return <Loading description="Loading recon status..." />;

  if (error || !status) {
    return (
      <InlineNotification
        kind="error"
        title="Reconciliation unavailable"
        subtitle={error ?? 'Could not reach recon service at /recon/api/v1'}
        hideCloseButton
        role="alert"
      />
    );
  }

  const tileClass = status.status === 'GREEN' ? 'recon-green' : 'recon-red';

  return (
    <div>
      <h2>Three-Way Reconciliation</h2>
      <div style={{ display: 'flex', gap: '1rem', marginBottom: '2rem', alignItems: 'center' }}>
        <Tile className={tileClass} style={{
          minWidth: 200,
          background: status.status === 'GREEN' ? '#198038' : '#da1e28',
          color: '#fff',
        }}>
          <h3 style={{ margin: 0 }}>{status.status}</h3>
          <p style={{ margin: '0.5rem 0 0' }}>{status.openCases} open case(s)</p>
          {status.lastRunAt && <p style={{ fontSize: '0.75rem' }}>Last: {new Date(status.lastRunAt).toLocaleString()}</p>}
        </Tile>
        <button type="button" onClick={runRecon} disabled={running}>
          {running ? 'Running…' : 'Run Recon Now'}
        </button>
      </div>

      <h3>Discrepancy Cases</h3>
      <DataTable rows={cases.map(c => ({
        id: c.id,
        status: c.status,
        delta: c.delta.toLocaleString('id-ID'),
        uetrs: (c.candidateUetrs ?? []).join(', ') || '—',
        sla: c.slaDeadline ? new Date(c.slaDeadline).toLocaleString() : '—',
      }))} headers={[
        { key: 'status', header: 'Status' },
        { key: 'delta', header: 'Delta (Rp)' },
        { key: 'uetrs', header: 'Candidate UETRs' },
        { key: 'sla', header: 'SLA Deadline' },
      ]}>
        {({ rows, headers, getTableProps, getHeaderProps, getRowProps }) => (
          <Table {...getTableProps()}>
            <TableHead>
              <TableRow>
                {headers.map(h => (
                  <TableHeader {...getHeaderProps({ header: h })} key={h.key}>{h.header}</TableHeader>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.length === 0 ? (
                <TableRow><TableCell colSpan={4}>No cases — invariant holds.</TableCell></TableRow>
              ) : rows.map(row => (
                <TableRow {...getRowProps({ row })} key={row.id}>
                  {row.cells.map(cell => (
                    <TableCell key={cell.id}>
                      {cell.info.header === 'status' ? (
                        <Tag type={cell.value === 'OPEN' ? 'red' : 'green'}>{cell.value}</Tag>
                      ) : cell.value}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </DataTable>
    </div>
  );
}
