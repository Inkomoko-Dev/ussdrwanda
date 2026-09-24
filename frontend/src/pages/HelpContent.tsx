import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { Alert, Card, Field, Modal, PageHead, useLoad } from '../components/ui';
import { useLanguages } from '../languages';
import type { FaqItem, Language } from '../types';

export default function HelpContent() {
  const { languages } = useLanguages();
  const { can } = useAuth();
  const { data, error, reload } = useLoad(() => api.get<FaqItem[]>('/api/admin/faqs'), []);
  const [edit, setEdit] = useState<Partial<FaqItem> | null>(null);

  return (
    <>
      <PageHead title="Help & FAQs" subtitle="Shown under 5. Help on USSD — changes apply immediately"
        actions={can('FAQ_MANAGE') && <button className="btn" onClick={() => setEdit({ language: 'en', displayOrder: 1, active: true })}>Add item</button>} />
      <Alert>{error}</Alert>
      {languages.filter((l) => l.active || (data ?? []).some((f) => f.language === l.code)).map((l) => {
        const items = (data ?? []).filter((f) => f.language === l.code);
        return (
          <Card key={l.code} title={l.englishName}>
            {items.length === 0 ? <div className="empty">No items.</div> : (
              <table>
                <thead><tr><th>#</th><th>Title</th><th>Text</th><th>Status</th><th></th></tr></thead>
                <tbody>{items.map((f) => (
                  <tr key={f.id}>
                    <td>{f.displayOrder}</td><td><strong>{f.title}</strong></td>
                    <td>{f.body} <span className="sub" style={{ margin: 0 }}>({f.body.length}/150)</span></td>
                    <td>{f.active ? 'Shown' : 'Hidden'}</td>
                    <td>{can('FAQ_MANAGE') && <button className="btn ghost small" onClick={() => setEdit(f)}>Edit</button>}</td>
                  </tr>
                ))}</tbody>
              </table>
            )}
          </Card>
        );
      })}
      {edit && <FaqModal item={edit} onClose={() => setEdit(null)} onDone={() => { setEdit(null); reload(); }} />}
    </>
  );
}

function FaqModal({ item, onClose, onDone }: { item: Partial<FaqItem>; onClose: () => void; onDone: () => void }) {
  const { languages } = useLanguages();
  const [f, setF] = useState(item);
  const [error, setError] = useState('');
  async function save() {
    try {
      const body = { language: f.language, title: f.title, body: f.body, displayOrder: f.displayOrder, active: f.active };
      if (item.id) await api.put(`/api/admin/faqs/${item.id}`, body); else await api.post('/api/admin/faqs', body);
      onDone();
    } catch (e) { setError((e as Error).message); }
  }
  return (
    <Modal title={item.id ? 'Edit help item' : 'Add help item'} onClose={onClose}
      footer={<><button className="btn secondary" onClick={onClose}>Cancel</button><button className="btn" disabled={!f.title || !f.body} onClick={save}>Save</button></>}>
      <Alert>{error}</Alert>
      <div className="form-grid">
        <Field label="Language"><select value={f.language} onChange={(e) => setF({ ...f, language: e.target.value as Language })}>
          {languages.filter((l) => l.active).map((l) => <option key={l.code} value={l.code}>{l.englishName}</option>)}</select></Field>
        <Field label="Order"><input type="number" min={0} value={f.displayOrder ?? 0} onChange={(e) => setF({ ...f, displayOrder: Number(e.target.value) })} /></Field>
      </div>
      <Field label="Menu title" help="Max 40 characters"><input maxLength={40} value={f.title ?? ''} onChange={(e) => setF({ ...f, title: e.target.value })} /></Field>
      <Field label="Screen text" help="Keep under ~150 characters to fit one USSD screen (GSM characters, no accents)">
        <textarea rows={4} maxLength={300} value={f.body ?? ''} onChange={(e) => setF({ ...f, body: e.target.value })} />
      </Field>
      <label className="check"><input type="checkbox" checked={!!f.active} onChange={(e) => setF({ ...f, active: e.target.checked })} /> Show on USSD</label>
    </Modal>
  );
}
