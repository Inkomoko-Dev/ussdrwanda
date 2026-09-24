import { useState, type FormEvent } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import PasswordRules, { passwordChecks } from '../components/PasswordRules';
import AuthShell from '../components/AuthShell';
import { Alert, Field, PasswordInput } from '../components/ui';
import type { LoginResponse } from '../types';

/** Password change form — forced after first login / reset / expiry, or from "My account". */
export function ChangePasswordForm({ onDone }: { onDone?: () => void }) {
  const { me, signIn } = useAuth();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [done, setDone] = useState('');
  const [busy, setBusy] = useState(false);
  if (!me) return null;
  const ok = passwordChecks(me.passwordPolicy, next, me.user.username).every((r) => r.ok) && next === confirm && current;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const res = await api.post<LoginResponse>('/api/auth/change-password', { currentPassword: current, newPassword: next });
      await signIn(res.token);
      setDone('Password changed.');
      setCurrent(''); setNext(''); setConfirm('');
      onDone?.();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit}>
      <Alert>{error}</Alert>
      <Alert kind="success">{done}</Alert>
      <Field label="Current password"><PasswordInput autoComplete="current-password" value={current} onChange={setCurrent} /></Field>
      <Field label="New password"><PasswordInput autoComplete="new-password" value={next} onChange={setNext} /></Field>
      <PasswordRules policy={me.passwordPolicy} password={next} username={me.user.username} />
      <Field label="Confirm new password"><PasswordInput autoComplete="new-password" value={confirm} onChange={setConfirm} /></Field>
      {confirm && next !== confirm && <Alert>Passwords do not match</Alert>}
      <button className="btn" disabled={busy || !ok}>{busy ? 'Saving…' : 'Change password'}</button>
    </form>
  );
}

export default function ForcedPasswordChange() {
  const { me, signOut } = useAuth();
  return (
    <AuthShell title={me?.passwordExpired ? 'Your password has expired.' : 'Welcome — choose your own password.'}
      text="For security, set a personal password before using the portal. It must follow the password policy shown.">
      <h2>Change password</h2>
      <p className="sub">Signed in as {me?.user.username}</p>
      <ChangePasswordForm />
      <button className="btn ghost" style={{ marginTop: 10 }} onClick={signOut}>Sign out</button>
    </AuthShell>
  );
}
