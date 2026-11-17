-- USSD loan repayment is optional: staff enable it per country in the portal.
-- Existing deployments start with repayment off; turn it on under Countries when ready.
ALTER TABLE country_config
    ADD COLUMN IF NOT EXISTS repayment_enabled BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN country_config.repayment_enabled IS
    'When true, USSD offers Repay Loan (main menu, loan submenu, next-due shortcut).';
