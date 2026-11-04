-- Inkomoko Rwanda contact details: head office Kacyiru, KG 559 St, Kigali; helpline 0788363555.
-- Only placeholders are replaced, so values already edited in the portal are kept.

UPDATE country_config
SET helpline = '0788363555', updated_at = NOW()
WHERE code = 'RW' AND (helpline IS NULL OR trim(helpline) = '' OR helpline LIKE '%XXX%');

-- Helpline FAQ
UPDATE faq_item SET body = replace(body, '[HELPLINE NUMBER]', '0788363555'), updated_at = NOW()
WHERE language = 'en' AND body LIKE '%[HELPLINE NUMBER]%';
UPDATE faq_item SET body = replace(body, '[NUMERO]', '0788363555'), updated_at = NOW()
WHERE language IN ('rw', 'fr') AND body LIKE '%[NUMERO]%';

-- Office locations FAQ
UPDATE faq_item
SET body = 'Inkomoko Rwanda head office: Kacyiru, KG 559 St, Kigali. Helpline: 0788363555.', updated_at = NOW()
WHERE language = 'en' AND body LIKE '%[TO BE CONFIRMED]%';
UPDATE faq_item
SET body = 'Icyicaro gikuru cya Inkomoko Rwanda: Kacyiru, KG 559 St, Kigali. Umurongo w''ubufasha: 0788363555.', updated_at = NOW()
WHERE language = 'rw' AND body LIKE '%[BIZEMEZWA]%';
UPDATE faq_item
SET body = 'Siege d''Inkomoko Rwanda : Kacyiru, KG 559 St, Kigali. Service client : 0788363555.', updated_at = NOW()
WHERE language = 'fr' AND body LIKE '%[A CONFIRMER]%';
