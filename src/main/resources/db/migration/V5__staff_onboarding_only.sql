-- Customers are registered for USSD by Inkomoko staff only (portal onboarding);
-- self-registration on first dial is no longer supported.
ALTER TABLE country_config DROP COLUMN self_registration;

-- Staff can sign in with username or e-mail: e-mails must be unique (case-insensitive).
CREATE UNIQUE INDEX uq_admin_user_email ON admin_user (LOWER(email)) WHERE email IS NOT NULL;
