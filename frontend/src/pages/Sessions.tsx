import { useState } from 'react';
import { api, getToken, qs } from '../api';
import { Alert, Card, PageHead, Pager, StatusBadge, fmtDate, useLoad } from '../components/ui';
import type { MsisdnVisits, Page, SessionSummary, UssdSessionView } from '../types';

const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const STATUS_OPTIONS: [string, string][] = [
  ['', 'All outcomes'],
  ['COMPLETED', 'Completed'],
  ['USER_EXIT', 'Exited from menu'],
  ['ABANDONED', 'Left / timed out'],
  ['IN_PROGRESS', 'In progress'],
  ['NOT_REGISTERED', 'Not registered'],
  ['CONSENT_DECLINED', 'Declined terms'],
  ['ACCOUNT_LOCKED', 'Account locked'],
  ['ERROR', 'System error'],
];

type Tab = 'phones' | 'log' | 'daily';

function fmtDuration(s: number) {
  if (s < 60) return `${s}s`;
  const m = Math.floor(s / 60);
  return m < 60 ? `${m}m ${s % 60}s` : `${Math.floor(m / 60)}h ${m % 60}m`;
}

async function download(path: string, fallbackName: string) {
  const token = getToken();
  const res = await fetch(path, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  if (!res.ok) throw new Error(`Export failed (${res.status})`);
  const name = /filename="([^"]+)"/.exec(res.headers.get('Content-Disposition') ?? '')?.[1] ?? fallbackName;
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  URL.revokeObjectURL(url);
}

export default function Sessions() {
  const today = new Date();
  const [from, setFrom] = useState(iso(new Date(today.getFullYear(), today.getMonth(), 1)));
  const [to, setTo] = useState(iso(today));
  const [msisdn, setMsisdn] = useState('');
  const [status, setStatus] = useState('');
  const [tab, setTab] = useState<Tab>('phones');
  const [page, setPage] = useState(0);
  const [exportError, setExportError] = useState('');

  const resetPage = <T,>(fn: (v: T) => void) => (v: T) => { fn(v); setPage(0); };

  const summary = useLoad(() => api.get<SessionSummary>(`/api/admin/ussd-sessions/summary${qs({ from, to })}`), [from, to]);
  const phones = useLoad(
    () => tab === 'phones'
      ? api.get<Page<MsisdnVisits>>(`/api/admin/ussd-sessions/by-msisdn${qs({ from, to, msisdn, page, size: 50 })}`)
      : Promise.resolve(null),
    [tab, from, to, msisdn, page]);
  const log = useLoad(
    () => tab === 'log'
      ? api.get<Page<UssdSessionView>>(`/api/admin/ussd-sessions${qs({ from, to, msisdn, status, page, size: 50 })}`)
      : Promise.resolve(null),
    [tab, from, to, msisdn, status, page]);

  const s = summary.data;
  const count = (k: string) => s?.byStatus[k] ?? 0;
  const pct = (n: number) => (s && s.sessions ? `${((n / s.sessions) * 100).toFixed(1)}%` : '—');
  const tiles = s ? [
    { label: 'Sessions (billable)', value: s.sessions.toLocaleString(), hint: `${s.screens.toLocaleString()} screens shown` },
    { label: 'Unique phone numbers', value: s.uniquePhones.toLocaleString(),
      hint: s.uniquePhones ? `${(s.sessions / s.uniquePhones).toFixed(1)} sessions per phone` : '—' },
    { label: 'Completed', value: (count('COMPLETED') + count('USER_EXIT')).toLocaleString(),
      hint: `${pct(count('COMPLETED') + count('USER_EXIT'))} of sessions` },
    { label: 'Left / timed out', value: count('ABANDONED').toLocaleString(), hint: `${pct(count('ABANDONED'))} of sessions` },
    { label: 'Not registered', value: count('NOT_REGISTERED').toLocaleString(), hint: 'Number not found in core banking' },
    { label: 'In progress', value: count('IN_PROGRESS').toLocaleString(), hint: 'Still open right now' },
  ] : [];

  const exportCsv = async () => {
    setExportError('');
    try {
      if (tab === 'log') await download(`/api/admin/ussd-sessions/export${qs({ from, to, msisdn, status })}`, 'ussd-sessions.csv');
      else await download(`/api/admin/ussd-sessions/by-msisdn/export${qs({ from, to, msisdn })}`, 'ussd-visits-by-phone.csv');
    } catch (e) {
      setExportError((e as Error).message);
    }
  };

  const showPhone = (phone: string) => { setMsisdn(phone); setStatus(''); setTab('log'); setPage(0); };

  return (
    <>
      <PageHead title="USSD sessions" subtitle="Who dialled the USSD code, how often, and how each session ended"
        actions={
          <div className="toolbar" style={{ margin: 0 }}>
            <label>From <input type="date" value={from} max={to} onChange={(e) => resetPage(setFrom)(e.target.value)} /></label>
            <label>To <input type="date" value={to} min={from} onChange={(e) => resetPage(setTo)(e.target.value)} /></label>
          </div>
        } />
      <Alert>{summary.error || phones.error || log.error || exportError}</Alert>
      <div className="grid tiles">
        {tiles.map((t) => (
          <div className="tile" key={t.label}>
            <div className="label">{t.label}</div>
            <div className="value">{t.value}</div>
            <div className="hint">{t.hint}</div>
          </div>
        ))}
      </div>

      <Card>
        <div className="tabs">
          <button className={tab === 'phones' ? 'active' : ''} onClick={() => { setTab('phones'); setPage(0); }}>Visits per phone number</button>
          <button className={tab === 'log' ? 'active' : ''} onClick={() => { setTab('log'); setPage(0); }}>Session log</button>
          <button className={tab === 'daily' ? 'active' : ''} onClick={() => setTab('daily')}>Daily &amp; network</button>
        </div>

        {tab !== 'daily' && (
          <div className="toolbar">
            <input placeholder="Phone number" value={msisdn} onChange={(e) => resetPage(setMsisdn)(e.target.value)} />
            {tab === 'log' && (
              <select value={status} onChange={(e) => resetPage(setStatus)(e.target.value)}>
                {STATUS_OPTIONS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
              </select>
            )}
            <button className="btn secondary small" onClick={exportCsv}>Export CSV</button>
          </div>
        )}

        {tab === 'phones' && (
          <>
            <div className="table-wrap">
              <table>
                <thead><tr>
                  <th>Phone number</th><th>Customer</th><th className="num">Sessions</th><th className="num">Completed</th>
                  <th className="num">Left / timed out</th><th className="num">Not registered</th><th className="num">Other</th>
                  <th className="num">Screens</th><th>First visit</th><th>Last visit</th>
                </tr></thead>
                <tbody>
                  {phones.data?.content.length === 0 && <tr><td colSpan={10} className="empty">No sessions in this period.</td></tr>}
                  {phones.data?.content.map((v) => (
                    <tr key={v.msisdn}>
                      <td className="mono"><a href="#" onClick={(e) => { e.preventDefault(); showPhone(v.msisdn); }}>{v.msisdn}</a></td>
                      <td>{v.clientName ?? '—'}</td>
                      <td className="num"><strong>{v.sessions.toLocaleString()}</strong></td>
                      <td className="num">{v.completed.toLocaleString()}</td>
                      <td className="num">{v.leftOrTimedOut.toLocaleString()}</td>
                      <td className="num">{v.notRegistered.toLocaleString()}</td>
                      <td className="num">{v.other.toLocaleString()}</td>
                      <td className="num">{v.screens.toLocaleString()}</td>
                      <td>{fmtDate(v.firstVisit)}</td><td>{fmtDate(v.lastVisit)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager page={phones.data ?? undefined} onPage={setPage} />
          </>
        )}

        {tab === 'log' && (
          <>
            <div className="table-wrap">
              <table>
                <thead><tr>
                  <th>Started</th><th>Phone number</th><th>Customer</th><th>Network</th><th>Language</th>
                  <th>Outcome</th><th>Last screen</th><th className="num">Screens</th><th className="num">Duration</th><th>Ended</th>
                </tr></thead>
                <tbody>
                  {log.data?.content.length === 0 && <tr><td colSpan={10} className="empty">No sessions match these filters.</td></tr>}
                  {log.data?.content.map((r) => (
                    <tr key={r.id}>
                      <td>{fmtDate(r.startedAt)}</td>
                      <td className="mono">{r.msisdn}</td>
                      <td>{r.clientName ?? '—'}</td>
                      <td>{r.networkCode ?? '—'}</td>
                      <td>{r.language?.toUpperCase() ?? '—'}</td>
                      <td><StatusBadge status={r.status} /></td>
                      <td>{r.lastScreen}{r.authenticated ? '' : <span className="sub"> · not signed in</span>}</td>
                      <td className="num">{r.screens}</td>
                      <td className="num">{fmtDuration(r.durationSeconds)}</td>
                      <td>{fmtDate(r.endedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager page={log.data ?? undefined} onPage={setPage} />
          </>
        )}

        {tab === 'daily' && s && (
          <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 24 }}>
            <div className="table-wrap">
              <table>
                <thead><tr><th>Day</th><th className="num">Sessions</th><th className="num">Unique phones</th></tr></thead>
                <tbody>
                  {s.byDay.length === 0 && <tr><td colSpan={3} className="empty">No sessions in this period.</td></tr>}
                  {s.byDay.map((d) => (
                    <tr key={d.day}><td>{d.day}</td><td className="num">{d.sessions.toLocaleString()}</td><td className="num">{d.phones.toLocaleString()}</td></tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="table-wrap">
              <table>
                <thead><tr><th>Network</th><th className="num">Sessions</th><th className="num">Unique phones</th></tr></thead>
                <tbody>
                  {s.byNetwork.map((n) => (
                    <tr key={n.network}><td>{n.network}</td><td className="num">{n.sessions.toLocaleString()}</td><td className="num">{n.phones.toLocaleString()}</td></tr>
                  ))}
                </tbody>
              </table>
              <table style={{ marginTop: 24 }}>
                <thead><tr><th>Outcome</th><th className="num">Sessions</th><th className="num">Share</th></tr></thead>
                <tbody>
                  {Object.entries(s.byStatus).sort((a, b) => b[1] - a[1]).map(([k, n]) => (
                    <tr key={k}><td><StatusBadge status={k} /></td><td className="num">{n.toLocaleString()}</td><td className="num">{pct(n)}</td></tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </Card>
    </>
  );
}
