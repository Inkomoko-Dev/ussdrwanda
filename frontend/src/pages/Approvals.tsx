import { api } from '../api';
import { useAuth } from '../auth';
import { Alert, Card, PageHead, fmtDate, useLoad } from '../components/ui';
import type { ReconAction } from '../types';
import { useState } from 'react';

const LABELS: Record<string, string> = {
  REPOST: 'Repost to core banking', MARK_POSTED: 'Mark as posted', MARK_FAILED: 'Mark as failed', MARK_REVERSED: 'Mark as reversed',
};

export default function Approvals() {
  const { can, me } = useAuth();
  const { data, error, reload } = useLoad(() => api.get<ReconAction[]>('/api/admin/reconciliation-actions'), []);
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');

  async function decide(a: ReconAction, approve: boolean) {
    const comment = prompt(approve ? 'Approval comment (optional)' : 'Reason for rejection') ?? undefined;
    if (!approve && !comment) return;
    setErr('');
    try {
      const res = await api.post<ReconAction>(`/api/admin/reconciliation-actions/${a.id}/${approve ? 'approve' : 'reject'}`, { comment });
      setMsg(`${a.transactionReference}: ${approve ? res.result ?? 'approved' : 'rejected'}`);
      reload();
    } catch (e) { setErr((e as Error).message); }
  }

  return (
    <>
      <PageHead title="Approvals" subtitle="Maker / checker: a different user must approve each reconciliation" />
      <Alert kind="success">{msg}</Alert>
      <Alert>{error || err}</Alert>
      <Card>
        <div className="table-wrap">
          <table>
            <thead><tr><th>Transaction</th><th>Action</th><th>Maker</th><th>Comment</th><th>Proposed</th><th></th></tr></thead>
            <tbody>
              {data?.length === 0 && <tr><td colSpan={6} className="empty">Nothing awaiting approval.</td></tr>}
              {data?.map((a) => (
                <tr key={a.id}>
                  <td className="mono">{a.transactionReference}</td><td>{LABELS[a.action] ?? a.action}{a.fineractTxnId ? ` (${a.fineractTxnId})` : ''}</td>
                  <td>{a.maker}</td><td>{a.makerComment}</td><td>{fmtDate(a.createdAt)}</td>
                  <td>
                    {can('RECON_APPROVE') && a.maker !== me?.user.username ? (
                      <div className="actions">
                        <button className="btn small" onClick={() => decide(a, true)}>Approve</button>
                        <button className="btn secondary small" onClick={() => decide(a, false)}>Reject</button>
                      </div>
                    ) : <span className="sub" style={{ margin: 0 }}>{a.maker === me?.user.username ? 'Your proposal' : 'Checker only'}</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </>
  );
}
