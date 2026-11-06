import { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import {
  Activity,
  ClipboardCheck,
  CreditCard,
  Globe,
  HelpCircle,
  Languages,
  LayoutDashboard,
  LockKeyhole,
  LogOut,
  Menu,
  MessageSquare,
  ScrollText,
  Shield,
  UserCog,
  Users,
  X,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

import { useAuth } from '../auth';
import type { Privilege } from '../types';
import { BrandBar, Logo } from './ui';
import '../layout.css';

interface NavItem {
  to: string;
  label: string;
  any: Privilege[];
  icon: LucideIcon;
}

interface NavSection {
  title: string;
  items: NavItem[];
}

const SECTIONS: NavSection[] = [
  {
    title: 'Overview',
    items: [
      {
        to: '/dashboard',
        label: 'Dashboard',
        any: ['DASHBOARD_VIEW'],
        icon: LayoutDashboard,
      },
      {
        to: '/sessions',
        label: 'USSD sessions',
        any: ['SESSION_VIEW'],
        icon: Activity,
      },
    ],
  },
  {
    title: 'Customers',
    items: [
      {
        to: '/customers',
        label: 'Customers',
        any: ['CUSTOMER_VIEW', 'CUSTOMER_ONBOARD'],
        icon: Users,
      },
    ],
  },
  {
    title: 'Repayments',
    items: [
      {
        to: '/repayments',
        label: 'Repayments',
        any: ['REPAYMENT_VIEW'],
        icon: CreditCard,
      },
      {
        to: '/approvals',
        label: 'Approvals',
        any: ['RECON_APPROVE', 'RECON_PROPOSE'],
        icon: ClipboardCheck,
      },
    ],
  },
  {
    title: 'Content',
    items: [
      {
        to: '/help-content',
        label: 'Help & FAQs',
        any: ['FAQ_VIEW'],
        icon: HelpCircle,
      },
      {
        to: '/ussd-texts',
        label: 'USSD & SMS texts',
        any: ['MESSAGE_VIEW'],
        icon: MessageSquare,
      },
    ],
  },
  {
    title: 'Administration',
    items: [
      {
        to: '/users',
        label: 'Users',
        any: ['USER_VIEW'],
        icon: UserCog,
      },
      {
        to: '/roles',
        label: 'Roles & privileges',
        any: ['ROLE_VIEW'],
        icon: Shield,
      },
      {
        to: '/countries',
        label: 'Countries',
        any: ['COUNTRY_VIEW'],
        icon: Globe,
      },
      {
        to: '/languages',
        label: 'Languages',
        any: ['LANGUAGE_MANAGE', 'MESSAGE_VIEW', 'COUNTRY_VIEW'],
        icon: Languages,
      },
      {
        to: '/password-policy',
        label: 'Password policy',
        any: ['POLICY_MANAGE'],
        icon: LockKeyhole,
      },
      {
        to: '/audit',
        label: 'Audit log',
        any: ['AUDIT_VIEW'],
        icon: ScrollText,
      },
    ],
  },
];

export function visibleNav(
  can: (...privileges: Privilege[]) => boolean,
): NavItem[] {
  return SECTIONS.flatMap((section) => section.items).filter((item) =>
    can(...item.any),
  );
}

export default function Layout() {
  const { me, can, signOut } = useAuth();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  if (!me) return null;

  const initials =
    me.user.fullName
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part.charAt(0))
      .join('')
      .toUpperCase() || 'U';

  const closeMobileNav = () => setMobileNavOpen(false);

  return (
    <div className="portal-shell">
      {mobileNavOpen && (
        <button
          type="button"
          className="portal-sidebar-overlay"
          aria-label="Close navigation"
          onClick={closeMobileNav}
        />
      )}

      <aside
        className={`portal-sidebar ${mobileNavOpen ? 'portal-sidebar-open' : ''}`}
      >
<div className="portal-brand">
  <div className="portal-brand-top">
    <div className="portal-logo-container">
      <Logo height={42} />
    </div>

    <button
      type="button"
      className="portal-mobile-close"
      aria-label="Close navigation"
      onClick={closeMobileNav}
    >
      <X size={20} />
    </button>
  </div>

          <div className="portal-brand-caption">
            <span className="portal-brand-dot" />
            <span>USSD STAFF PORTAL</span>
          </div>

          <div className="portal-brand-bar">
            <BrandBar />
          </div>
        </div>

        <div className="portal-nav-heading">WORKSPACE</div>

        <nav className="portal-nav" aria-label="Main navigation">
          {SECTIONS.map((section) => {
            const items = section.items.filter((item) =>
              can(...item.any),
            );

            if (!items.length) return null;

            return (
              <div className="portal-nav-section" key={section.title}>
                <div className="portal-nav-section-title">
                  {section.title}
                </div>

                <div className="portal-nav-items">
                  {items.map((item) => {
                    const Icon = item.icon;

                    return (
                      <NavLink
                        key={item.to}
                        to={item.to}
                        end
                        onClick={closeMobileNav}
                        className={({ isActive }) =>
                          `portal-nav-link ${isActive ? 'portal-nav-link-active' : ''}`
                        }
                      >
                        <span className="portal-nav-icon">
                          <Icon size={18} strokeWidth={1.9} />
                        </span>

                        <span className="portal-nav-label">
                          {item.label}
                        </span>

                        <span className="portal-nav-arrow">
                          <span />
                        </span>
                      </NavLink>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </nav>

        <div className="portal-sidebar-bottom">
          <div className="portal-service-card">
            <div className="portal-service-icon">
              <Activity size={17} />
            </div>

            <div className="portal-service-info">
              <span className="portal-service-label">USSD SERVICE</span>
              <strong>*{me.ussdServiceCode}#</strong>
              <span className="portal-service-country">
                {me.country?.name ?? 'Country not set'}
              </span>
            </div>
          </div>

          <div className="portal-sidebar-version">
            <span className="portal-status-dot" />
            <span>Staff portal</span>
            <span className="portal-version-label">ADMIN</span>
          </div>
        </div>
      </aside>

      <div className="portal-main">
        <header className="portal-topbar">
          <div className="portal-topbar-left">
            <button
              type="button"
              className="portal-menu-button"
              aria-label="Open navigation"
              onClick={() => setMobileNavOpen(true)}
            >
              <Menu size={21} />
            </button>

            <div className="portal-topbar-heading">
              <span className="portal-topbar-eyebrow">INKOMOKO</span>
              <strong>Administration Portal</strong>
            </div>
          </div>

          <div className="portal-topbar-right">
            {me.country ? (
              <div className="portal-country-chip">
                <Globe size={15} />
                <span>{me.country.name}</span>
                <span className="portal-chip-divider" />
                <strong>*{me.ussdServiceCode}#</strong>
              </div>
            ) : (
              <div className="portal-country-chip portal-country-warning">
                <Globe size={15} />
                <span>Country not configured</span>
              </div>
            )}

            <div className="portal-topbar-divider" />

            <div className="portal-user">
              <div className="portal-user-avatar">{initials}</div>

              <div className="portal-user-details">
                <strong>{me.user.fullName}</strong>
                <span>{me.user.roleName}</span>
              </div>
            </div>

            <NavLink
              to="/account"
              className={({ isActive }) =>
                `portal-account-link ${isActive ? 'portal-account-link-active' : ''}`
              }
              title="My account"
            >
              <UserCog size={17} />
              <span>My account</span>
            </NavLink>

            <button
              type="button"
              className="portal-signout-button"
              onClick={signOut}
              title="Sign out"
            >
              <LogOut size={17} />
              <span>Sign out</span>
            </button>
          </div>
        </header>

        <main className="portal-content">
          <Outlet />
        </main>

        <footer className="portal-footer">
          <span>Inkomoko USSD Administration</span>
          <span>Authorized staff access only</span>
        </footer>
      </div>
    </div>
  );
}