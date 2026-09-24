import { useState } from 'react';
import { api, qs } from '../api';
import { useAuth } from '../auth';
import { Alert, Card, PageHead, StatusBadge, fmtMoney, useLoad } from '../components/ui';
import type { ReportSummary } from '../types';

const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

export default function Dashboard() {
  const { me } = useAuth();
  const today = new Date();
  const [from, setFrom] = useState(iso(new Date(today.getFullYear(), today.getMonth(), 1)));
  const [to, setTo] = useState(iso(today));
  const { data, error, loading } = useLoad(() => api.get<ReportSummary>(`/api/admin/reports/summary${qs({ from, to })}`), [from, to]);
  const currency = me?.country?.currency ?? '';

  const tiles = data ? [
    { label: 'USSD sessions', value: data.sessions.toLocaleString(), hint: `${data.authenticatedSessions.toLocaleString()} signed in with PIN` },
    { label: 'Completion rate', value: `${data.completionRatePct}%`, hint: `${data.completedSessions.toLocaleString()} completed` },
    { label: 'Abandonment rate', value: `${data.abandonmentRatePct}%`, hint: `${data.abandonedSessions.toLocaleString()} abandoned` },
    { label: 'Avg response time', value: `${Math.round(data.avgResponseMs)} ms`, hint: 'Target ≤ 3,000 ms per menu' },
    { label: 'Failed PIN logins', value: data.loginFailures.toLocaleString(), hint: `${data.pinLocks} PIN lock(s)` },
    { label: 'Awaiting approval', value: data.pendingReconciliationActions.toLocaleString(), hint: 'Reconciliation actions' },
  ] : [];
  const statuses = data ? Object.keys(data.repaymentCountByStatus).sort() : [];

  return (
    <>
      <PageHead title="Dashboard" subtitle="Channel usage and repayment performance"
        actions={
          <div className="toolbar" style={{ margin: 0 }}>
            <label>From <input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} /></label>
            <label>To <input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} /></label>
          </div>
        } />
      <Alert>{error}</Alert>
      {loading && !data && <p className="sub">Loading…</p>}
      <div className="grid tiles">
        {tiles.map((t) => (
          <div className="tile" key={t.label}>
            <div className="label">{t.label}</div>
            <div className="value">{t.value}</div>
            <div className="hint">{t.hint}</div>
          </div>
        ))}
      </div>
      <Card title="Repayments by status">
        {statuses.length === 0 ? <div className="empty">No repayments in this period.</div> : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Status</th><th className="num">Transactions</th><th className="num">Amount</th></tr></thead>
              <tbody>
                {statuses.map((s) => (
                  <tr key={s}>
                    <td><StatusBadge status={s} /></td>
                    <td className="num">{data!.repaymentCountByStatus[s].toLocaleString()}</td>
                    <td className="num">{fmtMoney(data!.repaymentAmountByStatus[s], currency)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </>
  );
}
