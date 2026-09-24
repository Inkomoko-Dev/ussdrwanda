import { useMemo, useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { Alert, Card, Field, Modal, PageHead, useLoad } from '../components/ui';
import type { Privilege, PrivilegeView, RoleView } from '../types';

export default function Roles() {
  const { can } = useAuth();
  const { data, error, reload } = useLoad(() => api.get<RoleView[]>('/api/admin/roles'), []);
  const privileges = useLoad(() => api.get<PrivilegeView[]>('/api/admin/privileges'), []).data ?? [];
  const [edit, setEdit] = useState<Partial<RoleView> | null>(null);
  const [err, setErr] = useState('');
  const describe = (p: Privilege) => privileges.find((x) => x.code === p)?.description ?? p;

  async function remove(r: RoleView) {
    if (!confirm(`Delete the role "${r.name}"?`)) return;
    try { await api.del(`/api/admin/roles/${r.id}`); reload(); } catch (e) { setErr((e as Error).message); }
  }

  return (
    <>
      <PageHead title="Roles & privileges" subtitle="Each action in the portal is a privilege; roles group privileges and are assigned to users"
        actions={can('ROLE_MANAGE') && <button className="btn" onClick={() => setEdit({ privileges: [] })}>Create role</button>} />
      <Alert>{error || err}</Alert>
      <div className="grid two">
        {data?.map((r) => (
          <Card key={r.id} title={r.name}
            actions={can('ROLE_MANAGE') && !r.systemRole && <div className="actions">
              <button className="btn ghost small" onClick={() => setEdit(r)}>Edit</button>
              <button className="btn ghost small" onClick={() => remove(r)}>Delete</button></div>}>
            <p className="sub" style={{ marginBottom: 8 }}>{r.description} · {r.userCount} user(s)</p>
            {r.systemRole ? <span className="badge info">All privileges (system role)</span> : (
              <ul style={{ margin: 0, paddingLeft: 18, color: 'var(--muted)' }}>
                {r.privileges.map((p) => <li key={p}>{describe(p)}</li>)}
                {r.privileges.length === 0 && <li>No privileges</li>}
              </ul>
            )}
          </Card>
        ))}
      </div>
      {edit && <RoleModal role={edit} privileges={privileges} onClose={() => setEdit(null)} onDone={() => { setEdit(null); reload(); }} />}
    </>
  );
}

function RoleModal({ role, privileges, onClose, onDone }: {
  role: Partial<RoleView>; privileges: PrivilegeView[]; onClose: () => void; onDone: () => void;
}) {
  const [name, setName] = useState(role.name ?? '');
  const [description, setDescription] = useState(role.description ?? '');
  const [selected, setSelected] = useState<Set<Privilege>>(new Set(role.privileges ?? []));
  const [error, setError] = useState('');
  const groups = useMemo(() => {
    const m = new Map<string, PrivilegeView[]>();
    privileges.forEach((p) => m.set(p.category, [...(m.get(p.category) ?? []), p]));
    return [...m.entries()];
  }, [privileges]);

  const toggle = (p: Privilege) => setSelected((s) => { const n = new Set(s); if (n.has(p)) n.delete(p); else n.add(p); return n; });
  const toggleGroup = (items: PrivilegeView[], on: boolean) =>
    setSelected((s) => { const n = new Set(s); items.forEach((i) => (on ? n.add(i.code) : n.delete(i.code))); return n; });

  async function save() {
    try {
      const body = { name, description, privileges: [...selected] };
      if (role.id) await api.put(`/api/admin/roles/${role.id}`, body); else await api.post('/api/admin/roles', body);
      onDone();
    } catch (e) { setError((e as Error).message); }
  }

  return (
    <Modal title={role.id ? `Edit role: ${role.name}` : 'Create role'} onClose={onClose} wide
      footer={<><button className="btn secondary" onClick={onClose}>Cancel</button><button className="btn" disabled={!name.trim()} onClick={save}>Save role</button></>}>
      <Alert>{error}</Alert>
      <div className="form-grid">
        <Field label="Role name"><input value={name} onChange={(e) => setName(e.target.value)} /></Field>
        <Field label="Description"><input value={description} onChange={(e) => setDescription(e.target.value)} /></Field>
      </div>
      <h3>Privileges ({selected.size})</h3>
      {groups.map(([category, items]) => {
        const all = items.every((i) => selected.has(i.code));
        return (
          <div className="priv-group" key={category}>
            <div className="priv-group-head">
              <strong>{category}</strong>
              <button type="button" className="btn ghost small" onClick={() => toggleGroup(items, !all)}>{all ? 'Clear' : 'Select all'}</button>
            </div>
            {items.map((p) => (
              <label className="check" key={p.code}>
                <input type="checkbox" checked={selected.has(p.code)} onChange={() => toggle(p.code)} />
                {p.description} <span className="mono" style={{ color: 'var(--muted)' }}>{p.code}</span>
              </label>
            ))}
          </div>
        );
      })}
    </Modal>
  );
}
