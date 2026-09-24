ALTER TABLE country_config ADD COLUMN ussd_service_code VARCHAR(20);
UPDATE country_config SET ussd_service_code = '662*105' WHERE ussd_service_code IS NULL;
