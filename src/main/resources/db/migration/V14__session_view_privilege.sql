-- USSD sessions page (session log and visits per phone number): granted to every role
-- that can already see the dashboard. Super Administrator has all privileges implicitly.
INSERT INTO admin_role_privilege (role_id, privilege)
SELECT role_id, 'SESSION_VIEW' FROM admin_role_privilege WHERE privilege = 'DASHBOARD_VIEW'
ON CONFLICT DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_ussd_session_msisdn_created_at ON ussd_session (msisdn, created_at);
