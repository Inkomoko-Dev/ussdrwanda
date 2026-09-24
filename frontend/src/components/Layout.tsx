import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth';
import type { Privilege } from '../types';
import { BrandBar, Logo } from './ui';

interface NavItem { to: string; label: string; any: Privilege[]; demoOnly?: boolean }

const SECTIONS: { title: string; items: NavItem[] }[] = [
  { title: 'Overview', items: [{ to: '/dashboard', label: 'Dashboard', any: ['DASHBOARD_VIEW'] }] },
  {
    title: 'Customers',
    items: [
      { to: '/customers', label: 'Customers', any: ['CUSTOMER_VIEW', 'CUSTOMER_ONBOARD'] },
      { to: '/demo-data', label: 'Demo core banking', any: ['DEMO_DATA_MANAGE'], demoOnly: true },
    ],
  },
  {
    title: 'Repayments',
    items: [
      { to: '/repayments', label: 'Repayments', any: ['REPAYMENT_VIEW'] },
      { to: '/approvals', label: 'Approvals', any: ['RECON_APPROVE', 'RECON_PROPOSE'] },
    ],
  },
  {
    title: 'Content',
    items: [
      { to: '/help-content', label: 'Help & FAQs', any: ['FAQ_VIEW'] },
      { to: '/ussd-texts', label: 'USSD & SMS texts', any: ['MESSAGE_VIEW'] },
    ],
  },
  {
    title: 'Administration',
    items: [
      { to: '/users', label: 'Users', any: ['USER_VIEW'] },
      { to: '/roles', label: 'Roles & privileges', any: ['ROLE_VIEW'] },
      { to: '/countries', label: 'Countries', any: ['COUNTRY_VIEW'] },
      { to: '/languages', label: 'Languages', any: ['LANGUAGE_MANAGE', 'MESSAGE_VIEW', 'COUNTRY_VIEW'] },
      { to: '/password-policy', label: 'Password policy', any: ['POLICY_MANAGE'] },
      { to: '/audit', label: 'Audit log', any: ['AUDIT_VIEW'] },
    ],
  },
];

export function visibleNav(can: (...p: Privilege[]) => boolean, demoMode: boolean): NavItem[] {
  return SECTIONS.flatMap((s) => s.items).filter((i) => can(...i.any) && (!i.demoOnly || demoMode));
}

export default function Layout() {
  const { me, can, signOut } = useAuth();
  if (!me) return null;
  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <Logo height={46} />
          <small>USSD staff portal</small>
          <BrandBar />
        </div>
        <nav className="nav" aria-label="Main">
          {SECTIONS.map((section) => {
            const items = section.items.filter((i) => can(...i.any) && (!i.demoOnly || me.demoMode));
            if (!items.length) return null;
            return (
              <div key={section.title}>
                <div className="nav-section">{section.title}</div>
                {items.map((i) => (
                  <NavLink key={i.to} to={i.to} className={({ isActive }) => (isActive ? 'active' : '')}>
                    {i.label}
                  </NavLink>
                ))}
              </div>
            );
          })}
        </nav>
        <div className="sidebar-foot">*{me.ussdServiceCode}# · {me.country?.name ?? 'Country not set'}</div>
      </aside>
      <div className="main">
        <header className="topbar">
          {me.country && <span className="chip">{me.country.name} · *{me.ussdServiceCode}#</span>}
          {!me.country && <span className="chip demo">Country not configured</span>}
          {me.demoMode && <span className="chip demo" title="Core banking is simulated (pilot)">Demo mode</span>}
          <span className="spacer" />
          <div className="user">
            <strong>{me.user.fullName}</strong>
            <span>{me.user.roleName}</span>
          </div>
          <NavLink to="/account" className="btn secondary small">My account</NavLink>
          <button className="btn ghost small" onClick={signOut}>Sign out</button>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
