import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import CountryForm, { type CountryDraft } from '../components/CountryForm';
import { Alert, Card, Modal, PageHead, fmtMoney, useLoad } from '../components/ui';
import { useLanguages } from '../languages';
import type { Country } from '../types';

export default function Countries() {
  const { can, refresh } = useAuth();
  const { label } = useLanguages();
  const { data, error, reload } = useLoad(() => api.get<Country[]>('/api/admin/countries'), []);
  const [edit, setEdit] = useState<CountryDraft | null>(null);
  const [err, setErr] = useState('');

  async function activate(c: Country) {
    if (!confirm(`Make ${c.name} the country served by this USSD deployment? Languages, currency and limits change immediately.`)) return;
    try { await api.post(`/api/admin/countries/${c.code}/activate`); reload(); refresh(); } catch (e) { setErr((e as Error).message); }
  }

  return (
    <>
      <PageHead title="Countries" subtitle="Country profiles — the active one drives the USSD service of this deployment" />
      <Alert>{error || err}</Alert>
      <Card>
        <div className="table-wrap">
          <table>
            <thead><tr><th>Country</th><th>USSD</th><th>Dial code</th><th>Currency</th><th>Languages</th><th>USSD repayment</th><th>Repayment limits</th><th></th></tr></thead>
            <tbody>
              {data?.map((c) => (
                <tr key={c.code}>
                  <td><strong>{c.name}</strong> {c.active && <span className="badge ok">Active</span>}</td>
                  <td className="mono">{c.ussdServiceCode ? `*${c.ussdServiceCode}#` : '—'}</td>
                  <td>+{c.dialCode}</td><td>{c.currency}</td>
                  <td>{c.languages.map(label).join(', ')}</td>
                  <td>{c.repaymentEnabled
                    ? <span className="badge ok">Enabled</span>
                    : <span className="badge neutral">Disabled</span>}</td>
                  <td>{c.repaymentEnabled ? `${fmtMoney(c.minRepayment)} – ${fmtMoney(c.maxRepayment)}` : '—'}</td>
                  <td>{can('COUNTRY_MANAGE') && <div className="actions">
                    <button className="btn ghost small" onClick={() => setEdit({ ...c })}>Edit</button>
                    {!c.active && <button className="btn secondary small" onClick={() => activate(c)}>Activate</button>}
                  </div>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
      {edit && <EditCountry value={edit} onClose={() => setEdit(null)} onDone={() => { setEdit(null); reload(); refresh(); }} />}
    </>
  );
}

function EditCountry({ value, onClose, onDone }: { value: CountryDraft; onClose: () => void; onDone: () => void }) {
  const [c, setC] = useState(value);
  const [error, setError] = useState('');
  async function save() {
    try { await api.put(`/api/admin/countries/${c.code}`, c); onDone(); } catch (e) { setError((e as Error).message); }
  }
  return (
    <Modal title={`Edit ${value.name}`} onClose={onClose} wide
      footer={<><button className="btn secondary" onClick={onClose}>Cancel</button><button className="btn" onClick={save}>Save</button></>}>
      <Alert>{error}</Alert>
      <CountryForm value={c} onChange={setC} />
    </Modal>
  );
}
