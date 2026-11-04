-- "Mark as paid" on a pending repayment (pilot, Omnichannel manual success): granted to the
-- Super Administrator role. The system role already has every privilege in code
-- (AdminRole.effectivePrivileges); the row keeps the grant visible in the database and the role editor.
-- Other roles get it from Administration > Roles & privileges.
INSERT INTO admin_role_privilege (role_id, privilege)
SELECT id, 'REPAYMENT_MARK_PAID' FROM admin_role WHERE system_role = TRUE
ON CONFLICT DO NOTHING;
