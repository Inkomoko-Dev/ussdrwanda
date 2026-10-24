import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { Alert, Card, Field, Modal, PageHead, fmtDate, useLoad } from '../components/ui';
import { refreshLanguages, useLanguages } from '../languages';
import type { Language, MessageCatalog, MessageCheck, MessageEntry, MessageHistory } from '../types';

const GROUPS: Record<string, string> = {
  language: 'Language screen', consent: 'Terms & consent', menu: 'Main menu', nav: 'Navigation',
  common: 'General', session: 'Session', client: 'Customer checks', pin: 'PIN', loan: 'Loans & status',
  balance: '1. Check balance', statement: '3. Mini statement', repay: '4. Repay loan', help: '5. Help', sms: 'SMS messages',
};
const PAGE_SIZE_OPTIONS = [10, 25, 50, 100];
const fill = (t: string, v: Record<string, string>) => t.replace(/\{([a-zA-Z][a-zA-Z0-9_]*)\}/g, (m, k) => v[k] ?? m);

export default function UssdTexts() {
  const { me, can } = useAuth();
  const { languages, label, find } = useLanguages();
  const [params, setParams] = useSearchParams();
  const translate = params.get('translate') ?? '';
  const [scopePick, setScope] = useState(me?.country?.code ?? '*');
  const scope = translate ? '*' : scopePick;
  const [group, setGroup] = useState('');
  const [q, setQ] = useState('');
  const [onlyCustom, setOnlyCustom] = useState(false);
  const [onlyMissing, setOnlyMissing] = useState(true);
  const [open, setOpen] = useState<string | null>(null);
  // Pagination state
  const [pageSize, setPageSize] = useState(10);
  const [page, setPage] = useState(1);

  const { data, error, reload } = useLoad(() => api.get<MessageCatalog>(
    `/api/admin/messages?country=${encodeURIComponent(scope)}${translate ? `&languages=${encodeURIComponent(translate)}` : ''}`),
  [scope, translate]);
  // Reset pagination when the selected filters change.
  useEffect(() => {setPage(1);}, [scope, translate, group, q, onlyCustom, onlyMissing, pageSize]);

  const setTranslate = (code: string, keepOpen = false) => {
    const next = new URLSearchParams(params);
    if (code) next.set('translate', code); else next.delete('translate');
    setParams(next, { replace: true });
    if (!keepOpen) setOpen(null);
  };

  // Apply filters before pagination.
  const rows = useMemo(() => (data?.messages ?? []).filter((m) => {
    if (group && m.group !== group) return false;
    const texts = Object.values(m.texts);
    if (translate && onlyMissing && m.texts[translate]?.translated) return false;
    if (!translate && onlyCustom && !texts.some((t) => t?.override || t?.inherited)) return false;
    if (!q) return true;
    const s = q.toLowerCase();
    return m.key.toLowerCase().includes(s) || texts.some((t) => t?.effective?.toLowerCase().includes(s));
  }), [data, group, q, onlyCustom, onlyMissing, translate]);
  // Calculate pagination.
  const totalRows = rows.length;
  const totalPages = Math.max(1, Math.ceil(totalRows / pageSize));
  const currentPage = Math.min(page, totalPages);
  const startIndex = (currentPage - 1) * pageSize;
  const endIndex = Math.min(startIndex + pageSize, totalRows);
  // Only render the records for the current page.
  const visibleRows = useMemo(() => rows.slice(startIndex, startIndex + pageSize),[rows, startIndex, pageSize]);
  // Display at most five page-number buttons.
  const pageNumbers = useMemo(() => {const start = Math.max(1,Math.min(currentPage - 2, totalPages - 4));
    const end = Math.min(totalPages, start + 4);
    return Array.from({ length: end - start + 1 },(_, i) => start + i,);}, [currentPage, totalPages]);

  const groups = useMemo(() => [...new Set((data?.messages ?? []).map((m) => m.group))],[data]);

  const entry = data?.messages.find((m) => m.key === open);
  const target = translate ? find(translate) : undefined;
  const done = translate ? (data?.messages ?? []).filter((m) => m.texts[translate]?.translated).length : 0;
  const total = data?.messages.length ?? 0;
  const incomplete = languages.filter((l) => l.translated < l.total);

  const changed = () => { reload(); refreshLanguages(); };
  const nextMissing = (after: string) => {
    const list = data?.messages ?? [];
    const i = list.findIndex((m) => m.key === after);
    const next = [...list.slice(i + 1), ...list.slice(0, i)].find((m) => !m.texts[translate]?.translated);
    setOpen(next ? next.key : null);
  };

  return (
    <>
      <PageHead title="USSD & SMS texts"
        subtitle="Every screen customers see, per language. Edits go live within a minute; the shipped wording stays available as the default." />
      <Alert>{error}</Alert>

      {translate ? (
        <div className="translate-banner">
          <div className="grow">
            <strong>Translating {target?.englishName ?? translate}</strong>
            <span className="sub-inline">{' · '}{target?.charset === 'UCS2' ? 'UCS-2: any script, about 80 characters per screen' : 'GSM-7: no accents, 182 characters per screen'}{' · saved for all countries'}</span>
            <div className="progress" aria-label={`${done} of ${total} translated`}><span style={{ width: `${total ? (done / total) * 100 : 0}%` }} /></div>
            <span className="sub-inline">{done} of {total} texts translated.{' '}
              {done >= total && total > 0 ? 'Complete: it can now be offered in a country (Administration → Countries).'
                : 'A country can offer this language once every text is translated; until then nothing changes for customers.'}</span>
          </div>
          <button className="btn secondary small" onClick={() => setTranslate('')}>Done</button>
        </div>
      ) : (incomplete.length > 0 && can('MESSAGE_MANAGE') && (
        <Alert kind="info">
          {incomplete.map((l) => `${l.englishName} (${l.translated}/${l.total})`).join(', ')}{' '} {incomplete.length === 1 ? 'is' : 'are'} not fully translated yet.{' '}
          <button className="linklike" onClick={() => setTranslate(incomplete[0].code)}>Translate {incomplete[0].englishName}</button>
        </Alert>
        )
      )}

      <div className="toolbar">
        {!translate && (
          <select value={scopePick} onChange={(e) => setScope(e.target.value)} aria-label="Applies to">
            {me?.country && <option value={me.country.code}>{me.country.name} only</option>}
            <option value="*">All countries</option>
          </select>
        )}
        <select value={translate} onChange={(e) => setTranslate(e.target.value)} aria-label="Translate a language">
          <option value="">{translate ? 'Stop translating' : 'Add / edit a translation…'}</option>
          {languages.filter((l) => l.code !== 'en').map((l) => (
            <option key={l.code} value={l.code}>
              Translate {l.englishName} ({l.translated}/{l.total}){l.active ? '' : ' · inactive'}
            </option>
          ))}
        </select>
        <select value={group} onChange={(e) => setGroup(e.target.value)} aria-label="Screen group">
          <option value="">All screens</option>
          {groups.map((g) => <option key={g} value={g}>{GROUPS[g] ?? g}</option>)}
        </select>
        <input type="search" placeholder="Search text or key" value={q} onChange={(e) => setQ(e.target.value)} />
        {translate
          ? <label className="check"><input type="checkbox" checked={onlyMissing} onChange={(e) => setOnlyMissing(e.target.checked)} /> Not translated only</label>
          : <label className="check"><input type="checkbox" checked={onlyCustom} onChange={(e) => setOnlyCustom(e.target.checked)} /> Customised only</label>}
      </div>
      <Card>
        {!data ? <div className="empty">Loading…</div> : rows.length === 0 ? (
          <div className="empty">{translate && onlyMissing ? 'Everything is translated.' : 'No texts match.'}</div>
        ) : (
        <>
      <div className="table-wrap">
       <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 8, marginBottom: 12 }}>
         <label htmlFor="rows-per-page">Rows per page</label>
         <select id="rows-per-page" aria-label="Rows per page" value={pageSize} onChange={(e) => setPageSize(Number(e.target.value))}
          style={{ width: 75, padding: '5px 8px', border: '1px solid #d1d5db', borderRadius: 6, background: '#fff', cursor: 'pointer',}}>
         {PAGE_SIZE_OPTIONS.map((size) => (<option key={size} value={size}>{size}</option>))}
         </select>
        </div>
            <table className="texts-table">
              <thead><tr><th>Screen</th>{data.languages.map((l) => <th key={l}>{label(l)}{translate && l === 'en' ? ' (reference)' : ''}</th>)}</tr></thead>
              <tbody>{visibleRows.map((m) => (
                <tr key={m.key} className="clickable" onClick={() => setOpen(m.key)}>
                  <td className="text-key"><strong>{GROUPS[m.group] ?? m.group}</strong><code>{m.key}</code></td>
                  {data.languages.map((l) => {
                    const t = m.texts[l];
                    if (!t) return <td key={l} />;
                    if (!t.translated) return <td key={l}><span className="badge warn">Not translated</span></td>;
                    const over = t.length > t.maxLength;
                    return (
                      <td key={l}>
                        <div className="ussd-text">{t.effective}</div>
                        <div className="text-meta">
                          <span className={over ? 'len over' : 'len'}>{t.length}/{t.maxLength}</span>
                          {t.override ? <span className="badge info">{t.defaultText ? 'Custom' : 'Translated'}</span>
                            : t.inherited ? <span className="badge neutral">All countries</span> : null}
                        </div>
                      </td>
                    );
                  })}
                </tr>
              ))}</tbody>
            </table>
          </div>
      {/* Pagination controls */}
      <div className="toolbar" style={{ justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', marginTop: 16 }}>
        <span className="sub-inline">Showing {startIndex + 1}–{endIndex} of {totalRows}</span>

        <div style={{ display: 'flex', alignItems: 'center', gap: 4, flexWrap: 'wrap' }} aria-label="Pagination">
          <button type="button" className="btn secondary small" disabled={currentPage === 1} onClick={() => setPage((p) => Math.max(1, p - 1))}>Previous</button>
          {pageNumbers[0] > 1 && (
            <>
              <button type="button" className="btn secondary small" onClick={() => setPage(1)}>1</button>
              {pageNumbers[0] > 2 && <span>…</span>}
            </>
          )}

          {pageNumbers.map((pageNumber) => (
            <button type="button" key={pageNumber} className={pageNumber === currentPage ? 'btn small' : 'btn secondary small'} aria-current={pageNumber === currentPage ? 'page' : undefined} onClick={() => setPage(pageNumber)}>{pageNumber}</button>
          ))}
          {pageNumbers[pageNumbers.length - 1] < totalPages && (
            <>
              {pageNumbers[pageNumbers.length - 1] < totalPages - 1 && <span>…</span>}
              <button type="button" className="btn secondary small" onClick={() => setPage(totalPages)}>{totalPages}</button>
            </>
          )}

          <button type="button" className="btn secondary small" disabled={currentPage === totalPages} onClick={() => setPage((p) => Math.min(totalPages, p + 1))}>Next</button>
        </div>
      </div>
    </>
  )}
</Card>
      {entry && data && (
        <EditModal entry={entry} catalog={data} translate={translate} onClose={() => setOpen(null)} onChanged={changed}
          onTranslate={(code) => setTranslate(code, true)}
          onNext={translate ? () => nextMissing(entry.key) : undefined} />
      )}
    </>
  );
}

function EditModal({ entry, catalog, translate, onClose, onChanged, onTranslate, onNext }: {
  entry: MessageEntry; catalog: MessageCatalog; translate: string; onClose: () => void; onChanged: () => void;
  onTranslate: (code: string) => void; onNext?: () => void;
}) {
  const { can, me } = useAuth();
  const { languages, label } = useLanguages();
  const [tab, setTab] = useState<'edit' | 'history'>('edit');
  const scopeLabel = catalog.country === '*' ? 'all countries'
    : me?.country?.code === catalog.country ? `${me.country.name} only` : catalog.country;
  // When translating, put the language being translated first and English (reference) below it.
  const langs = translate ? [translate, ...catalog.languages.filter((l) => l !== translate)] : catalog.languages;
  const reference = entry.texts.en?.effective ?? '';
  const untranslated = languages.filter((l) => l.active && l.code !== 'en' && l.code !== translate && !entry.texts[l.code]?.translated);
  return (
    <Modal wide title={GROUPS[entry.group] ?? entry.group} onClose={onClose}
      footer={onNext && <button className="btn secondary" onClick={onNext}>Next untranslated text →</button>}>
      <p className="sub" style={{ marginBottom: 12 }}>
        <code>{entry.key}</code> · applies to {scopeLabel} · {entry.sms ? 'SMS' : 'one USSD screen'}
      </p>
      {entry.placeholders.length > 0 && (
        <p className="placeholders">Placeholders (keep each one exactly): {entry.placeholders.map((p) => (
          <span key={p} className="ph" title={`Example: ${catalog.sampleValues[p]}`}>{`{${p}}`}</span>))}
        </p>
      )}
      <div className="tabs">
        <button className={tab === 'edit' ? 'active' : ''} onClick={() => setTab('edit')}>Texts</button>
        <button className={tab === 'history' ? 'active' : ''} onClick={() => setTab('history')}>History</button>
      </div>
      {tab === 'edit' && untranslated.length > 0 && can('MESSAGE_MANAGE') && (
        <Alert kind="info">
          {untranslated.map((l) => (
            <div key={l.code}>
              {l.englishName} is active and this screen is not translated.{' '}
              <button type="button" className="linklike" onClick={() => onTranslate(l.code)}>Set translation</button>
            </div>
          ))}
        </Alert>
      )}
      {tab === 'edit' ? (
        <div className="lang-editors">
          {langs.map((l) => entry.texts[l] && (
            <LanguageEditor key={`${entry.key}-${l}-${entry.texts[l]!.effective ?? ''}`} entry={entry} language={l}
              languageLabel={label(l)} country={catalog.country} samples={catalog.sampleValues}
              reference={l === 'en' ? undefined : reference}
              editable={can('MESSAGE_MANAGE') && !(translate && l === 'en')} onChanged={onChanged} />
          ))}
        </div>
      ) : <History entryKey={entry.key} />}
    </Modal>
  );
}

function LanguageEditor({ entry, language, languageLabel, country, samples, reference, editable, onChanged }: {
  entry: MessageEntry; language: Language; languageLabel: string; country: string; samples: Record<string, string>;
  reference?: string; editable: boolean; onChanged: () => void;
}) {
  const t = entry.texts[language]!;
  const initial = t.effective ?? '';
  const [text, setText] = useState(initial);
  const [check, setCheck] = useState<MessageCheck | null>(null);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState('');
  const [busy, setBusy] = useState(false);
  const dirty = text !== initial;
  const preview = fill(text, samples);

  useEffect(() => {
    if (!dirty || !text) { setCheck(null); return; }
    const id = setTimeout(() => {
      api.post<MessageCheck>(`/api/admin/messages/${entry.key}/check?language=${encodeURIComponent(language)}`, { text })
        .then(setCheck).catch((e) => setError((e as Error).message));
    }, 350);
    return () => clearTimeout(id);
  }, [text, dirty, entry.key, language]);

  const q = `?country=${encodeURIComponent(country)}`;
  async function save() {
    setBusy(true); setError(''); setSaved('');
    try {
      await api.put(`/api/admin/messages/${entry.key}/${language}${q}`, { text });
      setSaved('Saved'); onChanged();
    } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }
  async function revert() {
    setBusy(true); setError(''); setSaved('');
    try {
      await api.del(`/api/admin/messages/${entry.key}/${language}${q}`);
      setSaved(t.defaultText ? 'Reverted to default' : 'Translation removed'); onChanged();
    } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }

  const over = preview.length > t.maxLength;
  return (
    <div className="lang-editor">
      <div className="lang-editor-head">
        <h3>{languageLabel}</h3>
        {!t.translated ? <span className="badge warn">Not translated · customers would see English</span>
          : t.override ? <span className="badge info">{t.defaultText ? 'Custom' : 'Translated'} · {t.updatedBy} · {fmtDate(t.updatedAt)}</span>
          : t.inherited ? <span className="badge neutral">From all countries</span>
          : <span className="badge ok">Default</span>}
      </div>
      <div className="lang-editor-body">
        <Field label="Text" help="Use a new line for each menu option. Keep every {placeholder}.">
          <textarea rows={Math.min(9, Math.max(3, (text || reference || '').split('\n').length + 1))} value={text}
            readOnly={!editable} placeholder={reference ? `English: ${reference}` : undefined}
            onChange={(e) => { setText(e.target.value); setSaved(''); }} spellCheck={false} />
        </Field>
        <div className="phone" aria-label="Preview with example values">
          <div className="phone-screen">{preview || <span className="muted">{reference ? fill(reference, samples) : ''}</span>}</div>
          <div className={over ? 'len over' : 'len'}>{preview.length}/{t.maxLength}</div>
        </div>
      </div>
      {check && !check.valid && <Alert>{check.errors.map((e) => <div key={e}>{e}</div>)}</Alert>}
      <Alert>{error}</Alert>
      {saved && <Alert kind="success">{saved}</Alert>}
      {t.defaultText && text !== t.defaultText && (
        <details className="default-text"><summary>Default text</summary><div className="ussd-text">{t.defaultText}</div></details>
      )}
      {editable && (
        <div className="lang-editor-actions">
          {reference && !text && <button className="btn ghost small" onClick={() => setText(reference)}>Start from English</button>}
          {t.override && <button className="btn secondary small" disabled={busy} onClick={revert}>{t.defaultText ? 'Revert to default' : 'Remove translation'}</button>}
          {dirty && <button className="btn secondary small" disabled={busy} onClick={() => setText(initial)}>Discard</button>}
          <button className="btn small" disabled={busy || !dirty || !text.trim() || (check !== null && !check.valid)} onClick={save}>Save {languageLabel}</button>
        </div>
      )}
    </div>
  );
}

function History({ entryKey }: { entryKey: string }) {
  const { label } = useLanguages();
  const { data, error } = useLoad(() => api.get<MessageHistory[]>(`/api/admin/messages/${entryKey}/history`), [entryKey]);
  if (error) return <Alert>{error}</Alert>;
  if (!data) return <div className="empty">Loading…</div>;
  if (data.length === 0) return <div className="empty">No changes yet — the default text has always been used.</div>;
  return (
    <table>
      <thead><tr><th>When</th><th>Who</th><th>Language</th><th>Scope</th><th>Change</th></tr></thead>
      <tbody>{data.map((h, i) => (
        <tr key={i}>
          <td>{fmtDate(h.changedAt)}</td><td>{h.changedBy}</td><td>{label(h.language)}</td>
          <td>{h.country === '*' ? 'All countries' : h.country}</td>
          <td>{h.action === 'REVERT' ? <em>Reverted / removed</em> : <div className="ussd-text">{h.newText}</div>}</td>
        </tr>
      ))}</tbody>
    </table>
  );
}
