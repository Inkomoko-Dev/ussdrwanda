import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { Alert, Card, Field, Modal, PageHead } from '../components/ui';
import { refreshLanguages, useLanguages } from '../languages';
import type { Charset, LanguageView } from '../types';

const CHARSETS: Record<Charset, string> = {
  GSM7: 'GSM-7: Latin letters without most accents · 182 characters per screen',
  UCS2: 'UCS-2: any script (Arabic, Amharic, accents) · about 80 characters per screen',
};

export default function Languages() {
  const { can } = useAuth();
  const { languages, error } = useLanguages();
  const navigate = useNavigate();
  const [edit, setEdit] = useState<LanguageView | 'new' | null>(null);
  const [msg, setMsg] = useState('');
  const manage = can('LANGUAGE_MANAGE');

  async function remove(l: LanguageView) {
    setMsg('');
    try {
      await api.del(`/api/admin/languages/${l.code}`);
      await refreshLanguages();
    } catch (e) { setMsg((e as Error).message); }
  }

  return (
    <>
      <PageHead title="Languages" subtitle="Languages customers can choose on USSD"
        actions={manage && <button className="btn" onClick={() => setEdit('new')}>Add language</button>} />
      <Alert>{error || msg}</Alert>

      <Card title="How a new language reaches customers">
        <ol className="steps">
          <li><strong>Add it here</strong> with its code and the name customers will see on the language screen.</li>
          <li><strong>Translate every text</strong> under Content → USSD &amp; SMS texts (the Translate button below opens it).</li>
          <li><strong>Offer it in a country</strong> under Administration → Countries. This is only possible once all texts are translated, so customers never see a half-translated menu.</li>
        </ol>
      </Card>

      <Card>
        <div className="table-wrap">
          <table>
            <thead><tr><th>Language</th><th>Code</th><th>Character set</th><th>Translation</th><th>Offered in</th><th>Status</th><th></th></tr></thead>
            <tbody>{languages.map((l) => {
              const pct = l.total ? Math.round((l.translated / l.total) * 100) : 0;
              return (
                <tr key={l.code}>
                  <td><strong>{l.englishName}</strong><div className="sub-inline">On USSD: {l.nativeName}</div></td>
                  <td className="mono">{l.code}</td>
                  <td title={CHARSETS[l.charset]}>{l.charset === 'GSM7' ? 'GSM-7' : 'UCS-2'}</td>
                  <td style={{ minWidth: 170 }}>
                    <div className="progress small"><span style={{ width: `${pct}%` }} /></div>
                    <span className="sub-inline">{l.translated}/{l.total} texts{l.builtIn ? ' · shipped' : ''}</span>
                  </td>
                  <td>{l.usedBy.length ? l.usedBy.join(', ') : '—'}</td>
                  <td>
                    {l.active ? <span className="badge ok">Active</span> : <span className="badge neutral">Inactive</span>}
                    {l.builtIn && <span className="badge info" style={{ marginLeft: 6 }}>Built-in</span>}
                  </td>
                  <td className="row-actions">
                    {can('MESSAGE_VIEW') && l.code !== 'en' && (
                      <button className="btn secondary small" onClick={() => navigate(`/ussd-texts?translate=${l.code}`)}>
                        {l.translated < l.total ? 'Translate' : 'Edit texts'}
                      </button>
                    )}
                    {manage && <button className="btn ghost small" onClick={() => setEdit(l)}>Edit</button>}
                    {manage && !l.builtIn && l.usedBy.length === 0 && (
                      <button className="btn ghost small" onClick={() => {
                        if (window.confirm(`Delete ${l.englishName} and its ${l.translated} translated texts?`)) remove(l);
                      }}>Delete</button>
                    )}
                  </td>
                </tr>
              );
            })}</tbody>
          </table>
        </div>
      </Card>
      {edit && (
        <LanguageModal language={edit === 'new' ? null : edit} onClose={() => setEdit(null)}
          onDone={async (saved, created) => {
            setEdit(null);
            await refreshLanguages();
            if (created && can('MESSAGE_VIEW')) navigate(`/ussd-texts?translate=${saved.code}`);
          }} />
      )}
    </>
  );
}

function LanguageModal({ language, onClose, onDone }: {
  language: LanguageView | null; onClose: () => void; onDone: (l: LanguageView, created: boolean) => void;
}) {
  const [f, setF] = useState({
    code: language?.code ?? '', nativeName: language?.nativeName ?? '', englishName: language?.englishName ?? '',
    charset: (language?.charset ?? 'GSM7') as Charset, active: language?.active ?? true,
  });
  const [error, setError] = useState('');
  async function save() {
    setError('');
    try {
      const saved = language
        ? await api.put<LanguageView>(`/api/admin/languages/${language.code}`,
          { nativeName: f.nativeName, englishName: f.englishName, charset: f.charset, active: f.active })
        : await api.post<LanguageView>('/api/admin/languages',
          { code: f.code, nativeName: f.nativeName, englishName: f.englishName, charset: f.charset });
      onDone(saved, !language);
    } catch (e) { setError((e as Error).message); }
  }
  return (
    <Modal title={language ? `Edit ${language.englishName}` : 'Add language'} onClose={onClose}
      footer={<><button className="btn secondary" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!f.code || !f.nativeName || !f.englishName} onClick={save}>
          {language ? 'Save' : 'Add and start translating'}</button></>}>
      <Alert>{error}</Alert>
      <div className="form-grid">
        <Field label="Code" help="ISO 639, e.g. sw (Swahili), ar (Arabic), am (Amharic)">
          <input maxLength={3} value={f.code} disabled={!!language}
            onChange={(e) => setF({ ...f, code: e.target.value.toLowerCase().replace(/[^a-z]/g, '') })} />
        </Field>
        <Field label="Name in English" help="Shown in this portal">
          <input maxLength={60} value={f.englishName} onChange={(e) => setF({ ...f, englishName: e.target.value })} />
        </Field>
      </div>
      <Field label="Name on USSD" help="As customers know it, e.g. Kiswahili. Shown on the language choice screen.">
        <input maxLength={30} value={f.nativeName} onChange={(e) => setF({ ...f, nativeName: e.target.value })} />
      </Field>
      <Field label="Character set" help="Decides how much text fits on one screen">
        <select value={f.charset} disabled={language?.builtIn} onChange={(e) => setF({ ...f, charset: e.target.value as Charset })}>
          {(Object.keys(CHARSETS) as Charset[]).map((c) => <option key={c} value={c}>{CHARSETS[c]}</option>)}
        </select>
      </Field>
      {language && (
        <label className="check"><input type="checkbox" checked={f.active} onChange={(e) => setF({ ...f, active: e.target.checked })} /> Active (can be offered in a country)</label>
      )}
    </Modal>
  );
}
