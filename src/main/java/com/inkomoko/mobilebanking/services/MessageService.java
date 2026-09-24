package com.inkomoko.mobilebanking.services;

import com.inkomoko.mobilebanking.models.CountryConfig;
import com.inkomoko.mobilebanking.models.UssdMessage;
import com.inkomoko.mobilebanking.repositories.CountryConfigRepository;
import com.inkomoko.mobilebanking.repositories.UssdMessageRepository;
import com.inkomoko.mobilebanking.utils.i18n.MessageBundle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves USSD/SMS texts by key and language.
 *
 * Resolution order:
 *   1. portal override for the active country and language
 *   2. portal override for every country ('*') and language
 *   3. shipped bundle for the language (i18n/ussd_&lt;code&gt;.properties; rw, en, fr)
 *   4. shipped English bundle (logged)
 *   5. the key itself (logged; never throws, so a gap degrades a screen instead of the session)
 *
 * Overrides are held in memory: reloaded after every portal change on this node and every
 * {@code inkomoko.messages.refresh-ms} (default 60 s) so other nodes pick changes up too.
 */
@Slf4j
@Service
public class MessageService {

    private final MessageBundle bundle = MessageBundle.load();
    private final UssdMessageRepository overridesRepo;
    private final CountryConfigRepository countryRepo;

    private record Snapshot(String countryCode, Map<String, String> overrides) {
    }

    private volatile Snapshot snapshot = new Snapshot(null, Map.of());

    public MessageService(UssdMessageRepository overridesRepo, CountryConfigRepository countryRepo) {
        this.overridesRepo = overridesRepo;
        this.countryRepo = countryRepo;
        refresh();
    }

    public String text(String l, String key) {
        return text(l, key, Map.of());
    }

    public String text(String l, String key, Map<String, ?> params) {
        return MessageBundle.format(template(l, key), params);
    }

    /** Effective (unformatted) template for the active country, after fallbacks. */
    public String template(String l, String key) {
        String lang = l == null ? MessageBundle.REFERENCE : l;
        Snapshot snap = snapshot;
        if (!snap.overrides().isEmpty()) {
            String o = snap.countryCode() == null ? null : snap.overrides().get(id(snap.countryCode(), lang, key));
            if (o == null) o = snap.overrides().get(id(UssdMessage.ALL_COUNTRIES, lang, key));
            if (o != null) return o;
        }
        String t = bundle.template(lang, key);
        if (t != null) return t;
        t = bundle.template(MessageBundle.REFERENCE, key);
        if (t != null) {
            log.debug("No {} text for key={}, using English", lang, key);
            return t;
        }
        log.error("Unknown USSD text key={}", key);
        return key;
    }

    /** Reloads overrides and the active country; keeps the previous snapshot if the DB is unavailable. */
    @Scheduled(fixedDelayString = "${inkomoko.messages.refresh-ms:60000}",
            initialDelayString = "${inkomoko.messages.refresh-ms:60000}")
    public void refresh() {
        try {
            Map<String, String> map = new HashMap<>();
            for (UssdMessage m : overridesRepo.findAll()) {
                map.put(id(m.getCountryCode(), m.getLanguage(), m.getMsgKey()), m.getText());
            }
            String country = countryRepo.findFirstByActiveTrue().map(CountryConfig::getCode).orElse(null);
            snapshot = new Snapshot(country, Map.copyOf(map));
        } catch (Exception e) {
            log.warn("Could not reload USSD text overrides, keeping {} cached: {}", snapshot.overrides().size(),
                    e.getMessage());
        }
    }

    public MessageBundle bundle() {
        return bundle;
    }

    private static String id(String country, String lang, String key) {
        return country + "|" + lang + "|" + key;
    }
}
