-- Initial Help / FAQ content. PLACEHOLDERS: replace office addresses and helpline with
-- Inkomoko's confirmed details (editable at runtime via /api/admin/faqs).
-- Kinyarwanda and French wording is illustrative, pending native-speaker review.

INSERT INTO faq_item (language, title, body, display_order, active, created_at, updated_at) VALUES
('ENGLISH', 'Office locations', 'Inkomoko Rwanda offices: Kigali (head office) and regional branches. Addresses: [TO BE CONFIRMED].', 1, TRUE, NOW(), NOW()),
('ENGLISH', 'Helpline', 'Call the Inkomoko helpline: [HELPLINE NUMBER], Mon-Fri 8:00-17:00.', 2, TRUE, NOW(), NOW()),
('ENGLISH', 'How do I repay?', 'Choose 4. Repay Loan, enter the amount (min RWF 500), confirm, then approve with your Mobile Money PIN. You get an SMS receipt.', 3, TRUE, NOW(), NOW()),
('ENGLISH', 'Forgot / locked PIN', 'After 3 wrong PINs your access is locked. Call the helpline and verify your identity to reset your PIN.', 4, TRUE, NOW(), NOW()),
('ENGLISH', 'Privacy', 'Your data is processed under Inkomoko''s Privacy Notice and stored in Rwanda. Never share your PIN with anyone.', 5, TRUE, NOW(), NOW()),

('KINYARWANDA', 'Aho ibiro biherereye', 'Ibiro bya Inkomoko: Kigali (icyicaro gikuru) n''amashami yo mu ntara. Aderesi: [BIZEMEZWA].', 1, TRUE, NOW(), NOW()),
('KINYARWANDA', 'Umurongo w''ubufasha', 'Hamagara umurongo w''ubufasha wa Inkomoko: [NUMERO], Kuwa 1-5 saa 8:00-17:00.', 2, TRUE, NOW(), NOW()),
('KINYARWANDA', 'Nishyura nte?', 'Hitamo 4. Kwishyura ideni, andika amafaranga (nibura RWF 500), wemeze, hanyuma wemeze na PIN ya Mobile Money. Uzahabwa SMS.', 3, TRUE, NOW(), NOW()),
('KINYARWANDA', 'Nibagiwe PIN', 'Nyuma yo kwibeshya PIN inshuro 3 konti irafungwa. Hamagara umurongo w''ubufasha kugira ngo bagufashe.', 4, TRUE, NOW(), NOW()),
('KINYARWANDA', 'Ibanga ry''amakuru', 'Amakuru yawe akoreshwa hakurikijwe Itangazo ry''Ibanga rya Inkomoko kandi abikwa mu Rwanda. Ntugasangize PIN yawe.', 5, TRUE, NOW(), NOW()),

('FRENCH', 'Nos bureaux', 'Bureaux Inkomoko Rwanda : Kigali (siege) et agences regionales. Adresses : [A CONFIRMER].', 1, TRUE, NOW(), NOW()),
('FRENCH', 'Service client', 'Appelez le service client Inkomoko : [NUMERO], lun-ven 8h00-17h00.', 2, TRUE, NOW(), NOW()),
('FRENCH', 'Comment rembourser ?', 'Choisissez 4. Rembourser, saisissez le montant (min RWF 500), confirmez puis validez avec votre PIN Mobile Money. Recu par SMS.', 3, TRUE, NOW(), NOW()),
('FRENCH', 'PIN oublie / bloque', 'Apres 3 PIN errones, l''acces est bloque. Appelez le service client et verifiez votre identite pour reinitialiser.', 4, TRUE, NOW(), NOW()),
('FRENCH', 'Confidentialite', 'Vos donnees sont traitees selon la Politique de confidentialite d''Inkomoko et hebergees au Rwanda. Ne partagez jamais votre PIN.', 5, TRUE, NOW(), NOW());
