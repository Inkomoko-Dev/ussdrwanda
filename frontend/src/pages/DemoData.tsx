import { useState, type ChangeEvent } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { Alert, Card, Field, Modal, PageHead, StatusBadge, fmtMoney, useLoad } from '../components/ui';
import type { DemoClientView, DemoLoan } from '../types';

const LOAN_STATUSES = ['UNDER_REVIEW', 'APPROVED', 'ACTIVE', 'IN_ARREARS', 'SETTLED', 'REJECTED', 'WITHDRAWN', 'CLOSED'];

/** Pilot only: simulated core-banking clients and loans (replaced by Fineract in live mode). */
export default function DemoData() {
  const { me } = useAuth();
  const currency = me?.country?.currency ?? '';
  const { data, error, reload } = useLoad(() => api.get<DemoClientView[]>('/api/admin/demo/clients'), []);
  const [adding, setAdding] = useState(false);
  const [editLoan, setEditLoan] = useState<{ loan: Partial<DemoLoan>; msisdn: string } | null>(null);
  const [actionError, setActionError] = useState('');

  async function remove(msisdn: string) {
    if (!confirm(`Delete demo client ${msisdn} and all their loans?`)) return;
    try { await api.del(`/api/admin/demo/clients/${msisdn}`); reload(); } catch (e) { setActionError((e as Error).message); }
  }

  return (
    <>
      <PageHead title="Demo core banking" subtitle="Pilot data standing in for Fineract until the live integration is switched on"
        actions={<button className="btn" onClick={() => setAdding(true)}>Add demo client</button>} />
      <Alert kind="info">Numbers listed here behave as Inkomoko clients in core banking. A client can use USSD only once onboarded by staff — tick "Onboard to USSD now" or use Customers → Onboard.</Alert>
      <Alert>{error || actionError}</Alert>
      {data?.map(({ client, loans }) => (
        <Card key={client.id} title={`${client.displayName} · ${client.msisdn}`}
          actions={<div className="actions">
            {!client.active && <StatusBadge status="DISABLED" />}
            <button className="btn secondary small" onClick={() => setEditLoan({ msisdn: client.msisdn, loan: { status: 'ACTIVE' } })}>Add loan</button>
            <button className="btn ghost small" onClick={() => remove(client.msisdn)}>Delete</button>
          </div>}>
          {loans.length === 0 ? <div className="empty">No loans.</div> : (
            <div className="table-wrap">
              <table>
                <thead><tr><th>Loan</th><th>Product</th><th>Status</th><th className="num">Principal</th><th className="num">Total</th><th className="num">Overdue</th><th>Next due</th><th></th></tr></thead>
                <tbody>
                  {loans.map((l) => (
                    <tr key={l.id}>
                      <td className="mono">{l.accountNo}</td><td>{l.productName}</td><td><StatusBadge status={l.status} /></td>
                      <td className="num">{fmtMoney(l.principalOutstanding, currency)}</td>
                      <td className="num">{fmtMoney(l.totalOutstanding, currency)}</td>
                      <td className="num">{fmtMoney(l.totalOverdue, currency)}</td>
                      <td>{l.nextDueDate ?? '—'}</td>
                      <td><button className="btn ghost small" onClick={() => setEditLoan({ msisdn: client.msisdn, loan: l })}>Edit</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      ))}
      {adding && <AddClient onClose={() => setAdding(false)} onDone={() => { setAdding(false); reload(); }} />}
      {editLoan && <LoanModal {...editLoan} onClose={() => setEditLoan(null)} onDone={() => { setEditLoan(null); reload(); }} />}
    </>
  );
}

function AddClient({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const [f, setF] = useState({ msisdn: '', displayName: '', sampleLoans: true, onboard: true });
  const [error, setError] = useState('');
  async function save() {
    try { await api.post('/api/admin/demo/clients', { ...f, active: true }); onDone(); } catch (e) { setError((e as Error).message); }
  }
  return (
    <Modal title="Add demo client" onClose={onClose}
      footer={<><button className="btn secondary" onClick={onClose}>Cancel</button><button className="btn" disabled={!f.msisdn} onClick={save}>Add</button></>}>
      <Alert>{error}</Alert>
      <Field label="Phone number"><input value={f.msisdn} placeholder="250788123456" onChange={(e) => setF({ ...f, msisdn: e.target.value })} /></Field>
      <Field label="Name"><input value={f.displayName} onChange={(e) => setF({ ...f, displayName: e.target.value })} /></Field>
      <label className="check"><input type="checkbox" checked={f.sampleLoans} onChange={(e) => setF({ ...f, sampleLoans: e.target.checked })} /> Add sample loans (one active, one in arrears)</label>
      <label className="check"><input type="checkbox" checked={f.onboard} onChange={(e) => setF({ ...f, onboard: e.target.checked })} /> Onboard to USSD now (client creates a PIN on first dial)</label>
    </Modal>
  );
}

function LoanModal({ loan, msisdn, onClose, onDone }: { loan: Partial<DemoLoan>; msisdn: string; onClose: () => void; onDone: () => void }) {
  const [f, setF] = useState<Partial<DemoLoan>>(loan);
  const [error, setError] = useState('');
  const num = (k: keyof DemoLoan) => (e: ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value === '' ? undefined : Number(e.target.value) });
  async function save() {
    const body = { productName: f.productName, status: f.status, principalOutstanding: f.principalOutstanding, interestOutstanding: f.interestOutstanding,
      totalOverdue: f.totalOverdue, installment: f.installment, nextDueDate: f.nextDueDate || undefined, accountNo: f.accountNo };
    try {
      if (loan.id) await api.put(`/api/admin/demo/loans/${loan.id}`, body);
      else await api.post(`/api/admin/demo/clients/${msisdn}/loans`, body);
      onDone();
    } catch (e) { setError((e as Error).message); }
  }
  async function remove() {
    if (!loan.id || !confirm('Delete this loan?')) return;
    try { await api.del(`/api/admin/demo/loans/${loan.id}`); onDone(); } catch (e) { setError((e as Error).message); }
  }
  return (
    <Modal title={loan.id ? `Edit loan ${loan.accountNo}` : 'Add demo loan'} onClose={onClose}
      footer={<>{loan.id && <button className="btn ghost" onClick={remove}>Delete</button>}
        <button className="btn secondary" onClick={onClose}>Cancel</button><button className="btn" onClick={save}>Save</button></>}>
      <Alert>{error}</Alert>
      <div className="form-grid">
        <Field label="Product"><input value={f.productName ?? ''} onChange={(e) => setF({ ...f, productName: e.target.value })} /></Field>
        <Field label="Status"><select value={f.status} onChange={(e) => setF({ ...f, status: e.target.value })}>{LOAN_STATUSES.map((s) => <option key={s}>{s}</option>)}</select></Field>
        <Field label="Principal outstanding"><input type="number" min={0} value={f.principalOutstanding ?? ''} onChange={num('principalOutstanding')} /></Field>
        <Field label="Interest outstanding"><input type="number" min={0} value={f.interestOutstanding ?? ''} onChange={num('interestOutstanding')} /></Field>
        <Field label="Total overdue"><input type="number" min={0} value={f.totalOverdue ?? ''} onChange={num('totalOverdue')} /></Field>
        <Field label="Installment"><input type="number" min={0} value={f.installment ?? ''} onChange={num('installment')} /></Field>
        <Field label="Next due date"><input type="date" value={f.nextDueDate ?? ''} onChange={(e) => setF({ ...f, nextDueDate: e.target.value })} /></Field>
        <Field label="Account number" help="Generated when blank"><input value={f.accountNo ?? ''} onChange={(e) => setF({ ...f, accountNo: e.target.value })} /></Field>
      </div>
    </Modal>
  );
}
