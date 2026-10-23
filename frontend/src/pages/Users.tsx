import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { Alert, Card, Field, Modal, PageHead, StatusBadge, fmtDate, useLoad } from '../components/ui';
import type { Country, RoleView, UserView } from '../types';

export default function Users() {
  const { can, me } = useAuth();
  const { data, error, reload } = useLoad(() => api.get<UserView[]>('/api/admin/users'), []);
  const roles = useLoad(() => api.get<RoleView[]>('/api/admin/roles'), []).data ?? [];
  const countries = useLoad(() => api.get<Country[]>('/api/admin/countries'), []).data ?? [];
  const [edit, setEdit] = useState<Partial<UserView> | null>(null);
  const [secret, setSecret] = useState<{ user: UserView; temporaryPassword: string } | null>(null);
  const [err, setErr] = useState('');
  const [notice, setNotice] = useState('');

  async function resetPassword(u: UserView) {
    if (!confirm(`Reset the password of ${u.fullName}? They will get a temporary password.`)) return;
    try { setSecret(await api.post(`/api/admin/users/${u.id}/reset-password`)); reload(); } catch (e) { setErr((e as Error).message); }
  }
  async function emailDefault(u: UserView) {
    const own = me?.user.id === u.id ? ' You will be signed out and must use the password in that e-mail.' : '';
    if (!confirm(`Reset ${u.fullName}'s password to the default and send it to ${u.email}?${own}`)) return;
    setErr('');
    setNotice('');
    try {
      await api.post(`/api/admin/users/${u.id}/email-default-password`);
      setNotice(`Default sign-in details sent to ${u.email}.`);
      reload();
    } catch (e) { setErr((e as Error).message); }
  }
  async function unlock(u: UserView) {
    try { await api.post(`/api/admin/users/${u.id}/unlock`); reload(); } catch (e) { setErr((e as Error).message); }
  }

  return (
    <>
      <PageHead title="Users" subtitle="People who can sign in to this portal"
        actions={can('USER_MANAGE') && <button className="btn" onClick={() => setEdit({ status: 'ACTIVE' })}>Add user</button>} />
      <Alert>{error || err}</Alert>
      <Alert kind="success">{notice}</Alert>
      <Card>
        <div className="table-wrap">
          <table>
            <thead><tr><th>Name</th><th>Username</th><th>Role</th><th>Country</th><th>Status</th><th>Last sign-in</th><th></th></tr></thead>
            <tbody>
              {data?.map((u) => (
                <tr key={u.id}>
                  <td><strong>{u.fullName}</strong><div className="sub" style={{ margin: 0 }}>{u.email}</div></td>
                  <td className="mono">{u.username}</td><td>{u.roleName}</td><td>{u.countryCode ?? 'All'}</td>
                  <td><div className="actions"><StatusBadge status={u.status} />{u.locked && <StatusBadge status="LOCKED" />}
                    {u.mustChangePassword && <span className="badge info">Password change due</span>}</div></td>
                  <td>{fmtDate(u.lastLoginAt)}</td>
                  <td>{can('USER_MANAGE') && <div className="actions">
                    <button className="btn ghost small" onClick={() => setEdit(u)}>Edit</button>
                    <button className="btn ghost small" onClick={() => resetPassword(u)}>Reset password</button>
                    {me?.user.systemRole && u.email && <button className="btn ghost small" onClick={() => emailDefault(u)}>Email default password</button>}
                    {u.locked && <button className="btn secondary small" onClick={() => unlock(u)}>Unlock</button>}
                  </div>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
      {edit && <UserModal user={edit} roles={roles} countries={countries} onClose={() => setEdit(null)}
        onSaved={(res) => { setEdit(null); reload(); if (res?.temporaryPassword) setSecret(res as { user: UserView; temporaryPassword: string }); }} />}
      {secret && (
        <Modal title="Temporary password" onClose={() => setSecret(null)} footer={<button className="btn" onClick={() => setSecret(null)}>Done</button>}>
          <p>Share this with <strong>{secret.user.fullName}</strong> ({secret.user.username}) through a secure channel. It is shown only once; they must change it at first sign-in.</p>
          <div className="secret">{secret.temporaryPassword}</div>
        </Modal>
      )}
    </>
  );
}

function UserModal({ user, roles, countries, onClose, onSaved }: {
  user: Partial<UserView>; roles: RoleView[]; countries: Country[]; onClose: () => void;
  onSaved: (res?: { user: UserView; temporaryPassword?: string }) => void;
}) {
  const [f, setF] = useState<Partial<UserView>>(user);
  const [error, setError] = useState('');
  const isNew = !user.id;
  async function save() {
    setError('');
    try {
      if (isNew) {
        onSaved(await api.post('/api/admin/users', { username: f.username, fullName: f.fullName, email: f.email || undefined,
          phone: f.phone || undefined, roleId: f.roleId, countryCode: f.countryCode || undefined }));
      } else {
        await api.put(`/api/admin/users/${user.id}`, { fullName: f.fullName, email: f.email ?? '', phone: f.phone ?? '',
          roleId: f.roleId, countryCode: f.countryCode ?? '', status: f.status });
        onSaved();
      }
    } catch (e) { setError((e as Error).message); }
  }
  return (
    <Modal title={isNew ? 'Add user' : `Edit ${user.fullName}`} onClose={onClose}
      footer={<><button className="btn secondary" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!f.fullName || !f.roleId || (isNew && !f.username)} onClick={save}>{isNew ? 'Create user' : 'Save'}</button></>}>
      <Alert>{error}</Alert>
      <div className="form-grid">
        <Field label="Full name"><input value={f.fullName ?? ''} onChange={(e) => setF({ ...f, fullName: e.target.value })} /></Field>
        <Field label="Username"><input value={f.username ?? ''} disabled={!isNew} onChange={(e) => setF({ ...f, username: e.target.value.trim().toLowerCase() })} /></Field>
        <Field label="Email"><input type="email" value={f.email ?? ''} onChange={(e) => setF({ ...f, email: e.target.value })} /></Field>
        <Field label="Phone"><input value={f.phone ?? ''} onChange={(e) => setF({ ...f, phone: e.target.value })} /></Field>
        <Field label="Role"><select value={f.roleId ?? ''} onChange={(e) => setF({ ...f, roleId: Number(e.target.value) })}>
          <option value="" disabled>Choose a role</option>{roles.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}</select></Field>
        <Field label="Country"><select value={f.countryCode ?? ''} onChange={(e) => setF({ ...f, countryCode: e.target.value })}>
          <option value="">All countries</option>{countries.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}</select></Field>
        {!isNew && <Field label="Status"><select value={f.status} onChange={(e) => setF({ ...f, status: e.target.value as UserView['status'] })}>
          <option value="ACTIVE">Active</option><option value="DISABLED">Disabled</option></select></Field>}
      </div>
      {isNew && <p className="sub">A temporary password is generated and shown once after saving.</p>}
    </Modal>
  );
}
