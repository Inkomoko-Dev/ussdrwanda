-- Settings menu and the main menu that links to it, for every country.
-- Newlines are real line breaks, matching the i18n bundles.

INSERT INTO ussd_message (msg_key, language, country_code, text, updated_by, updated_at) VALUES
('menu.main', 'en', '*', E'MAIN MENU\n1. Check Balance\n2. Loan Status\n3. Mini Statement\n4. Repay Loan\n5. Help / FAQs\n6. Settings\n0. Exit', 'system', NOW()),
('menu.main', 'rw', '*', E'MENU NYAMUKURU\n1. Kureba amafaranga asigaye\n2. Uko ideni rihagaze\n3. Amakuru y''imyishyurire\n4. Kwishyura ideni\n5. Ubufasha\n6. Igenamiterere\n0. Gusohoka', 'system', NOW()),
('menu.main', 'fr', '*', E'MENU PRINCIPAL\n1. Consulter le solde\n2. Statut du pret\n3. Mini releve\n4. Rembourser le pret\n5. Aide / FAQ\n6. Reglages\n0. Quitter', 'system', NOW()),

('settings.menu', 'en', '*', E'SETTINGS\n1. Change language\n2. Change PIN', 'system', NOW()),
('settings.menu', 'rw', '*', E'IGENAMITERERE\n1. Hindura ururimi\n2. Hindura PIN', 'system', NOW()),
('settings.menu', 'fr', '*', E'REGLAGES\n1. Changer la langue\n2. Changer le PIN', 'system', NOW()),

('settings.pin_current', 'en', '*', 'Enter your current PIN', 'system', NOW()),
('settings.pin_current', 'rw', '*', 'Shyiramo PIN ukoresha', 'system', NOW()),
('settings.pin_current', 'fr', '*', 'Entrez votre PIN actuel', 'system', NOW()),

('settings.pin_changed', 'en', '*', 'Your PIN has been changed.', 'system', NOW()),
('settings.pin_changed', 'rw', '*', 'PIN yawe yahinduwe.', 'system', NOW()),
('settings.pin_changed', 'fr', '*', 'Votre PIN a ete change.', 'system', NOW()),

('settings.language_saved', 'en', '*', 'Language updated.', 'system', NOW()),
('settings.language_saved', 'rw', '*', 'Ururimi rwahinduwe.', 'system', NOW()),
('settings.language_saved', 'fr', '*', 'Langue mise a jour.', 'system', NOW())
ON CONFLICT ON CONSTRAINT uq_ussd_message DO UPDATE
SET text = EXCLUDED.text,
    updated_by = EXCLUDED.updated_by,
    updated_at = EXCLUDED.updated_at;
