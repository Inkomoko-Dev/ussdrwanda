
import { useState } from 'react';
import {
  Activity,
  ArrowDownToLine,
  CalendarDays,
  CheckCircle2,
  Clock3,
  Smartphone,
  Users,
  XCircle,
  RefreshCw,
  Radio,
  BarChart3,
} from 'lucide-react';

import { api, getToken, qs } from '../api';
import {
  Alert,
  Card,
  PageHead,
  Pager,
  StatusBadge,
  fmtDate,
  useLoad,
} from '../components/ui';

import type {
  MsisdnVisits,
  Page,
  SessionSummary,
  UssdSessionView,
} from '../types';

import '../sessions.css';

const iso = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

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

function fmtDuration(seconds: number) {
  if (seconds < 60) return `${seconds}s`;

  const minutes = Math.floor(seconds / 60);

  return minutes < 60
    ? `${minutes}m ${seconds % 60}s`
    : `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
}

async function download(path: string, fallbackName: string) {
  const token = getToken();

  const response = await fetch(path, {
    headers: token
      ? { Authorization: `Bearer ${token}` }
      : {},
  });

  if (!response.ok) {
    throw new Error(`Export failed (${response.status})`);
  }

  const name =
    /filename="([^"]+)"/.exec(
      response.headers.get('Content-Disposition') ?? ''
    )?.[1] ?? fallbackName;

  const url = URL.createObjectURL(await response.blob());
  const anchor = document.createElement('a');

  anchor.href = url;
  anchor.download = name;

  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();

  URL.revokeObjectURL(url);
}

function SummaryTile({
  label,
  value,
  hint,
  icon: Icon,
  color,
  background,
}: {
  label: string;
  value: string;
  hint: string;
  icon: typeof Activity;
  color: string;
  background: string;
}) {
  return (
    <article className="sessions-stat-card">
      <div
        className="sessions-stat-icon"
        style={{ color, background }}
      >
        <Icon size={19} strokeWidth={2} />
      </div>

      <div className="sessions-stat-label">{label}</div>
      <div className="sessions-stat-value">{value}</div>
      <div className="sessions-stat-hint">{hint}</div>
    </article>
  );
}

function EmptyMessage({
  title,
  description,
}: {
  title: string;
  description: string;
}) {
  return (
    <div className="sessions-empty">
      <div className="sessions-empty-icon">
        <Activity size={22} />
      </div>
      <strong>{title}</strong>
      <span>{description}</span>
    </div>
  );
}

export default function Sessions() {
  const today = new Date();

  const [from, setFrom] = useState(
    iso(new Date(today.getFullYear(), today.getMonth(), 1))
  );

  const [to, setTo] = useState(iso(today));
  const [msisdn, setMsisdn] = useState('');
  const [status, setStatus] = useState('');
  const [tab, setTab] = useState<Tab>('phones');
  const [page, setPage] = useState(0);
  const [exportError, setExportError] = useState('');
  const [exporting, setExporting] = useState(false);

  const resetPage = <T,>(setter: (value: T) => void) => (value: T) => {
    setter(value);
    setPage(0);
  };

  const summary = useLoad(
    () =>
      api.get<SessionSummary>(
        `/api/admin/ussd-sessions/summary${qs({ from, to })}`
      ),
    [from, to]
  );

  const phones = useLoad(
    () =>
      tab === 'phones'
        ? api.get<Page<MsisdnVisits>>(
            `/api/admin/ussd-sessions/by-msisdn${qs({
              from,
              to,
              msisdn,
              page,
              size: 50,
            })}`
          )
        : Promise.resolve(null),
    [tab, from, to, msisdn, page]
  );

  const log = useLoad(
    () =>
      tab === 'log'
        ? api.get<Page<UssdSessionView>>(
            `/api/admin/ussd-sessions${qs({
              from,
              to,
              msisdn,
              status,
              page,
              size: 50,
            })}`
          )
        : Promise.resolve(null),
    [tab, from, to, msisdn, status, page]
  );

  const data = summary.data;

  const count = (key: string) => data?.byStatus[key] ?? 0;

  const percentage = (value: number) =>
    data && data.sessions
      ? `${((value / data.sessions) * 100).toFixed(1)}%`
      : '—';

  const completed = count('COMPLETED') + count('USER_EXIT');

  const tiles = data
    ? [
        {
          label: 'Sessions',
          value: data.sessions.toLocaleString(),
          hint: `${data.screens.toLocaleString()} screens shown`,
          icon: Smartphone,
          color: '#2563eb',
          background: '#e0eaff',
        },
        {
          label: 'Unique phone numbers',
          value: data.uniquePhones.toLocaleString(),
          hint: data.uniquePhones
            ? `${(data.sessions / data.uniquePhones).toFixed(1)} sessions per phone`
            : 'No phone activity',
          icon: Users,
          color: '#7c3aed',
          background: '#f1eafe',
        },
        {
          label: 'Completed',
          value: completed.toLocaleString(),
          hint: `${percentage(completed)} of sessions`,
          icon: CheckCircle2,
          color: '#15803d',
          background: '#dcf5e7',
        },
        {
          label: 'Left / timed out',
          value: count('ABANDONED').toLocaleString(),
          hint: `${percentage(count('ABANDONED'))} of sessions`,
          icon: XCircle,
          color: '#dc2626',
          background: '#fee2e2',
        },
        {
          label: 'Not registered',
          value: count('NOT_REGISTERED').toLocaleString(),
          hint: 'Number not found in core banking',
          icon: Users,
          color: '#d97706',
          background: '#fef3c7',
        },
        {
          label: 'In progress',
          value: count('IN_PROGRESS').toLocaleString(),
          hint: 'Still open right now',
          icon: Clock3,
          color: '#0284c7',
          background: '#e0f2fe',
        },
      ]
    : [];

  const exportCsv = async () => {
    setExportError('');
    setExporting(true);

    try {
      if (tab === 'log') {
        await download(
          `/api/admin/ussd-sessions/export${qs({
            from,
            to,
            msisdn,
            status,
          })}`,
          'ussd-sessions.csv'
        );
      } else {
        await download(
          `/api/admin/ussd-sessions/by-msisdn/export${qs({
            from,
            to,
            msisdn,
          })}`,
          'ussd-visits-by-phone.csv'
        );
      }
    } catch (error) {
      setExportError((error as Error).message);
    } finally {
      setExporting(false);
    }
  };

  const showPhone = (phone: string) => {
    setMsisdn(phone);
    setStatus('');
    setTab('log');
    setPage(0);
  };

  const changeTab = (nextTab: Tab) => {
    setTab(nextTab);
    setPage(0);
    setExportError('');
  };

  const combinedError =
    summary.error ||
    phones.error ||
    log.error ||
    exportError;

  return (
    <div className="sessions-page">
      <PageHead
        title="USSD Sessions"
        subtitle="Monitor customer activity, phone visits and session outcomes"
        actions={
          <div className="sessions-date-toolbar">
            <div className="sessions-date-field">
              <CalendarDays size={16} />
              <label htmlFor="sessions-from">From</label>
              <input
                id="sessions-from"
                type="date"
                value={from}
                max={to}
                onChange={(event) =>
                  resetPage(setFrom)(event.target.value)
                }
              />
            </div>

            <div className="sessions-date-field">
              <label htmlFor="sessions-to">To</label>
              <input
                id="sessions-to"
                type="date"
                value={to}
                min={from}
                max={iso(today)}
                onChange={(event) =>
                  resetPage(setTo)(event.target.value)
                }
              />
            </div>
          </div>
        }
      />

      <Alert>{combinedError}</Alert>

      {/* SUMMARY CARDS */}

      {summary.loading && !data ? (
        <div className="sessions-loading">
          <RefreshCw size={19} className="sessions-spin" />
          <span>Loading session statistics...</span>
        </div>
      ) : (
        <div className="sessions-stats-grid">
          {tiles.map((tile) => (
            <SummaryTile
              key={tile.label}
              label={tile.label}
              value={tile.value}
              hint={tile.hint}
              icon={tile.icon}
              color={tile.color}
              background={tile.background}
            />
          ))}
        </div>
      )}

      {/* SESSION DATA */}

      <div className="sessions-content">
        <Card>
          <div className="sessions-card-content">
            <div className="sessions-section-heading">
              <div>
                <h2>Session activity</h2>
                <p>Explore customer visits and USSD session records.</p>
              </div>

              {tab !== 'daily' && (
                <button
                  type="button"
                  className="sessions-export-button"
                  onClick={exportCsv}
                  disabled={exporting}
                >
                  {exporting ? (
                    <RefreshCw size={16} className="sessions-spin" />
                  ) : (
                    <ArrowDownToLine size={16} />
                  )}
                  {exporting ? 'Exporting...' : 'Export CSV'}
                </button>
              )}
            </div>

            {/* TABS */}

            <div className="sessions-tabs">
              <button
                type="button"
                className={tab === 'phones' ? 'active' : ''}
                onClick={() => changeTab('phones')}
              >
                <Smartphone size={16} />
                Visits per phone
              </button>

              <button
                type="button"
                className={tab === 'log' ? 'active' : ''}
                onClick={() => changeTab('log')}
              >
                <Activity size={16} />
                Session log
              </button>

              <button
                type="button"
                className={tab === 'daily' ? 'active' : ''}
                onClick={() => changeTab('daily')}
              >
                <BarChart3 size={16} />
                Daily & network
              </button>
            </div>

            {/* FILTERS */}

            {tab !== 'daily' && (
              <div className="sessions-filters">
                <div className="sessions-search">
                  <Smartphone size={17} />
                  <input
                    type="text"
                    placeholder="Search phone number..."
                    value={msisdn}
                    onChange={(event) =>
                      resetPage(setMsisdn)(event.target.value)
                    }
                  />
                </div>

                {tab === 'log' && (
                  <select
                    value={status}
                    onChange={(event) =>
                      resetPage(setStatus)(event.target.value)
                    }
                    aria-label="Filter by session outcome"
                  >
                    {STATUS_OPTIONS.map(([value, label]) => (
                      <option key={value} value={value}>
                        {label}
                      </option>
                    ))}
                  </select>
                )}
              </div>
            )}

            {/* PHONE VISITS */}

            {tab === 'phones' && (
              <>
                {phones.loading ? (
                  <div className="sessions-loading">
                    <RefreshCw
                      size={19}
                      className="sessions-spin"
                    />
                    <span>Loading phone visits...</span>
                  </div>
                ) : (
                  <div className="sessions-table-container">
                    <div className="sessions-table-wrap">
                      <table className="sessions-table">
                        <thead>
                          <tr>
                            <th>Phone number</th>
                            <th>Customer</th>
                            <th className="num">Sessions</th>
                            <th className="num">Completed</th>
                            <th className="num">Left / timed out</th>
                            <th className="num">Not registered</th>
                            <th className="num">Other</th>
                            <th className="num">Screens</th>
                            <th>First visit</th>
                            <th>Last visit</th>
                          </tr>
                        </thead>

                        <tbody>
                          {phones.data?.content.length === 0 && (
                            <tr>
                              <td colSpan={10}>
                                <EmptyMessage
                                  title="No phone visits found"
                                  description="Try changing the date range or phone number filter."
                                />
                              </td>
                            </tr>
                          )}

                          {phones.data?.content.map((visit) => (
                            <tr key={visit.msisdn}>
                              <td className="sessions-phone">
                                <button
                                  type="button"
                                  onClick={() =>
                                    showPhone(visit.msisdn)
                                  }
                                  title="View this phone's session log"
                                >
                                  {visit.msisdn}
                                </button>
                              </td>

                              <td>{visit.clientName ?? '—'}</td>

                              <td className="num">
                                <strong>
                                  {visit.sessions.toLocaleString()}
                                </strong>
                              </td>

                              <td className="num">
                                {visit.completed.toLocaleString()}
                              </td>

                              <td className="num">
                                {visit.leftOrTimedOut.toLocaleString()}
                              </td>

                              <td className="num">
                                {visit.notRegistered.toLocaleString()}
                              </td>

                              <td className="num">
                                {visit.other.toLocaleString()}
                              </td>

                              <td className="num">
                                {visit.screens.toLocaleString()}
                              </td>

                              <td>{fmtDate(visit.firstVisit)}</td>
                              <td>{fmtDate(visit.lastVisit)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>

                    <Pager
                      page={phones.data ?? undefined}
                      onPage={setPage}
                    />
                  </div>
                )}
              </>
            )}

            {/* SESSION LOG */}

            {tab === 'log' && (
              <>
                {log.loading ? (
                  <div className="sessions-loading">
                    <RefreshCw
                      size={19}
                      className="sessions-spin"
                    />
                    <span>Loading session records...</span>
                  </div>
                ) : (
                  <div className="sessions-table-container">
                    <div className="sessions-table-wrap">
                      <table className="sessions-table">
                        <thead>
                          <tr>
                            <th>Started</th>
                            <th>Phone number</th>
                            <th>Customer</th>
                            <th>Network</th>
                            <th>Language</th>
                            <th>Outcome</th>
                            <th>Last screen</th>
                            <th className="num">Screens</th>
                            <th className="num">Duration</th>
                            <th>Ended</th>
                          </tr>
                        </thead>

                        <tbody>
                          {log.data?.content.length === 0 && (
                            <tr>
                              <td colSpan={10}>
                                <EmptyMessage
                                  title="No sessions found"
                                  description="No sessions match the selected filters."
                                />
                              </td>
                            </tr>
                          )}

                          {log.data?.content.map((record) => (
                            <tr key={record.id}>
                              <td>{fmtDate(record.startedAt)}</td>

                              <td className="sessions-mono">
                                {record.msisdn}
                              </td>

                              <td>{record.clientName ?? '—'}</td>
                              <td>{record.networkCode ?? '—'}</td>
                              <td>
                                {record.language?.toUpperCase() ?? '—'}
                              </td>

                              <td>
                                <StatusBadge status={record.status} />
                              </td>

                              <td>
                                {record.lastScreen}
                                {!record.authenticated && (
                                  <span className="sessions-sub">
                                    {' '}· not signed in
                                  </span>
                                )}
                              </td>

                              <td className="num">
                                {record.screens}
                              </td>

                              <td className="num">
                                {fmtDuration(record.durationSeconds)}
                              </td>

                              <td>{fmtDate(record.endedAt)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>

                    <Pager
                      page={log.data ?? undefined}
                      onPage={setPage}
                    />
                  </div>
                )}
              </>
            )}

            {/* DAILY AND NETWORK */}

            {tab === 'daily' && (
              <>
                {summary.loading && !data ? (
                  <div className="sessions-loading">
                    <RefreshCw
                      size={19}
                      className="sessions-spin"
                    />
                    <span>Loading daily statistics...</span>
                  </div>
                ) : data ? (
                  <div className="sessions-analytics-grid">
                    <section className="sessions-data-panel">
                      <div className="sessions-data-heading">
                        <CalendarDays size={17} />
                        <h3>Daily activity</h3>
                      </div>

                      <div className="sessions-table-wrap">
                        <table className="sessions-table">
                          <thead>
                            <tr>
                              <th>Day</th>
                              <th className="num">Sessions</th>
                              <th className="num">Unique phones</th>
                            </tr>
                          </thead>

                          <tbody>
                            {data.byDay.length === 0 && (
                              <tr>
                                <td colSpan={3}>
                                  No daily activity available.
                                </td>
                              </tr>
                            )}

                            {data.byDay.map((day) => (
                              <tr key={day.day}>
                                <td>{day.day}</td>
                                <td className="num">
                                  {day.sessions.toLocaleString()}
                                </td>
                                <td className="num">
                                  {day.phones.toLocaleString()}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </section>

                    <div className="sessions-analytics-side">
                      <section className="sessions-data-panel">
                        <div className="sessions-data-heading">
                          <Radio size={17} />
                          <h3>Network activity</h3>
                        </div>

                        <div className="sessions-table-wrap">
                          <table className="sessions-table">
                            <thead>
                              <tr>
                                <th>Network</th>
                                <th className="num">Sessions</th>
                                <th className="num">Phones</th>
                              </tr>
                            </thead>

                            <tbody>
                              {data.byNetwork.length === 0 && (
                                <tr>
                                  <td colSpan={3}>
                                    No network data available.
                                  </td>
                                </tr>
                              )}

                              {data.byNetwork.map((network) => (
                                <tr key={network.network}>
                                  <td>
                                    <strong>{network.network}</strong>
                                  </td>
                                  <td className="num">
                                    {network.sessions.toLocaleString()}
                                  </td>
                                  <td className="num">
                                    {network.phones.toLocaleString()}
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      </section>

                      <section className="sessions-data-panel">
                        <div className="sessions-data-heading">
                          <Activity size={17} />
                          <h3>Session outcomes</h3>
                        </div>

                        <div className="sessions-table-wrap">
                          <table className="sessions-table">
                            <thead>
                              <tr>
                                <th>Outcome</th>
                                <th className="num">Sessions</th>
                                <th className="num">Share</th>
                              </tr>
                            </thead>

                            <tbody>
                              {Object.entries(data.byStatus)
                                .sort((a, b) => b[1] - a[1])
                                .map(([key, value]) => (
                                  <tr key={key}>
                                    <td>
                                      <StatusBadge status={key} />
                                    </td>
                                    <td className="num">
                                      {value.toLocaleString()}
                                    </td>
                                    <td className="num">
                                      {percentage(value)}
                                    </td>
                                  </tr>
                                ))}
                            </tbody>
                          </table>
                        </div>
                      </section>
                    </div>
                  </div>
                ) : (
                  <EmptyMessage
                    title="No session summary"
                    description="There is no summary data available for this date range."
                  />
                )}
              </>
            )}
          </div>
        </Card>
      </div>

      <div className="sessions-footer">
        <span>
          <Activity size={14} />
          USSD session analytics
        </span>
        <span>
          Reporting period: {from} — {to}
        </span>
      </div>
    </div>
  );
}
