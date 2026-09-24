import { HashRouter, Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth';
import Layout, { visibleNav } from './components/Layout';
import Account from './pages/Account';
import Approvals from './pages/Approvals';
import Audit from './pages/Audit';
import ForcedPasswordChange from './pages/ChangePassword';
import Countries from './pages/Countries';
import CountrySetup from './pages/CountrySetup';
import Customers from './pages/Customers';
import Dashboard from './pages/Dashboard';
import DemoData from './pages/DemoData';
import HelpContent from './pages/HelpContent';
import Languages from './pages/Languages';
import Login from './pages/Login';
import PasswordPolicy from './pages/PasswordPolicy';
import Repayments from './pages/Repayments';
import Roles from './pages/Roles';
import Users from './pages/Users';
import UssdTexts from './pages/UssdTexts';

export default function App() {
  const { me, loading, can } = useAuth();

  if (loading) return <div className="content"><p className="sub">Loading…</p></div>;
  if (!me) return <Login />;
  if (me.mustChangePassword || me.passwordExpired) return <ForcedPasswordChange />;
  if (me.countrySetupRequired && can('COUNTRY_MANAGE')) return <CountrySetup />;

  const home = visibleNav(can, me.demoMode)[0]?.to ?? '/account';
  const guard = (ok: boolean, el: JSX.Element) => (ok ? el : <Navigate to={home} replace />);

  return (
    <HashRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<Navigate to={home} replace />} />
          <Route path="/dashboard" element={guard(can('DASHBOARD_VIEW'), <Dashboard />)} />
          <Route path="/customers" element={guard(can('CUSTOMER_VIEW', 'CUSTOMER_ONBOARD'), <Customers />)} />
          <Route path="/demo-data" element={guard(can('DEMO_DATA_MANAGE') && me.demoMode, <DemoData />)} />
          <Route path="/repayments" element={guard(can('REPAYMENT_VIEW'), <Repayments />)} />
          <Route path="/approvals" element={guard(can('RECON_APPROVE', 'RECON_PROPOSE'), <Approvals />)} />
          <Route path="/help-content" element={guard(can('FAQ_VIEW'), <HelpContent />)} />
          <Route path="/ussd-texts" element={guard(can('MESSAGE_VIEW'), <UssdTexts />)} />
          <Route path="/users" element={guard(can('USER_VIEW'), <Users />)} />
          <Route path="/roles" element={guard(can('ROLE_VIEW'), <Roles />)} />
          <Route path="/countries" element={guard(can('COUNTRY_VIEW'), <Countries />)} />
          <Route path="/languages" element={guard(can('LANGUAGE_MANAGE', 'MESSAGE_VIEW', 'COUNTRY_VIEW'), <Languages />)} />
          <Route path="/password-policy" element={guard(can('POLICY_MANAGE'), <PasswordPolicy />)} />
          <Route path="/audit" element={guard(can('AUDIT_VIEW'), <Audit />)} />
          <Route path="/account" element={<Account />} />
          <Route path="*" element={<Navigate to={home} replace />} />
        </Route>
      </Routes>
    </HashRouter>
  );
}
