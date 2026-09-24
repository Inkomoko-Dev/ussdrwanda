import type { PasswordPolicy } from '../types';

export function passwordChecks(policy: PasswordPolicy, pw: string, username?: string) {
  const rules = [
    { ok: pw.length >= policy.minLength, label: `At least ${policy.minLength} characters` },
  ];
  if (policy.requireUpper) rules.push({ ok: /[A-Z]/.test(pw), label: 'An uppercase letter' });
  if (policy.requireLower) rules.push({ ok: /[a-z]/.test(pw), label: 'A lowercase letter' });
  if (policy.requireDigit) rules.push({ ok: /\d/.test(pw), label: 'A digit' });
  if (policy.requireSpecial) rules.push({ ok: /[^A-Za-z0-9]/.test(pw), label: 'A special character' });
  if (username && username.length >= 3) {
    rules.push({ ok: !pw.toLowerCase().includes(username.toLowerCase()), label: 'Does not contain the username' });
  }
  return rules;
}

export default function PasswordRules({ policy, password, username }: {
  policy: PasswordPolicy; password: string; username?: string;
}) {
  return (
    <ul className="rules" aria-label="Password requirements">
      {passwordChecks(policy, password, username).map((r) => (
        <li key={r.label} className={r.ok ? 'ok' : 'no'}>{r.ok ? '✓' : '○'} {r.label}</li>
      ))}
      {policy.historyCount > 0 && <li className="no">○ Not one of your last {policy.historyCount} passwords</li>}
    </ul>
  );
}
