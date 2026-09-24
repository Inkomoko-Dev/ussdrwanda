import type { ReactNode } from 'react';
import { Logo } from './ui';

/** Split screen used by sign-in and first-time screens: branded navy panel + white panel with the logo. */
export default function AuthShell({ title, text, children, wide }: { title: string; text: string; children: ReactNode; wide?: boolean }) {
  return (
    <div className="auth">
      <div className="auth-hero">
        <div className="accent" aria-hidden="true">
          <span style={{ background: '#fff' }} /><span style={{ background: 'var(--coral)' }} /><span style={{ background: 'var(--sky)' }} />
        </div>
        <h1>{title}</h1>
        <p>{text}</p>
      </div>
      <div className="auth-panel">
        <div className={`auth-card${wide ? ' wide' : ''}`}>
          <Logo height={64} className="logo" />
          {children}
        </div>
      </div>
    </div>
  );
}
