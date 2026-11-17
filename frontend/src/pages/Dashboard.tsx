
import { useMemo, useState } from 'react';
import {Activity,ArrowRight,Bell,CalendarDays,CheckCircle2,Clock3,CreditCard,LockKeyhole,RefreshCw,ShieldAlert,Smartphone,Wallet,Zap,
} from 'lucide-react';

import {CartesianGrid,Line,LineChart,ResponsiveContainer,Tooltip,XAxis,YAxis,
} from 'recharts';
import { api, qs } from '../api';
import { useAuth } from '../auth';
import {Alert,PageHead,StatusBadge,fmtMoney,useLoad,
} from '../components/ui';

import type { ReportSummary } from '../types';

const iso = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

const formatNumber = (value: number) =>
  Number(value ?? 0).toLocaleString();

type Metric = {
  label: string;
  value: string;
  hint: string;
  icon: typeof Smartphone;
  color: string;
  background: string;
};

function MetricCard({ metric }: { metric: Metric }) {
  const Icon = metric.icon;

  return (
    <article className="db-metric-card">
      <div className="db-metric-top">
        <div
          className="db-metric-icon"
          style={{
            color: metric.color,
            background: metric.background,
          }}
        >
          <Icon size={19} strokeWidth={2} />
        </div>
      </div>

      <div className="db-metric-label">{metric.label}</div>
      <div className="db-metric-value">{metric.value}</div>
      <div className="db-metric-hint">{metric.hint}</div>
    </article>
  );
}

function Panel({
  title,
  subtitle,
  icon,
  action,
  children,
  className = '',
}: {
  title: string;
  subtitle?: string;
  icon?: React.ReactNode;
  action?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <section className={`db-panel ${className}`}>
      <div className="db-panel-header">
        <div className="db-panel-title">
          {icon && <span className="db-panel-icon">{icon}</span>}
          <div>
            <h3>{title}</h3>
            {subtitle && <p>{subtitle}</p>}
          </div>
        </div>

        {action && <div className="db-panel-action">{action}</div>}
      </div>

      {children}
    </section>
  );
}

function EmptyState({
  message,
  detail,
}: {
  message: string;
  detail?: string;
}) {
  return (
    <div className="db-empty">
      <div className="db-empty-icon">
        <Activity size={22} />
      </div>
      <strong>{message}</strong>
      {detail && <span>{detail}</span>}
    </div>
  );
}

export default function Dashboard() {
  const { me } = useAuth();

  const today = new Date();
  const todayValue = iso(today);

  const [from, setFrom] = useState(
    iso(new Date(today.getFullYear(), today.getMonth(), 1))
  );
  const [to, setTo] = useState(todayValue);
  const [period, setPeriod] = useState('30D');

  const {
    data,
    error,
    loading,
    reload,
  } = useLoad(
    () =>
      api.get<ReportSummary>(
        `/api/admin/reports/summary${qs({ from, to })}`
      ),
    [from, to]
  );

  const currency = me?.country?.currency ?? '';

  const setPreset = (days: number, label: string) => {
    const end = new Date();
    const start = new Date();

    start.setDate(start.getDate() - (days - 1));

    setFrom(iso(start));
    setTo(iso(end));
    setPeriod(label);
  };

  const handleFromChange = (value: string) => {
    setFrom(value);
    setPeriod('CUSTOM');
  };

  const handleToChange = (value: string) => {
    setTo(value);
    setPeriod('CUSTOM');
  };

  const statuses = useMemo(
    () =>
      data
        ? Object.keys(data.repaymentCountByStatus).sort()
        : [],
    [data]
  );

  const totalRepayments = useMemo(
    () =>
      statuses.reduce(
        (total, status) =>
          total + (data?.repaymentCountByStatus[status] ?? 0),
        0
      ),
    [data, statuses]
  );

  const totalRepaymentAmount = useMemo(
    () =>
      statuses.reduce(
        (total, status) =>
          total + (data?.repaymentAmountByStatus[status] ?? 0),
        0
      ),
    [data, statuses]
  );

  const metrics: Metric[] = data
    ? [
        {
          label: 'USSD Sessions',
          value: formatNumber(data.sessions),
          hint: `${formatNumber(data.authenticatedSessions)} signed in with PIN`,
          icon: Smartphone,
          color: '#2563eb',
          background: '#e0eaff',
        },
        {
          label: 'Completion Rate',
          value: `${data.completionRatePct}%`,
          hint: `${formatNumber(data.completedSessions)} completed`,
          icon: CheckCircle2,
          color: '#15803d',
          background: '#dcf5e7',
        },
        {
          label: 'Abandonment Rate',
          value: `${data.abandonmentRatePct}%`,
          hint: `${formatNumber(data.abandonedSessions)} abandoned`,
          icon: ShieldAlert,
          color: '#dc2626',
          background: '#fee2e2',
        },
        {
          label: 'Avg Response Time',
          value: `${Math.round(data.avgResponseMs)} ms`,
          hint: 'Target ≤ 3,000 ms per menu',
          icon: Clock3,
          color: '#2563eb',
          background: '#e0eaff',
        },
        {
          label: 'Failed PIN Logins',
          value: formatNumber(data.loginFailures),
          hint: `${formatNumber(data.pinLocks)} PIN lock(s)`,
          icon: LockKeyhole,
          color: '#9333ea',
          background: '#f3e8ff',
        },
        {
          label: 'Awaiting Approval',
          value: formatNumber(data.pendingReconciliationActions),
          hint: 'Reconciliation actions',
          icon: Clock3,
          color: '#ea580c',
          background: '#ffedd5',
        },
      ]
    : [];

  const authenticationRate =
    data && data.sessions > 0
      ? ((data.authenticatedSessions / data.sessions) * 100).toFixed(1)
      : '0.0';

  const pendingApprovals = data?.pendingReconciliationActions ?? 0;

  /** One point per calendar day in the selected range (zeros filled so the line is continuous). */
  const activitySeries = useMemo(() => {
    const byDay = new Map((data?.byDay ?? []).map((d) => [d.day.slice(0, 10), d]));
    const points: { date: string; label: string; sessions: number; phones: number }[] = [];
    const cursor = new Date(`${from}T00:00:00`);
    const end = new Date(`${to}T00:00:00`);
    while (cursor <= end) {
      const key = iso(cursor);
      const row = byDay.get(key);
      points.push({
        date: key,
        label: cursor.toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
        sessions: row?.sessions ?? 0,
        phones: row?.phones ?? 0,
      });
      cursor.setDate(cursor.getDate() + 1);
    }
    return points;
  }, [data, from, to]);

  const hasActivity = activitySeries.some((p) => p.sessions > 0);

  return (
    <div className="dashboard-page">
      <PageHead
        title="Dashboard"
        subtitle="Channel usage and repayment performance"
        actions={
          <div className="db-toolbar">
            <div className="db-presets">
              <CalendarDays size={17} className="db-calendar-icon" />

              <button
                type="button"
                className={period === 'TODAY' ? 'active' : ''}
                onClick={() => setPreset(1, 'TODAY')}
              >
                Today
              </button>

              <button
                type="button"
                className={period === '7D' ? 'active' : ''}
                onClick={() => setPreset(7, '7D')}
              >
                7D
              </button>

              <button
                type="button"
                className={period === '30D' ? 'active' : ''}
                onClick={() => setPreset(30, '30D')}
              >
                30D
              </button>

              <button
                type="button"
                className={period === '90D' ? 'active' : ''}
                onClick={() => setPreset(90, '90D')}
              >
                90D
              </button>
            </div>

            <div className="db-date-range">
              <input
                aria-label="Start date"
                type="date"
                value={from}
                max={to}
                onChange={(event) => handleFromChange(event.target.value)}
              />

              <ArrowRight size={16} />

              <input
                aria-label="End date"
                type="date"
                value={to}
                min={from}
                max={todayValue}
                onChange={(event) => handleToChange(event.target.value)}
              />

              <button
                type="button"
                className="db-refresh"
                title="Refresh dashboard"
                onClick={() => reload()}
                disabled={loading}
              >
                <RefreshCw
                  size={17}
                  className={loading ? 'db-spin' : ''}
                />
              </button>
            </div>
          </div>
        }
      />

      <Alert>{error}</Alert>

      {loading && !data && (
        <div className="db-loading">
          <RefreshCw size={20} className="db-spin" />
          <span>Loading dashboard statistics...</span>
        </div>
      )}

      {data && (
        <>
          {/* SIX COMPACT KPI CARDS */}

          <div className="db-metrics-grid">
            {metrics.map((metric) => (
              <MetricCard key={metric.label} metric={metric} />
            ))}
          </div>

          {/* MAIN DASHBOARD GRID */}

          <div className="db-content-grid">
            <div className="db-main-column">
              <Panel
                title="Repayments by status"
                icon={<CreditCard size={18} />}
                action={
                  <a href="/#/repayments" className="db-view-link">
                    View all
                  </a>
                }
              >
                {statuses.length === 0 ? (
                  <EmptyState
                    message="No repayment transactions"
                    detail="There are no repayment records for the selected period."
                  />
                ) : (
                  <div className="db-table-wrap">
                    <table className="db-table">
                      <thead>
                        <tr>
                          <th>Status</th>
                          <th className="num">Transactions</th>
                          <th className="num">Amount</th>
                        </tr>
                      </thead>

                      <tbody>
                        {statuses.map((status) => (
                          <tr key={status}>
                            <td>
                              <StatusBadge status={status} />
                            </td>
                            <td className="num">
                              {formatNumber(
                                data.repaymentCountByStatus[status]
                              )}
                            </td>
                            <td className="num db-amount">
                              {fmtMoney(
                                data.repaymentAmountByStatus[status],
                                currency
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>

                      <tfoot>
                        <tr>
                          <td>Total</td>
                          <td className="num">
                            {formatNumber(totalRepayments)}
                          </td>
                          <td className="num db-amount">
                            {fmtMoney(totalRepaymentAmount, currency)}
                          </td>
                        </tr>
                      </tfoot>
                    </table>
                  </div>
                )}
              </Panel>

              <Panel
                title="Session activity"
                subtitle="Daily USSD session volume during the selected period"
                icon={<Activity size={19} />}
                className="db-activity-panel"
                action={
                  <span className="db-chart-legend">
                    <span />
                    USSD sessions
                  </span>
                }
              >
                <div className="db-line-chart">
                  {hasActivity ? (
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart
                        data={activitySeries}
                        margin={{
                          top: 12,
                          right: 10,
                          left: -15,
                          bottom: 4,
                        }}
                      >
                        <CartesianGrid
                          stroke="#e8edf5"
                          strokeDasharray="3 3"
                          vertical
                        />
                        <XAxis
                          dataKey="label"
                          axisLine={false}
                          tickLine={false}
                          tick={{ fill: '#64748b', fontSize: 11 }}
                          interval="preserveStartEnd"
                          minTickGap={28}
                        />
                        <YAxis
                          allowDecimals={false}
                          axisLine={false}
                          tickLine={false}
                          tick={{ fill: '#64748b', fontSize: 11 }}
                          width={36}
                        />
                        <Tooltip
                          content={({ active, payload, label }) => {
                            if (!active || !payload?.length) return null;
                            const point = payload[0]?.payload as { date?: string; sessions?: number; phones?: number } | undefined;
                            const dateLabel = point?.date
                              ? new Date(`${point.date}T00:00:00`).toLocaleDateString(undefined, {
                                  weekday: 'short', month: 'short', day: 'numeric', year: 'numeric',
                                })
                              : String(label ?? '');
                            return (
                              <div className="db-chart-tooltip">
                                <strong>{dateLabel}</strong>
                                <span>{formatNumber(point?.sessions ?? 0)} sessions</span>
                                <span>{formatNumber(point?.phones ?? 0)} unique phones</span>
                              </div>
                            );
                          }}
                        />
                        <Line
                          type="monotone"
                          dataKey="sessions"
                          name="sessions"
                          stroke="#2563eb"
                          strokeWidth={2.5}
                          dot={{ r: 3, fill: '#2563eb' }}
                          activeDot={{ r: 6 }}
                        />
                      </LineChart>
                    </ResponsiveContainer>
                  ) : (
                    <div className="db-chart-empty">
                      <Activity size={22} />
                      <strong>No USSD sessions in this period</strong>
                      <span>Dial activity will appear here once customers use the USSD code.</span>
                    </div>
                  )}
                </div>
              </Panel>
            </div>

            <div className="db-side-column">
              <Panel
                title="Channel & integration health"
                icon={<Zap size={19} />}
                action={
                  <span className="db-checked-label">Live status</span>
                }
              >
                <EmptyState
                  message="Integration health is not connected"
                  // detail="Connect your gateway and core banking health checks to display live operational status here."
                />
              </Panel>

              <Panel
                title="Needs attention"
                icon={<Bell size={19} />}
                className="db-attention-panel"
                action={
                  <span className="db-attention-count">
                    {pendingApprovals}
                  </span>
                }
              >
                {pendingApprovals > 0 ? (
                  <div className="db-attention-item">
                    <div className="db-attention-icon">
                      <Clock3 size={20} />
                    </div>

                    <div className="db-attention-content">
                      <strong>
                        {pendingApprovals} pending approval
                        {pendingApprovals === 1 ? '' : 's'}
                      </strong>
                      <span>
                        {pendingApprovals === 1
                          ? 'There is 1 item awaiting approval.'
                          : `There are ${formatNumber(pendingApprovals)} items awaiting approval.`}
                      </span>
                    </div>

                    <ArrowRight size={18} />
                  </div>
                ) : (
                  <div className="db-no-attention">
                    <CheckCircle2 size={21} />
                    <div>
                      <strong>All caught up</strong>
                      <span>No pending reconciliation actions.</span>
                    </div>
                  </div>
                )}
              </Panel>

              <Panel
                title="Repayment overview"
                icon={<Wallet size={19} />}
                className="db-overview-panel"
              >
                <div className="db-overview-item">
                  <div className="db-overview-icon green">
                    <Wallet size={19} />
                  </div>
                  <div>
                    <span>Total repayments</span>
                    <strong>{formatNumber(totalRepayments)}</strong>
                    <small>Transactions across all statuses</small>
                  </div>
                </div>

                <div className="db-overview-item">
                  <div className="db-overview-icon blue">
                    <CreditCard size={19} />
                  </div>
                  <div>
                    <span>Total repayment amount</span>
                    <strong>
                      {fmtMoney(totalRepaymentAmount, currency)}
                    </strong>
                    <small>Combined amount across all statuses</small>
                  </div>
                </div>

                <div className="db-overview-item">
                  <div className="db-overview-icon purple">
                    <Activity size={19} />
                  </div>
                  <div>
                    <span>Session authentication</span>
                    <strong>{authenticationRate}%</strong>
                    <small>Authenticated USSD sessions</small>
                  </div>
                </div>
              </Panel>
            </div>
          </div>

          <div className="db-footer">
            <span>
              <Activity size={14} />
              Dashboard analytics
            </span>
            <span>
              Reporting period: {from} — {to}
            </span>
          </div>
        </>
      )}
    </div>
  );
}
