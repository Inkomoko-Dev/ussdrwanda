package com.inkomoko.mobilebanking.services;

import com.inkomoko.mobilebanking.models.LanguageSetting;
import com.inkomoko.mobilebanking.utils.i18n.MessageBundle;
import com.inkomoko.mobilebanking.repositories.LanguageSettingRepository;
import com.inkomoko.mobilebanking.utils.DateUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LanguagePreferenceService {

    private final LanguageSettingRepository repo;
    private final CountryService countries;

    /** Last language the client chose; Kinyarwanda by default. */
    public String preferredLanguage(String msisdn) {
        try {
            return repo.findByMsisdn(msisdn).map(LanguageSetting::getLanguage).orElseGet(this::defaultLanguage);
        } catch (Exception e) {
            return defaultLanguage();
        }
    }

    /** First language of the active country: used before a client has chosen one. */
    public String defaultLanguage() {
        List<String> langs = countries.languages();
        return langs.isEmpty() ? MessageBundle.REFERENCE : langs.get(0);
    }

    /** Language the client actually chose on USSD. Empty when they have not picked one yet. */
    public Optional<String> chosenLanguage(String msisdn) {
        return repo.findByMsisdn(msisdn).map(LanguageSetting::getLanguage);
    }

    public Map<String, String> chosenLanguages(Collection<String> msisdns) {
        if (msisdns == null || msisdns.isEmpty()) return Map.of();
        return repo.findByMsisdnIn(msisdns).stream()
                .collect(Collectors.toMap(LanguageSetting::getMsisdn, LanguageSetting::getLanguage, (a, b) -> a));
    }

    /** Keeps a chosen language when the customer's phone number changes. */
    public void reassign(String from, String to) {
        if (from == null || to == null || from.equals(to)) return;
        repo.findByMsisdn(from).ifPresent(setting -> {
            if (repo.findByMsisdn(to).isPresent()) {
                repo.delete(setting);
                return;
            }
            setting.setMsisdn(to);
            setting.setUpdatedAt(DateUtility.nowKigali());
            repo.save(setting);
        });
    }

    public void savePreferredLanguage(String msisdn, String language) {
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

    /** Drops a saved language (e.g. after Terms are declined) so the next dial starts at language again. */
    public void clearPreferredLanguage(String msisdn) {
        if (msisdn == null || msisdn.isBlank()) return;
        repo.findByMsisdn(msisdn).ifPresent(repo::delete);
    }
}
