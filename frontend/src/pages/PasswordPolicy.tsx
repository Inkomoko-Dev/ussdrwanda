import { useEffect, useState, type ChangeEvent } from 'react';
import { api } from '../api';
import { Alert, Card, Field, PageHead, fmtDate, useLoad } from '../components/ui';
import type { PasswordPolicy as Policy } from '../types';

export default function PasswordPolicy() {
  const { data, error } = useLoad(() => api.get<Policy>('/api/admin/password-policy'), []);
  const [f, setF] = useState<Policy | null>(null);
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  useEffect(() => { if (data) setF(data); }, [data]);
  if (!f) return <><PageHead title="Password policy" /><Alert>{error}</Alert></>;

  const num = (k: keyof Policy) => (e: ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: Number(e.target.value) });
  const bool = (k: keyof Policy) => (e: ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.checked });

  async function save() {
    setErr(''); setMsg('');
    try { setF(await api.put<Policy>('/api/admin/password-policy', f)); setMsg('Password policy saved.'); } catch (e) { setErr((e as Error).message); }
  }

  return (
    <>
      <PageHead title="Password policy" subtitle={`Applies to all portal users · last changed ${fmtDate(f.updatedAt)} by ${f.updatedBy ?? '—'}`} />
      <Alert kind="success">{msg}</Alert>
      <Alert>{err}</Alert>
      <Card title="Complexity">
        <div className="form-grid">
          <Field label="Minimum length"><input type="number" min={8} max={64} value={f.minLength} onChange={num('minLength')} /></Field>
        </div>
        <label className="check"><input type="checkbox" checked={f.requireUpper} onChange={bool('requireUpper')} /> Require an uppercase letter</label>
        <label className="check"><input type="checkbox" checked={f.requireLower} onChange={bool('requireLower')} /> Require a lowercase letter</label>
        <label className="check"><input type="checkbox" checked={f.requireDigit} onChange={bool('requireDigit')} /> Require a digit</label>
        <label className="check"><input type="checkbox" checked={f.requireSpecial} onChange={bool('requireSpecial')} /> Require a special character</label>
      </Card>
      <Card title="Lifecycle & lockout">
        <div className="form-grid">
          <Field label="Expiry (days)" help="0 = never expires"><input type="number" min={0} max={365} value={f.expiryDays} onChange={num('expiryDays')} /></Field>
          <Field label="Password history" help="Last N passwords cannot be reused"><input type="number" min={0} max={24} value={f.historyCount} onChange={num('historyCount')} /></Field>
          <Field label="Failed attempts before lockout"><input type="number" min={1} max={20} value={f.maxFailedAttempts} onChange={num('maxFailedAttempts')} /></Field>
          <Field label="Lockout duration (minutes)"><input type="number" min={1} max={1440} value={f.lockoutMinutes} onChange={num('lockoutMinutes')} /></Field>
          <Field label="Session length (minutes)" help="Users are signed out after this"><input type="number" min={5} max={720} value={f.sessionMinutes} onChange={num('sessionMinutes')} /></Field>
        </div>
      </Card>
      <button className="btn" onClick={save}>Save policy</button>
    </>
  );
}
