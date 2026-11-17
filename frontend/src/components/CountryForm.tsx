import { Link } from 'react-router-dom';
import { useLanguages } from '../languages';
import type { Country, Language } from '../types';
import { Field } from './ui';

export type CountryDraft = Omit<Country, 'active'> & { active?: boolean };

/** Editable country profile (setup wizard and Countries page). */
export default function CountryForm({ value, onChange, disabled }: {
  value: CountryDraft; onChange: (v: CountryDraft) => void; disabled?: boolean;
}) {
  const { languages, label, ready } = useLanguages();
  const pendingLang = languages.find((l) => l.active && !value.languages.includes(l.code) && !ready(l));
  const set = <K extends keyof CountryDraft>(k: K, v: CountryDraft[K]) => onChange({ ...value, [k]: v });
  const langs = value.languages;
  const toggle = (code: Language) =>
    set('languages', langs.includes(code) ? langs.filter((l) => l !== code) : [...langs, code]);
  const move = (i: number, d: -1 | 1) => {
    const next = [...langs];
    const j = i + d;
    if (j < 0 || j >= next.length) return;
    [next[i], next[j]] = [next[j], next[i]];
    set('languages', next);
  };

  return (
    <fieldset disabled={disabled} style={{ border: 0, padding: 0, margin: 0 }}>
      <div className="form-grid">
        <Field label="Country name"><input value={value.name} onChange={(e) => set('name', e.target.value)} /></Field>
        <Field label="USSD shortcode" help="What customers dial, e.g. 662*105 for *662*105#">
          <input value={value.ussdServiceCode ?? ''} placeholder="662*105"
            onChange={(e) => set('ussdServiceCode', e.target.value.replace(/[^0-9*#]/g, ''))} />
        </Field>
        <Field label="Dialing code" help="Without +, e.g. 250">
          <input value={value.dialCode} onChange={(e) => set('dialCode', e.target.value.replace(/\D/g, ''))} />
        </Field>
        <Field label="Local number length" help="Digits after the dialing code">
          <input type="number" min={6} max={12} value={value.nationalNumberLength}
            onChange={(e) => set('nationalNumberLength', Number(e.target.value))} />
        </Field>
        <Field label="Currency" help="ISO code shown on USSD screens">
          <input maxLength={3} value={value.currency} onChange={(e) => set('currency', e.target.value.toUpperCase())} />
        </Field>
        <Field label="Timezone"><input value={value.timezone} onChange={(e) => set('timezone', e.target.value)} /></Field>
        <Field label="Helpline" help="Phone number shown to clients, e.g. +250 788 123 456">
          <input value={value.helpline ?? ''} placeholder="+250 788 123 456" onChange={(e) => set('helpline', e.target.value)} />
        </Field>
        <Field label="USSD loan repayment" help="When off, customers do not see Repay Loan on USSD (main menu, loan submenu, next due).">
          <label className="check">
            <input type="checkbox" checked={!!value.repaymentEnabled}
              onChange={(e) => set('repaymentEnabled', e.target.checked)} />
            Enable Mobile Money repayment on USSD
          </label>
        </Field>
        <Field label={`Minimum repayment (${value.currency})`}>
          <input type="number" min={1} value={value.minRepayment} disabled={!value.repaymentEnabled}
            onChange={(e) => set('minRepayment', Number(e.target.value))} />
        </Field>
        <Field label={`Maximum repayment (${value.currency})`}>
          <input type="number" min={1} value={value.maxRepayment} disabled={!value.repaymentEnabled}
            onChange={(e) => set('maxRepayment', Number(e.target.value))} />
        </Field>
        <Field label="Mobile networks" help="Comma-separated, e.g. MTN, AIRTEL">
          <input value={value.mobileNetworks.join(', ')} disabled={!value.repaymentEnabled}
            onChange={(e) => set('mobileNetworks', e.target.value.split(',').map((s) => s.trim().toUpperCase()).filter(Boolean))} />
        </Field>
      </div>

      <Field label="USSD languages" help="Order = numbering on the language screen. One language skips that screen.">
        <div>
          {langs.map((code, i) => (
            <div className="lang-row" key={code}>
              <strong className="grow">{i + 1}. {label(code)}</strong>
              <button type="button" className="btn ghost small" onClick={() => move(i, -1)} disabled={i === 0}>↑</button>
              <button type="button" className="btn ghost small" onClick={() => move(i, 1)} disabled={i === langs.length - 1}>↓</button>
              <button type="button" className="btn ghost small" onClick={() => toggle(code)} disabled={langs.length === 1}>Remove</button>
            </div>
          ))}
          <div className="actions">
            {languages.filter((l) => l.active && !langs.includes(l.code)).map((l) => (
              ready(l)
                ? <button type="button" key={l.code} className="btn secondary small" onClick={() => toggle(l.code)}>+ {l.englishName}</button>
                : <span key={l.code} className="btn secondary small is-disabled"
                    title="Translate every USSD text in this language before offering it">
                    + {l.englishName} ({l.translated}/{l.total} translated)
                  </span>
            ))}
          </div>
          {pendingLang && (
            <span className="help">A language can be offered once all its texts are translated:{' '}
              <Link to={`/ussd-texts?translate=${pendingLang.code}`}>translate {pendingLang.englishName}</Link>.</span>
          )}
        </div>
      </Field>

    </fieldset>
  );
}
