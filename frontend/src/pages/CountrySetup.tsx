import { useEffect, useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import AuthShell from '../components/AuthShell';
import CountryForm, { type CountryDraft } from '../components/CountryForm';
import { Alert } from '../components/ui';
import type { Country } from '../types';

/** Shown to the Super Administrator after the first sign-in, until a country is activated. */
export default function CountrySetup() {
  const { me, refresh, signOut } = useAuth();
  const [step, setStep] = useState(0);
  const [templates, setTemplates] = useState<Country[]>([]);
  const [country, setCountry] = useState<CountryDraft | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get<Country[]>('/api/admin/countries').then(setTemplates).catch((e) => setError(e.message));
  }, []);

  async function finish() {
    if (!country) return;
    setBusy(true);
    setError('');
    try {
      await api.put(`/api/admin/countries/${country.code}`, country);
      await api.post(`/api/admin/countries/${country.code}/activate`);
      await refresh();
    } catch (e) {
      setError((e as Error).message);
      setBusy(false);
    }
  }

  return (
    <AuthShell wide title="One last step: the country."
      text={`Choose the country this installation serves and confirm its settings. USSD code: *${me?.ussdServiceCode}#`}>
      <div className="steps">{[0, 1].map((i) => <span key={i} className={i <= step ? 'done' : ''} />)}</div>
      <Alert>{error}</Alert>
      {step === 0 && (
        <>
          <h2>Which country does this installation serve?</h2>
          <div className="country-pick">
            {templates.map((t) => (
              <button key={t.code} type="button" className={country?.code === t.code ? 'sel' : ''} onClick={() => setCountry({ ...t })}>
                <strong>{t.name}</strong>
                <span>+{t.dialCode} · {t.currency}</span>
              </button>
            ))}
          </div>
          <div className="modal-foot">
            <button className="btn ghost" onClick={signOut}>Sign out</button>
            <button className="btn" disabled={!country} onClick={() => setStep(1)}>Continue</button>
          </div>
        </>
      )}
      {step === 1 && country && (
        <>
          <h2>Confirm {country.name} settings</h2>
          <p className="sub">Values come from a template — confirm limits, helpline and languages with the country team.</p>
          <CountryForm value={country} onChange={setCountry} />
          <div className="modal-foot">
            <button className="btn secondary" onClick={() => setStep(0)}>Back</button>
            <button className="btn" disabled={busy || !country.languages.length} onClick={finish}>{busy ? 'Saving…' : 'Activate country'}</button>
          </div>
        </>
      )}
    </AuthShell>
  );
}
