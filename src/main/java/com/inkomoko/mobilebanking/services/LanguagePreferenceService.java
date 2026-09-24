package com.inkomoko.mobilebanking.services;

import com.inkomoko.mobilebanking.enums.ELanguage;
import com.inkomoko.mobilebanking.models.LanguageSetting;
import com.inkomoko.mobilebanking.repositories.LanguageSettingRepository;
import com.inkomoko.mobilebanking.utils.DateUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LanguagePreferenceService {

    private final LanguageSettingRepository repo;

    /** Last language the client chose; Kinyarwanda by default. */
    public ELanguage preferredLanguage(String msisdn) {
        try {
            return repo.findByMsisdn(msisdn).map(LanguageSetting::getLanguage).orElse(ELanguage.KINYARWANDA);
        } catch (Exception e) {
            return ELanguage.KINYARWANDA;
        }
    }

    public void savePreferredLanguage(String msisdn, ELanguage language) {
        try {
            LanguageSetting s = repo.findByMsisdn(msisdn)
                    .orElseGet(() -> new LanguageSetting(msisdn, language, DateUtility.nowKigali()));
            s.setLanguage(language);
            s.setUpdatedAt(DateUtility.nowKigali());
            repo.save(s);
        } catch (DataIntegrityViolationException race) {
            // concurrent first-time insert for the same MSISDN — the other write wins
            log.debug("language preference race for {}", msisdn);
        }
    }
}
