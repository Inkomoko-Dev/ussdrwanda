import { useState } from 'react';
import { api, qs } from '../api';
import { Alert, Card, PageHead, Pager, fmtDate, useLoad } from '../components/ui';
import type { AuditLog, Page } from '../types';

export default function Audit() {
  const [actor, setActor] = useState('');
  const [page, setPage] = useState(0);
  const { data, error } = useLoad(() => api.get<Page<AuditLog>>(`/api/admin/audit-logs${qs({ actor, page, size: 50 })}`), [actor, page]);
  return (
    <>
      <PageHead title="Audit log" subtitle="Append-only record of USSD, portal and system actions" />
      <Alert>{error}</Alert>
      <Card>
        <div className="toolbar">
          <input placeholder="Filter by actor (phone or username)" value={actor} onChange={(e) => { setActor(e.target.value); setPage(0); }} />
        </div>
        <div className="table-wrap">
          <table>
            <thead><tr><th>When</th><th>Actor</th><th>Channel</th><th>IP address</th><th>Country</th><th>Action</th><th>Resource</th><th>Result</th><th>Detail</th></tr></thead>
            <tbody>
              {data?.content.map((a) => (
                <tr key={a.id}>
                  <td>{fmtDate(a.loggedAt)}</td><td className="mono">{a.actor}</td><td>{a.channel}</td>
                  <td className="mono">{a.ipAddress ?? '—'}</td>
                  <td>{a.ipCountry ?? '—'}</td>
                  <td className="mono">{a.action}</td><td className="mono">{a.resource ?? ''}</td>
                  <td>{a.success ? <span className="badge ok">OK</span> : <span className="badge bad">Failed</span>}</td>
                  <td>{a.detail ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pager page={data ?? undefined} onPage={setPage} />
      </Card>
    </>
  );
}
