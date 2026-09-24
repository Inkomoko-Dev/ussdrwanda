package com.inkomoko.mobilebanking.services;

import com.inkomoko.mobilebanking.enums.ECharset;
import com.inkomoko.mobilebanking.models.CountryConfig;
import com.inkomoko.mobilebanking.models.UssdLanguage;
import com.inkomoko.mobilebanking.models.UssdMessage;
import com.inkomoko.mobilebanking.models.api.portal.LanguageDtos.*;
import com.inkomoko.mobilebanking.repositories.CountryConfigRepository;
import com.inkomoko.mobilebanking.repositories.UssdLanguageRepository;
import com.inkomoko.mobilebanking.repositories.UssdMessageRepository;
import com.inkomoko.mobilebanking.utils.DateUtility;
import com.inkomoko.mobilebanking.utils.i18n.MessageBundle;
import com.inkomoko.mobilebanking.utils.i18n.MessageRules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Languages the USSD service can speak (table ussd_language).
 *
 * A language is ready for customers when every text key has a value: shipped in a bundle, or
 * translated in the portal for all countries ('*') or for the country that offers it. Only ready,
 * active languages can be enabled for a country, so a customer never lands on a half-translated menu.
 */
@Slf4j
@Service
public class LanguageService {

    private final UssdLanguageRepository repo;
    private final UssdMessageRepository messagesRepo;
    private final CountryConfigRepository countries;
    private final MessageService messages;

    private volatile Map<String, UssdLanguage> cache = Map.of();

    public LanguageService(UssdLanguageRepository repo, UssdMessageRepository messagesRepo,
                           CountryConfigRepository countries, MessageService messages) {
        this.repo = repo;
        this.messagesRepo = messagesRepo;
        this.countries = countries;
        this.messages = messages;
        refresh();
    }

    // ── Lookups used on every USSD request (cached) ───────────────────────

    public Optional<UssdLanguage> find(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(cache.get(code.toLowerCase()));
    }

    /** Name shown on the USSD language screen. */
    public String nativeName(String code) {
        return find(code).map(UssdLanguage::getNativeName).orElse(code);
    }

    public ECharset charset(String code) {
        return find(code).map(UssdLanguage::getCharset).orElse(ECharset.GSM7);
    }

    @Scheduled(fixedDelayString = "${inkomoko.messages.refresh-ms:60000}",
            initialDelayString = "${inkomoko.messages.refresh-ms:60000}")
    public void refresh() {
        try {
            Map<String, UssdLanguage> map = new LinkedHashMap<>();
            repo.findAllByOrderByBuiltInDescEnglishNameAsc().forEach(l -> map.put(l.getCode(), l));
            cache = Map.copyOf(map);
        } catch (Exception e) {
            log.warn("Could not reload USSD languages, keeping {} cached: {}", cache.size(), e.getMessage());
        }
    }

    // ── Translation coverage ──────────────────────────────────────────────

    /** Keys with no text in {@code code} for {@code country} (null = count only all-countries translations). */
    public Set<String> missingKeys(String code, String country) {
        MessageBundle bundle = messages.bundle();
        Set<String> missing = new TreeSet<>(bundle.keys(MessageBundle.REFERENCE));
        missing.removeAll(bundle.keys(code));
        if (missing.isEmpty()) return missing;
        Set<String> translated = new HashSet<>();
        for (UssdMessage m : messagesRepo.findByLanguage(code)) {
            if (UssdMessage.ALL_COUNTRIES.equals(m.getCountryCode()) || m.getCountryCode().equals(country)) {
                translated.add(m.getMsgKey());
            }
        }
        missing.removeAll(translated);
        return missing;
    }

    /**
     * Checks the languages a country wants to offer: known, active and fully translated for it.
     * Returns them normalised (lower case, no duplicates, order kept).
     */
    public List<String> requireReady(List<String> codes, String country) {
        List<String> result = codes.stream().map(c -> c == null ? "" : c.trim().toLowerCase())
                .filter(c -> !c.isEmpty()).distinct().toList();
        for (String c : result) {
            UssdLanguage l = repo.findById(c).orElseThrow(() -> new IllegalArgumentException("Unknown language: " + c));
            if (!l.isActive()) throw new IllegalArgumentException(l.getEnglishName() + " is inactive");
            int missing = missingKeys(c, country).size();
            if (missing > 0) {
                throw new IllegalArgumentException(l.getEnglishName() + " is not fully translated yet (" + missing
                        + " texts missing). Translate it under Content > USSD & SMS texts first.");
            }
        }
        return result;
    }

    // ── Portal management ─────────────────────────────────────────────────

    public List<LanguageView> list() {
        int total = messages.bundle().keys(MessageBundle.REFERENCE).size();
        List<CountryConfig> all = countries.findAll();
        return repo.findAllByOrderByBuiltInDescEnglishNameAsc().stream()
                .map(l -> view(l, total, all)).toList();
    }

    @Transactional
    public LanguageView create(LanguageCreateRequest r, String actor) {
        String code = r.code().trim().toLowerCase();
        if (repo.existsById(code)) throw new IllegalArgumentException("Language " + code + " already exists");
        UssdLanguage l = new UssdLanguage();
        l.setCode(code);
        l.setBuiltIn(false);
        l.setActive(true);
        l.setCreatedBy(actor);
        l.setCreatedAt(DateUtility.nowKigali());
        apply(l, r.nativeName(), r.englishName(), r.charset());
        return view(repo.save(l));
    }

    @Transactional
    public LanguageView update(String code, LanguageUpdateRequest r) {
        UssdLanguage l = require(code);
        if (l.isBuiltIn() && r.charset() != l.getCharset()) {
            throw new IllegalArgumentException("The character set of a built-in language cannot change");
        }
        if (!r.active() && l.isActive()) {
            List<String> used = usedBy(l.getCode(), countries.findAll());
            if (!used.isEmpty()) {
                throw new IllegalArgumentException("Remove " + l.getEnglishName() + " from " + String.join(", ", used)
                        + " before deactivating it");
            }
        }
        l.setActive(r.active());
        apply(l, r.nativeName(), r.englishName(), r.charset());
        return view(repo.save(l));
    }

    /** Deletes a portal-added language and its translations (history is kept). */
    @Transactional
    public void delete(String code) {
        UssdLanguage l = require(code);
        if (l.isBuiltIn()) throw new IllegalArgumentException("Built-in languages cannot be deleted");
        List<String> used = usedBy(l.getCode(), countries.findAll());
        if (!used.isEmpty()) {
            throw new IllegalArgumentException(l.getEnglishName() + " is offered in " + String.join(", ", used));
        }
        messagesRepo.deleteAll(messagesRepo.findByLanguage(l.getCode()));
        repo.delete(l);
    }

    private void apply(UssdLanguage l, String nativeName, String englishName, ECharset charset) {
        String nn = nativeName.trim();
        if (charset == ECharset.GSM7 && !MessageBundle.isGsm7(nn)) {
            throw new IllegalArgumentException("The native name has characters outside GSM-7; choose the UCS-2 "
                    + "character set or write it without accents");
        }
        l.setNativeName(nn);
        l.setEnglishName(englishName.trim());
        l.setCharset(charset);
        l.setUpdatedAt(DateUtility.nowKigali());
    }

    private UssdLanguage require(String code) {
        return repo.findById(code == null ? "" : code.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Unknown language: " + code));
    }

    private LanguageView view(UssdLanguage l) {
        return view(l, messages.bundle().keys(MessageBundle.REFERENCE).size(), countries.findAll());
    }

    private LanguageView view(UssdLanguage l, int total, List<CountryConfig> all) {
        int translated = total - missingKeys(l.getCode(), null).size();
        return new LanguageView(l.getCode(), l.getNativeName(), l.getEnglishName(), l.getCharset(), l.isBuiltIn(),
                l.isActive(), translated, total, usedBy(l.getCode(), all), l.getCreatedBy(), l.getCreatedAt());
    }

    private static List<String> usedBy(String code, List<CountryConfig> all) {
        return all.stream().filter(c -> c.languageList().contains(code)).map(CountryConfig::getCode).sorted().toList();
    }

    /** Longest USSD length allowed for a key in this language. */
    public int maxLength(String key, String code) {
        return MessageRules.maxLength(key, charset(code));
    }
}
