import { useAuth } from '../auth';
import { Card, Field, PageHead, fmtDate } from '../components/ui';
import { ChangePasswordForm } from './ChangePassword';

export default function Account() {
  const { me } = useAuth();
  if (!me) return null;
  return (
    <>
      <PageHead title="My account" />
      <div className="grid two">
        <Card title="Profile">
          <Field label="Name"><span>{me.user.fullName}</span></Field>
          <Field label="Username"><span className="mono">{me.user.username}</span></Field>
          <Field label="Role"><span>{me.user.roleName}</span></Field>
          <Field label="Last sign-in"><span>{fmtDate(me.user.lastLoginAt)}</span></Field>
        </Card>
        <Card title="Change password"><ChangePasswordForm /></Card>
      </div>
    </>
  );
}
