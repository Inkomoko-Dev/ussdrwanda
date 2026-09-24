-- Languages become data: staff add a language in the portal, translate it under
-- "USSD & SMS texts" (ussd_message rows with country_code '*'), then offer it in a country.
CREATE TABLE ussd_language (
    code          VARCHAR(10)  PRIMARY KEY,              -- ISO 639 code, lower case
    native_name   VARCHAR(30)  NOT NULL,                 -- shown on the USSD language screen
    english_name  VARCHAR(60)  NOT NULL,                 -- shown in the portal
    charset       VARCHAR(5)   NOT NULL,                 -- GSM7 (182 chars/screen) | UCS2 (80 chars/screen)
    built_in      BOOLEAN      NOT NULL DEFAULT FALSE,   -- shipped as i18n/ussd_<code>.properties
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by    VARCHAR(120),
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP
);

INSERT INTO ussd_language (code, native_name, english_name, charset, built_in, active, created_by, created_at, updated_at) VALUES
('rw', 'Kinyarwanda', 'Kinyarwanda', 'GSM7', TRUE, TRUE, 'system', NOW(), NOW()),
('en', 'English',     'English',     'GSM7', TRUE, TRUE, 'system', NOW(), NOW()),
('fr', 'Francais',    'French',      'GSM7', TRUE, TRUE, 'system', NOW(), NOW());

-- Stored values move from enum names (KINYARWANDA/ENGLISH/FRENCH) to language codes.
CREATE FUNCTION pg_temp.lang_code(v VARCHAR) RETURNS VARCHAR AS $$
    SELECT CASE v WHEN 'KINYARWANDA' THEN 'rw' WHEN 'ENGLISH' THEN 'en' WHEN 'FRENCH' THEN 'fr' ELSE lower(v) END
$$ LANGUAGE SQL IMMUTABLE;

UPDATE ussd_session         SET language = pg_temp.lang_code(language) WHERE language IS NOT NULL;
UPDATE language_setting     SET language = pg_temp.lang_code(language);
UPDATE faq_item             SET language = pg_temp.lang_code(language);
UPDATE ussd_message         SET language = pg_temp.lang_code(language);
UPDATE ussd_message_history SET language = pg_temp.lang_code(language);
UPDATE country_config SET languages = (
    SELECT string_agg(pg_temp.lang_code(trim(x)), ',' ORDER BY ord)
    FROM unnest(string_to_array(languages, ',')) WITH ORDINALITY AS t(x, ord));

COMMENT ON COLUMN country_config.languages IS 'Ordered, comma-separated ussd_language codes (order = numbering on the language screen)';
