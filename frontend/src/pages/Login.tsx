import { useState, type FormEvent } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import AuthShell from '../components/AuthShell';
import { Alert, Field, PasswordInput } from '../components/ui';
import type { LoginResponse } from '../types';

export default function Login() {
  const { signIn } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const res = await api.post<LoginResponse>('/api/auth/login', { username, password });
      await signIn(res.token);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title="Mobile self-service for Inkomoko clients."
      text="Staff portal to onboard clients to USSD, support their PINs and follow repayments and reconciliation.">
      <form onSubmit={submit}>
        <h2>Staff sign in</h2>
        <p className="sub">Use your Inkomoko portal account.</p>
        <Alert>{error}</Alert>
        <Field label="Email or username">
          <input value={username} autoComplete="username" autoFocus onChange={(e) => setUsername(e.target.value)} />
        </Field>
        <Field label="Password">
          <PasswordInput value={password} autoComplete="current-password" onChange={setPassword} />
        </Field>
        <button className="btn" style={{ width: '100%', marginTop: 6, padding: '11px 18px' }} disabled={busy || !username || !password}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
        <p className="sub" style={{ marginTop: 16, fontSize: 12 }}>Forgot your password? Ask a portal administrator to reset it.</p>
      </form>
    </AuthShell>
  );
}
