-- "How do I repay?" FAQ: Repay Loan is option 2 of the main menu (V12), and the flow is
-- Pay balance / Other amount -> paying number -> Mobile Money PIN. Only the seeded text is
-- replaced, so answers already edited in the portal are kept.

UPDATE faq_item
SET body = 'Choose 2. Repay Loan, pick Pay balance or Other amount (min RWF 500), choose the paying number, then approve with your Mobile Money PIN. You get an SMS receipt.',
    updated_at = NOW()
WHERE language = 'en' AND body LIKE 'Choose 4. Repay Loan%';

UPDATE faq_item
SET body = 'Hitamo 2. Kwishyura, hitamo ideni ryose cyangwa andi mafaranga (nibura RWF 500), hitamo nimero yishyura, wemeze na PIN ya Mobile Money. Uzahabwa SMS.',
    updated_at = NOW()
WHERE language = 'rw' AND body LIKE 'Hitamo 4.%';

UPDATE faq_item
SET body = 'Choisissez 2. Rembourser, puis le solde ou un autre montant (min RWF 500), le numero payeur, et validez avec votre PIN Mobile Money. Recu par SMS.',
    updated_at = NOW()
WHERE language = 'fr' AND body LIKE 'Choisissez 4.%';
