import { useEffect, useState } from 'react';
import { api, qs } from '../api';
import { useAuth } from '../auth';
import { Alert, Card, Field, Modal, PageHead, Pager, StatusBadge, fmtDate, fmtMoney, useLoad } from '../components/ui';
import { useLanguages } from '../languages';
import type { Customer, CustomerDetails, CustomerLookup, Page, PinStatus } from '../types';

export default function Customers() {
  const { can, me } = useAuth();
  const { label: languageLabel } = useLanguages();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<PinStatus | ''>('');
  const [page, setPage] = useState(0);
  const [onboarding, setOnboarding] = useState(false);
  const [editing, setEditing] = useState<Customer | null>(null);
  const [notice, setNotice] = useState('');
  const [actionError, setActionError] = useState('');
  const { data, error, reload } = useLoad(
    () => can('CUSTOMER_VIEW')
      ? api.get<Page<Customer>>(`/api/admin/customers${qs({ q, status, page, size: 25 })}`)
      : Promise.resolve(null),
    [q, status, page],
  );

  async function act(c: Customer, path: string, label: string) {
    if (!confirm(`${label} for ${c.clientName ?? c.msisdn}?`)) return;
    setActionError('');
    try {
      await api.post(`/api/admin/customers/${c.msisdn}/${path}`);
      setNotice(`${label}: done for ${c.msisdn}`);
      reload();
    } catch (e) {
      setActionError((e as Error).message);
    }
  }

  return (
    <>
      <PageHead title="Customers" subtitle="Clients registered for the USSD channel"
        actions={can('CUSTOMER_ONBOARD') && <button className="btn" onClick={() => setOnboarding(true)}>Onboard customer</button>} />
      <Alert kind="success">{notice}</Alert>
      <Alert>{error || actionError}</Alert>
      {can('CUSTOMER_VIEW') && (
        <Card>
          <div className="toolbar">
            <input placeholder="Search phone or name" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
            <select value={status} onChange={(e) => { setStatus(e.target.value as PinStatus | ''); setPage(0); }}>
              <option value="">All statuses</option>
              <option value="ACTIVE">Active</option>
              <option value="NOT_SET">PIN not set</option>
              <option value="LOCKED">Locked</option>
              <option value="DISABLED">Disabled</option>
            </select>
          </div>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Phone</th><th>Name</th><th>Status</th><th>Language</th><th>Terms</th><th>Registered via</th><th>Last login</th><th></th></tr></thead>
              <tbody>
                {data?.content.length === 0 && <tr><td colSpan={8} className="empty">No customers found.</td></tr>}
                {data?.content.map((c) => (
                  <tr key={c.msisdn}>
                    <td className="mono">{c.msisdn}</td>
                    <td>{c.clientName ?? '—'}</td>
                    <td><StatusBadge status={c.status} /></td>
                    <td>{languageLabel(c.language)}</td>
                    <td><StatusBadge status={c.termsAccepted ? 'ACCEPTED' : 'NOT_ACCEPTED'} /></td>
                    <td>{c.registrationChannel === 'PORTAL' ? `Portal · ${c.onboardedBy ?? ''}` : c.registrationChannel === 'SELF' ? 'Self (first dial)' : '—'}</td>
                    <td>{fmtDate(c.lastLoginAt)}</td>
                    <td>
                      <div className="actions">
                        <button className="btn secondary small" onClick={() => setEditing(c)}>Details</button>
                        {c.status === 'LOCKED' && can('CUSTOMER_PIN_UNLOCK') && <button className="btn secondary small" onClick={() => act(c, 'unlock', 'Unlock PIN')}>Unlock</button>}
                        {c.status !== 'DISABLED' && can('CUSTOMER_PIN_RESET') && <button className="btn secondary small" onClick={() => act(c, 'reset-pin', 'Reset PIN')}>Reset PIN</button>}
                        {can('CUSTOMER_DISABLE') && (c.status === 'DISABLED'
                          ? <button className="btn secondary small" onClick={() => act(c, 'enable', 'Enable USSD access')}>Enable</button>
                          : <button className="btn ghost small" onClick={() => act(c, 'disable', 'Disable USSD access')}>Disable</button>)}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager page={data ?? undefined} onPage={setPage} />
        </Card>
      )}
      {editing && (
        <DetailsModal customer={editing} canEdit={can('CUSTOMER_ONBOARD')}
          onClose={() => setEditing(null)}
          onDone={(_c, message) => { setEditing(null); setNotice(message); reload(); }} />
      )}
      {onboarding && (
        <OnboardModal currency={me?.country?.currency ?? ''}
          onClose={() => setOnboarding(false)}
          onDone={(c) => { setOnboarding(false); setNotice(`${c.clientName ?? c.msisdn} onboarded — they can now dial and create a PIN.`); reload(); }} />
      )}
    </>
  );
}

function DetailsModal({ customer, canEdit, onClose, onDone }: {
  customer: Customer; canEdit: boolean; onClose: () => void; onDone: (c: Customer, message: string) => void;
}) {
  const [details, setDetails] = useState<CustomerDetails | null>(null);
  const [name, setName] = useState(customer.clientName ?? '');
  const [phone, setPhone] = useState(customer.msisdn);
  const [email, setEmail] = useState(customer.email ?? '');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get<CustomerDetails>(`/api/admin/customers/${customer.msisdn}/details`)
      .then((d) => {
        setDetails(d);
        setName(d.customer.clientName ?? d.coreName ?? '');
        setPhone(d.customer.msisdn);
        setEmail(d.customer.email ?? d.coreEmail ?? '');
      })
      .catch((e) => setError((e as Error).message));
  }, [customer.msisdn]);

  const coreDiffers = !!details?.inCoreBanking && (
    (details.coreName ?? '') !== (details.customer.clientName ?? '')
    || (details.corePhone ?? '') !== details.customer.msisdn
    || (details.coreEmail ?? '') !== (details.customer.email ?? '')
  );

  async function save() {
    setBusy(true); setError('');
    try {
      const saved = await api.put<Customer>(`/api/admin/customers/${customer.msisdn}`, { name, phone, email });
      onDone(saved, `${saved.clientName ?? saved.msisdn} updated${details?.inCoreBanking ? ' in the portal and in core banking' : ''}.`);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function sync() {
    setBusy(true); setError('');
    try {
      const saved = await api.post<Customer>(`/api/admin/customers/${customer.msisdn}/sync`);
      onDone(saved, `${saved.clientName ?? saved.msisdn} synced from core banking.`);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title="Customer details" onClose={onClose}
      footer={<>
        <button className="btn secondary" onClick={onClose}>Cancel</button>
        {canEdit && <button className="btn secondary" disabled={busy || !details?.inCoreBanking} onClick={sync}>Sync from core banking</button>}
        {canEdit && <button className="btn" disabled={busy || !name.trim() || !phone.trim()} onClick={save}>Save</button>}
      </>}>
      <Alert>{error}</Alert>
      {details && !details.inCoreBanking && <Alert kind="info">This number is not linked to a core-banking client. Saving updates the portal record only.</Alert>}
      {coreDiffers && (
        <Alert kind="info">
          Core banking has {details?.coreName || 'no name'}
          {details?.corePhone ? `, phone ${details.corePhone}` : ''}
          {details?.coreEmail ? `, ${details.coreEmail}` : ''}. Sync to copy those onto this customer.
        </Alert>
      )}
      <Field label="Terms & conditions"><span>{customer.termsAccepted ? 'Accepted' : 'Not yet'}</span></Field>
      <Field label="Name"><input value={name} disabled={!canEdit} onChange={(e) => setName(e.target.value)} /></Field>
      <Field label="Phone" help="Used to sign in on USSD"><input value={phone} disabled={!canEdit} onChange={(e) => setPhone(e.target.value)} /></Field>
      <Field label="Email" help="Example: grace@inkomoko.org"><input type="email" value={email} disabled={!canEdit} placeholder="grace@inkomoko.org" onChange={(e) => setEmail(e.target.value)} /></Field>
    </Modal>
  );
}


function OnboardModal({ onClose, onDone, currency }: {
  onClose: () => void; onDone: (c: Customer) => void; currency: string;
}) {
  const [msisdn, setMsisdn] = useState('');
  const [lookup, setLookup] = useState<CustomerLookup | null>(null);
  const [sendSms, setSendSms] = useState(true);
  const [verified, setVerified] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function find() {
    setBusy(true); setError(''); setLookup(null); setVerified(false);
    try {
      setLookup(await api.get<CustomerLookup>(`/api/admin/customers/lookup${qs({ msisdn })}`));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function onboard() {
    setBusy(true); setError('');
    try {
      onDone(await api.post<Customer>('/api/admin/customers', { msisdn: lookup!.msisdn, sendSms }));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const reg = lookup?.registration;
  const canOnboard = lookup?.foundInCoreBanking && lookup.client?.active && verified && reg?.status !== 'ACTIVE' && reg?.status !== 'DISABLED';

  return (
    <Modal title="Onboard customer" onClose={onClose} wide
      footer={<>
        <button className="btn secondary" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!canOnboard || busy} onClick={onboard}>Onboard to USSD</button>
      </>}>
      <p className="sub">Find the client in core banking by the phone number registered on their loan file, verify their identity, then activate USSD access. The client creates their own PIN on first dial.</p>
      <Alert>{error}</Alert>
      <div className="toolbar">
        <input placeholder="Phone, e.g. 0788123456" value={msisdn} onChange={(e) => setMsisdn(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && msisdn && find()} />
        <button className="btn secondary" disabled={!msisdn || busy} onClick={find}>Find client</button>
      </div>

      {lookup && !lookup.foundInCoreBanking && (
        <Alert kind="info">
          No core-banking client uses {lookup.msisdn}.
        </Alert>
      )}

      {lookup?.client && (
        <>
          <div className="grid two" style={{ marginBottom: 12 }}>
            <Field label="Client"><strong>{lookup.client.displayName}</strong></Field>
            <Field label="Core-banking status">
              <span>{lookup.client.active ? <StatusBadge status="ACTIVE" /> : <StatusBadge status="DISABLED" />} Client #{lookup.client.clientId}</span>
            </Field>
            <Field label="USSD registration">
              <span>{reg ? <StatusBadge status={reg.status} /> : 'Not registered'}</span>
            </Field>
          </div>
          {lookup.loans.length > 0 && (
            <div className="table-wrap" style={{ marginBottom: 12 }}>
              <table>
                <thead><tr><th>Loan</th><th>Product</th><th>Status</th><th className="num">Outstanding</th></tr></thead>
                <tbody>
                  {lookup.loans.map((l) => (
                    <tr key={l.loanId}><td className="mono">{l.accountNo}</td><td>{l.productName}</td>
                      <td><StatusBadge status={l.status} /></td><td className="num">{fmtMoney(l.totalOutstanding, currency)}</td></tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {reg?.status === 'ACTIVE' && <Alert kind="info">Already registered with a PIN. Use Reset PIN in the customer list if needed.</Alert>}
          {reg?.status === 'DISABLED' && <Alert kind="info">USSD access is disabled for this number — enable it from the customer list.</Alert>}
          <label className="check">
            <input type="checkbox" checked={verified} onChange={(e) => setVerified(e.target.checked)} />
            I have verified the client's identity (national ID / refugee ID matches the loan file)
          </label>
          <label className="check">
            <input type="checkbox" checked={sendSms} onChange={(e) => setSendSms(e.target.checked)} />
            Send SMS invitation with the USSD code
          </label>
        </>
      )}
    </Modal>
  );
}
