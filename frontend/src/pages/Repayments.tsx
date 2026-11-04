import { useState, type ReactNode } from 'react';
import { api, getToken, qs } from '../api';
import { useAuth } from '../auth';
import { Alert, Card, Field, Modal, PageHead, Pager, StatusBadge, fmtDate, fmtMoney, useLoad } from '../components/ui';
import type { Page, ReconAction, Repayment } from '../types';

const STATUSES = ['INITIATED', 'PENDING', 'PAID', 'POSTED', 'POSTING_FAILED', 'NEEDS_REVIEW', 'FAILED', 'REVERSED'];
const RECONCILABLE = ['NEEDS_REVIEW', 'POSTING_FAILED', 'PENDING', 'INITIATED', 'PAID'];

export default function Repayments() {
  const [status, setStatus] = useState('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<string | null>(null);
  const [exportError, setExportError] = useState('');
  const { data, error, reload } = useLoad(() => api.get<Page<Repayment>>(`/api/admin/repayments${qs({ q, status, page, size: 25 })}`), [q, status, page]);

  return (
    <>
      <PageHead title="Repayments" subtitle="Mobile Money repayments from USSD, end to end" />
      <Alert>{error || exportError}</Alert>
      <Card>
        <div className="toolbar">
          <input placeholder="Reference, phone, loan or telecom ref" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
            <option value="">All statuses</option>
            {STATUSES.map((s) => <option key={s} value={s}>{s.replace(/_/g, ' ').toLowerCase()}</option>)}
          </select>
          <button className="btn secondary small" onClick={reload}>Refresh</button>
          <button className="btn secondary small" onClick={() => download(q, status).catch((e) => setExportError((e as Error).message))}>Export Excel</button>
        </div>
        <div className="table-wrap">
          <table>
            <thead><tr><th>Reference</th><th>Phone</th><th>Loan</th><th className="num">Amount</th><th>Status</th><th>Created</th><th>Note</th></tr></thead>
            <tbody>
              {data?.content.length === 0 && <tr><td colSpan={7} className="empty">No repayments.</td></tr>}
              {data?.content.map((r) => (
                <tr key={r.reference} style={{ cursor: 'pointer' }} onClick={() => setSelected(r.reference)}>
                  <td className="mono">{r.reference}</td><td className="mono">{r.msisdn}</td><td className="mono">{r.loanAccountNo}</td>
                  <td className="num">{fmtMoney(r.amount, r.currency)}</td><td><StatusBadge status={r.status} /></td>
                  <td>{fmtDate(r.createdAt)}</td><td style={{ maxWidth: 260 }}>{r.failureReason ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pager page={data ?? undefined} onPage={setPage} />
      </Card>
      {selected && <RepaymentDetail reference={selected} onClose={() => setSelected(null)} onChanged={reload} />}
    </>
  );
}

async function download(q: string, status: string) {
  const res = await fetch(`/api/admin/repayments/export${qs({ q, status })}`, {
    headers: getToken() ? { Authorization: `Bearer ${getToken()}` } : {},
  });
  if (!res.ok) throw new Error(`Export failed (${res.status})`);
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'repayments.xlsx';
  link.click();
  URL.revokeObjectURL(url);
}

function RepaymentDetail({ reference, onClose, onChanged }: { reference: string; onClose: () => void; onChanged: () => void }) {
  const { can } = useAuth();
  const { data, error, reload } = useLoad(
    () => api.get<{ transaction: Repayment; reconciliationActions: ReconAction[]; manualSuccessEnabled: boolean }>(
      `/api/admin/repayments/${reference}`), [reference]);
  const [action, setAction] = useState('REPOST');
  const [comment, setComment] = useState('');
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const t = data?.transaction;
  const pending = data?.reconciliationActions.some((a) => a.status === 'PENDING_APPROVAL');
  // Pilot override (Omnichannel manual success): any unpaid or failed payment; Omnichannel decides.
  const showMarkPaid = !!t && can('REPAYMENT_MARK_PAID') && (t.status === 'PENDING' || t.status === 'INITIATED'
    || t.status === 'FAILED' || (t.status === 'NEEDS_REVIEW' && !t.paidAt));
  const markPaidBlocked = data && !data.manualSuccessEnabled
    ? 'Switched off: set OMNICHANNEL_MANUAL_SUCCESS_ENABLED=true and restart the app.' : '';

  function markPaid() {
    if (!t) return;
    if (!confirm(`Mark ${fmtMoney(t.amount, t.currency)} from ${t.msisdn} as paid? Omnichannel records it as successful`
      + ' without debiting the customer, and it is posted to the loan.')) return;
    run('manual-success', 'Marked as paid.');
  }

  async function run(path: string, done: string) {
    setBusy(true); setErr(''); setMsg('');
    try {
      await api.post(`/api/admin/repayments/${reference}/${path}`);
      setMsg(done);
      reload(); onChanged();
    } catch (e) { setErr((e as Error).message); } finally { setBusy(false); }
  }

  async function propose() {
    setErr('');
    try {
      const chosen = t?.status === 'POSTED' ? 'MARK_REVERSED' : action;
      await api.post(`/api/admin/repayments/${reference}/reconcile`, { action: chosen, comment });
      setMsg('Proposal sent for checker approval.');
      reload(); onChanged();
    } catch (e) { setErr((e as Error).message); }
  }

  const rows: [string, ReactNode][] = t ? [
    ['Status', <StatusBadge status={t.status} />], ['Amount', fmtMoney(t.amount, t.currency)], ['Phone', t.msisdn],
    ['Loan', t.loanAccountNo], ['Network', t.telecom], ['Gateway ref', t.gatewayRef], ['MNO ref', t.telecomRef],
    ['Core-banking txn', t.fineractTxnId], ['Posting attempts', t.postingAttempts], ['Balance after', fmtMoney(t.balanceAfter, t.currency)],
    ['Created', fmtDate(t.createdAt)], ['Paid', fmtDate(t.paidAt)], ['Posted', fmtDate(t.postedAt)], ['Note', t.failureReason],
  ] : [];

  return (
    <Modal title={`Repayment ${reference}`} onClose={onClose} wide>
      <Alert>{error || err}</Alert>
      <Alert kind="success">{msg}</Alert>
      {t && can('RECON_PROPOSE') && (
        <div className="actions" style={{ marginBottom: 14 }}>
          {t.status !== 'POSTED' && t.status !== 'REVERSED' && t.status !== 'FAILED' && (
            <button className="btn secondary small" disabled={busy} onClick={() => run('telecom-status', 'Telecom status updated.')}>Check telecom status</button>
          )}
          {(t.status === 'PAID' || t.status === 'POSTING_FAILED' || (t.status === 'NEEDS_REVIEW' && t.paidAt)) && (
            <button className="btn small" disabled={busy} onClick={() => run('post', 'Posted to core banking.')}>Post to core banking</button>
          )}
        </div>
      )}
      {showMarkPaid && (
        <div className="actions" style={{ marginBottom: 14, alignItems: 'center' }}>
          <button className="btn small" disabled={busy || !!markPaidBlocked} onClick={markPaid}
            title="Pilot: Omnichannel marks this payment successful without a telecom debit">
            Mark as paid
          </button>
          {markPaidBlocked && <span className="hint">{markPaidBlocked}</span>}
        </div>
      )}
      <div className="form-grid">
        {rows.map(([k, v]) => <Field key={k} label={k}><span>{v ?? '—'}</span></Field>)}
      </div>
      {!!data?.reconciliationActions.length && (
        <>
          <h3>Reconciliation history</h3>
          <table>
            <thead><tr><th>Action</th><th>Status</th><th>Maker</th><th>Checker</th><th>Result</th></tr></thead>
            <tbody>{data.reconciliationActions.map((a) => (
              <tr key={a.id}><td>{a.action}</td><td><StatusBadge status={a.status} /></td><td>{a.maker}</td><td>{a.checker ?? '—'}</td><td>{a.result ?? a.makerComment}</td></tr>
            ))}</tbody>
          </table>
        </>
      )}
      {t && can('RECON_PROPOSE') && (RECONCILABLE.includes(t.status) || t.status === 'POSTED') && !pending && (
        <div className="card" style={{ marginTop: 16, boxShadow: 'none', border: '1px solid var(--border)' }}>
          <h3>Propose a reconciliation (maker)</h3>
          <div className="form-grid">
            <Field label="Action">
              <select value={t.status === 'POSTED' ? 'MARK_REVERSED' : action} onChange={(e) => setAction(e.target.value)}>
                {t?.status !== 'POSTED' && <option value="REPOST">Repost to core banking</option>}
                {t?.status !== 'POSTED' && <option value="MARK_FAILED">Mark as failed (not collected)</option>}
                {t?.status === 'POSTED' && <option value="MARK_REVERSED">Reverse (refund the client)</option>}
              </select>
            </Field>
          </div>
          <Field label="Comment"><textarea rows={2} value={comment} onChange={(e) => setComment(e.target.value)} /></Field>
          <button className="btn" disabled={!comment} onClick={propose}>Submit for approval</button>
        </div>
      )}
    </Modal>
  );
}
