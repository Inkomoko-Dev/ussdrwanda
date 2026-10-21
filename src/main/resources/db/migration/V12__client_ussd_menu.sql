-- Client menu (Loan, Repay, Account, Settings, FAQs) and the phone that pays a repayment.
-- Newlines are real line breaks. These rows override the i18n bundles.

ALTER TABLE ussd_session ADD COLUMN payer_msisdn VARCHAR(20);

INSERT INTO ussd_message (msg_key, language, country_code, text, updated_by, updated_at) VALUES
('menu.main', 'en', '*', E'Welcome to the Inkomoko Loan App\n1. Loan\n2. Repay Loan\n3. Account Info\n4. Settings\n5. FAQs\n0. Exit', 'system', NOW()),
('menu.main', 'rw', '*', E'Murakaza neza kuri Inkomoko\n1. Ideni\n2. Kwishyura\n3. Konti\n4. Igenamiterere\n5. Ibibazo\n0. Gusohoka', 'system', NOW()),
('menu.main', 'fr', '*', E'Bienvenue chez Inkomoko\n1. Pret\n2. Rembourser\n3. Compte\n4. Reglages\n5. FAQ\n0. Quitter', 'system', NOW()),

('menu.loan', 'en', '*', E'1. Direct repayment\n2. Mini statement', 'system', NOW()),
('menu.loan', 'rw', '*', E'1. Kwishyura ideni\n2. Raporo y''ubwishyu', 'system', NOW()),
('menu.loan', 'fr', '*', E'1. Remboursement\n2. Mini releve', 'system', NOW()),

('menu.account', 'en', '*', E'1. Mini statement\n2. Next due date\n3. Registered phone', 'system', NOW()),
('menu.account', 'rw', '*', E'1. Raporo y''ubwishyu\n2. Itariki ikurikira\n3. Telefoni yanditse', 'system', NOW()),
('menu.account', 'fr', '*', E'1. Mini releve\n2. Prochaine echeance\n3. Telephone enregistre', 'system', NOW()),

('settings.menu', 'en', '*', E'Settings\n1. Change language\n2. Change PIN', 'system', NOW()),
('settings.menu', 'rw', '*', E'Igenamiterere\n1. Hindura ururimi\n2. Hindura PIN', 'system', NOW()),
('settings.menu', 'fr', '*', E'Reglages\n1. Changer la langue\n2. Changer le PIN', 'system', NOW()),

('settings.language', 'en', '*', 'Please select language', 'system', NOW()),
('settings.language', 'rw', '*', 'Hitamo ururimi', 'system', NOW()),
('settings.language', 'fr', '*', 'Choisissez la langue', 'system', NOW()),

('settings.pin_current', 'en', '*', 'Enter current PIN', 'system', NOW()),
('settings.pin_current', 'rw', '*', 'Shyiramo PIN ukoresha', 'system', NOW()),
('settings.pin_current', 'fr', '*', 'Entrez le PIN actuel', 'system', NOW()),

('settings.pin_new', 'en', '*', 'Enter new PIN', 'system', NOW()),
('settings.pin_new', 'rw', '*', 'Shyiramo PIN nshya', 'system', NOW()),
('settings.pin_new', 'fr', '*', 'Entrez le nouveau PIN', 'system', NOW()),

('settings.pin_confirm', 'en', '*', 'Confirm new PIN', 'system', NOW()),
('settings.pin_confirm', 'rw', '*', 'Emeza PIN nshya', 'system', NOW()),
('settings.pin_confirm', 'fr', '*', 'Confirmez le nouveau PIN', 'system', NOW()),

('settings.pin_changed', 'en', '*', 'Done', 'system', NOW()),
('settings.pin_changed', 'rw', '*', 'Byarangiye', 'system', NOW()),
('settings.pin_changed', 'fr', '*', 'Termine', 'system', NOW())
ON CONFLICT ON CONSTRAINT uq_ussd_message DO UPDATE
SET text = EXCLUDED.text,
    updated_by = EXCLUDED.updated_by,
    updated_at = EXCLUDED.updated_at;
