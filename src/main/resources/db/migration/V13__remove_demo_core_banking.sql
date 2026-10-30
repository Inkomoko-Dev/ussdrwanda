-- Demo core banking is removed: customer and loan data always comes from core banking
-- (Omnichannel or Fineract). Drop the demo tables and the privilege that managed them.
DELETE FROM admin_role_privilege WHERE privilege = 'DEMO_DATA_MANAGE';

DROP TABLE IF EXISTS demo_repayment;
DROP TABLE IF EXISTS demo_loan;
DROP TABLE IF EXISTS demo_client;
