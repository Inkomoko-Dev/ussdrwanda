import { useCallback, useEffect, useState, type ReactNode } from 'react';
import type { Page } from '../types';

export function Card({ title, children, actions }: { title?: string; children: ReactNode; actions?: ReactNode }) {
  return (
    <section className="card">
      {(title || actions) && (
        <div className="page-head" style={{ marginBottom: 12 }}>
          {title && <h2 style={{ margin: 0 }}>{title}</h2>}
          {actions}
        </div>
      )}
      {children}
    </section>
  );
}

export function PageHead({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="page-head">
      <div>
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {actions && <div className="actions">{actions}</div>}
    </div>
  );
}

export function PasswordInput({ value, onChange, autoComplete }: {
  value: string;
  onChange: (value: string) => void;
  autoComplete?: string;
}) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="password-wrap">
      <input
        type={visible ? 'text' : 'password'}
        value={value}
        autoComplete={autoComplete}
        onChange={(e) => onChange(e.target.value)}
      />
      <button
        type="button"
        className="password-toggle"
        aria-label={visible ? 'Hide password' : 'Show password'}
        aria-pressed={visible}
        onClick={() => setVisible((shown) => !shown)}
      >
        {visible ? <EyeOffIcon /> : <EyeIcon />}
      </button>
    </div>
  );
}

function EyeIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

function EyeOffIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 3l18 18" />
      <path d="M10.6 10.6a3 3 0 0 0 4.2 4.2" />
      <path d="M9.9 5.1A10.8 10.8 0 0 1 12 5c6.5 0 10 7 10 7a18.4 18.4 0 0 1-3.2 4.2" />
      <path d="M6.1 6.1C3.8 7.8 2 12 2 12s3.5 7 10 7a10.6 10.6 0 0 0 4.1-.8" />
    </svg>
  );
}

export function Field({ label, help, children }: { label: string; help?: string; children: ReactNode }) {
  return (
    <div className="field">
      <label>{label}</label>
      {children}
      {help && <span className="help">{help}</span>}
    </div>
  );
}

export function Alert({ kind = 'error', children }: { kind?: 'error' | 'success' | 'info'; children: ReactNode }) {
  if (!children) return null;
  return <div className={`alert ${kind}`} role={kind === 'error' ? 'alert' : 'status'}>{children}</div>;
}

export function Modal({ title, onClose, children, footer, wide }: {
  title: string; onClose: () => void; children: ReactNode; footer?: ReactNode; wide?: boolean;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`modal${wide ? ' wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-head">
          <h2 style={{ margin: 0 }}>{title}</h2>
          <button className="btn ghost" onClick={onClose} aria-label="Close">✕</button>
        </div>
        {children}
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

const STATUS_KIND: Record<string, string> = {
  ACTIVE: 'ok', POSTED: 'ok', APPROVED: 'ok', SETTLED: 'ok', ACCEPTED: 'ok',
  NOT_SET: 'info', PENDING: 'info', INITIATED: 'info', PAID: 'info', PENDING_APPROVAL: 'warn', UNDER_REVIEW: 'info',
  LOCKED: 'bad', DISABLED: 'bad', FAILED: 'bad', POSTING_FAILED: 'warn', NEEDS_REVIEW: 'warn', IN_ARREARS: 'warn',
  REVERSED: 'neutral', REJECTED: 'neutral', CLOSED: 'neutral', WITHDRAWN: 'neutral',
  COMPLETED: 'ok', USER_EXIT: 'ok', IN_PROGRESS: 'info', ABANDONED: 'warn', CONSENT_DECLINED: 'neutral',
  NOT_REGISTERED: 'neutral', ACCOUNT_LOCKED: 'bad', ERROR: 'bad', ENDED: 'neutral',
};
const STATUS_LABEL: Record<string, string> = {
  NOT_SET: 'PIN not set', PENDING_APPROVAL: 'Pending approval', ACCEPTED: 'Accepted', NOT_ACCEPTED: 'Not yet',
  USER_EXIT: 'Exited from menu', ABANDONED: 'Left / timed out', IN_PROGRESS: 'In progress',
  CONSENT_DECLINED: 'Declined terms', ERROR: 'System error',
};

export function StatusBadge({ status }: { status: string }) {
  const label = STATUS_LABEL[status] ?? status.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
  return <span className={`badge ${STATUS_KIND[status] ?? 'neutral'}`}>{label}</span>;
}

export function Pager<T>({ page, onPage }: { page?: Page<T>; onPage: (n: number) => void }) {
  if (!page || page.page.totalPages <= 1) return null;
  const { number, totalPages, totalElements } = page.page;
  return (
    <div className="pager">
      <span>{totalElements} records · page {number + 1} of {totalPages}</span>
      <button className="btn secondary small" disabled={number === 0} onClick={() => onPage(number - 1)}>Previous</button>
      <button className="btn secondary small" disabled={number + 1 >= totalPages} onClick={() => onPage(number + 1)}>Next</button>
    </div>
  );
}

export function fmtDate(v?: string | null): string {
  if (!v) return '—';
  const d = new Date(v);
  return isNaN(d.getTime()) ? v : d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

export function fmtMoney(v?: number | null, currency = ''): string {
  if (v === undefined || v === null) return '—';
  return `${currency ? currency + ' ' : ''}${Number(v).toLocaleString(undefined, { maximumFractionDigits: 0 })}`;
}

/** Loads data with loading / error state and a reload function. */
export function useLoad<T>(loader: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const load = useCallback(loader, deps);
  const reload = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setData(await load());
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [load]);
  useEffect(() => {
    reload();
  }, [reload]);
  return { data, error, loading, reload, setData };
}

/** Official Inkomoko logo (frontend/public/logo.png) — shown on white surfaces only. */
export function Logo({ height = 46, className }: { height?: number; className?: string }) {
  return <img src={`${import.meta.env.BASE_URL}logo.png`} alt="Inkomoko" style={{ height }} className={className} />;
}

/** The logo's three colours as a thin accent bar. */
export function BrandBar() {
  return (
    <div className="brand-bar" aria-hidden="true">
      <span style={{ background: 'var(--navy)' }} />
      <span style={{ background: 'var(--coral)' }} />
      <span style={{ background: 'var(--sky)' }} />
    </div>
  );
}
