package com.inkomoko.mobilebanking.services.admin;

import com.inkomoko.mobilebanking.models.CountryConfig;
import com.inkomoko.mobilebanking.models.UssdLanguage;
import com.inkomoko.mobilebanking.models.UssdMessage;
import com.inkomoko.mobilebanking.models.UssdMessageHistory;
import com.inkomoko.mobilebanking.models.api.portal.MessageDtos.*;
import com.inkomoko.mobilebanking.repositories.CountryConfigRepository;
import com.inkomoko.mobilebanking.repositories.UssdLanguageRepository;
import com.inkomoko.mobilebanking.repositories.UssdMessageHistoryRepository;
import com.inkomoko.mobilebanking.repositories.UssdMessageRepository;
import com.inkomoko.mobilebanking.services.LanguageService;
import com.inkomoko.mobilebanking.services.MessageService;
import com.inkomoko.mobilebanking.utils.DateUtility;
import com.inkomoko.mobilebanking.utils.i18n.MessageBundle;
import com.inkomoko.mobilebanking.utils.i18n.MessageRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Portal editor for USSD/SMS texts. Overrides are stored per language and per country
 * ("*" = every country). For the shipped languages the bundle stays the untouched default, so
 * "Revert" is always safe; for languages added in the portal the all-countries rows ARE the
 * translation. Every save is validated with {@link MessageRules} (the same rules the build applies
 * to the bundles), using the language's character set for length limits.
 * Callers refresh {@link MessageService} after the transaction commits.
 */
@Service
@RequiredArgsConstructor
public class MessageAdminService {

    private final UssdMessageRepository repo;
    private final UssdMessageHistoryRepository historyRepo;
    private final CountryConfigRepository countries;
    private final UssdLanguageRepository languageRepo;
    private final MessageService messages;
    private final LanguageService languageService;

    /**
     * @param extra languages to show besides the scope's own (e.g. one being translated); English is
     *              always included first when extra languages are requested, as the reference.
     */
    public MessageCatalog catalog(String country, List<String> extra) {
        String scope = scope(country);
        List<String> languages = languages(scope, extra);
        MessageBundle bundle = messages.bundle();

        Map<String, UssdMessage> own = index(scope);
        Map<String, UssdMessage> shared = UssdMessage.ALL_COUNTRIES.equals(scope) ? Map.of() : index(UssdMessage.ALL_COUNTRIES);

        List<MessageEntry> entries = new ArrayList<>();
        for (String key : bundle.keys(MessageBundle.REFERENCE)) {
            Map<String, MessageText> texts = new LinkedHashMap<>();
            for (String l : languages) {
                texts.put(l, text(key, l, bundle.template(l, key), own.get(id(l, key)), shared.get(id(l, key))));
            }
            entries.add(new MessageEntry(key, key.substring(0, key.indexOf('.') < 0 ? key.length() : key.indexOf('.')),
                    MessageRules.isSms(key),
                    List.copyOf(MessageBundle.placeholders(bundle.template(MessageBundle.REFERENCE, key))), texts));
        }
        return new MessageCatalog(scope, languages, MessageRules.SAMPLE_VALUES, entries);
    }

    public MessageCheckResponse check(String key, String language, String text) {
        String reference = reference(key);
        String lang = language == null || language.isBlank() ? MessageBundle.REFERENCE : language.trim().toLowerCase();
        List<String> errors = MessageRules.validate(key, reference, text, languageService.charset(lang));
        String preview = text == null ? "" : MessageRules.preview(text);
        return new MessageCheckResponse(errors.isEmpty(), errors, preview, preview.length(),
                languageService.maxLength(key, lang));
    }

    @Transactional
    public MessageText update(String key, String language, String country, String text, String actor) {
        String scope = scope(country);
        String lang = requireLanguage(scope, language);
        List<String> errors = MessageRules.validate(key, reference(key), text, languageService.charset(lang));
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));

        UssdMessage m = repo.findByMsgKeyAndLanguageAndCountryCode(key, lang, scope).orElse(null);
        String old = m == null ? null : m.getText();
        if (text.equals(old)) return view(key, lang, scope);
        if (m == null) {
            m = new UssdMessage();
            m.setMsgKey(key);
            m.setLanguage(lang);
            m.setCountryCode(scope);
        }
        m.setText(text);
        m.setUpdatedBy(actor);
        m.setUpdatedAt(DateUtility.nowKigali());
        repo.save(m);
        history(key, lang, scope, "UPDATE", old, text, actor);
        return view(key, lang, scope);
    }

    /**
     * Removes the override so the shipped default (or the all-countries override) applies again.
     * For a portal-added language this removes the translation of that text.
     */
    @Transactional
    public MessageText revert(String key, String language, String country, String actor) {
        String scope = scope(country);
        String lang = language == null ? "" : language.trim().toLowerCase();
        reference(key);
        UssdMessage m = repo.findByMsgKeyAndLanguageAndCountryCode(key, lang, scope)
                .orElseThrow(() -> new IllegalArgumentException("Nothing to revert for this text"));
        if (UssdMessage.ALL_COUNTRIES.equals(scope) && !messages.bundle().isBundled(lang)) {
            List<String> offering = countries.findAll().stream()
                    .filter(c -> c.languageList().contains(lang)
                            && repo.findByMsgKeyAndLanguageAndCountryCode(key, lang, c.getCode()).isEmpty())
                    .map(CountryConfig::getCode).toList();
            if (!offering.isEmpty()) {
                throw new IllegalArgumentException("Customers in " + String.join(", ", offering)
                        + " use this translation; it cannot be removed while the language is offered there");
            }
        }
        repo.delete(m);
        history(key, lang, scope, "REVERT", m.getText(), null, actor);
        return view(key, lang, scope);
    }

    public List<MessageHistoryView> history(String key) {
        reference(key);
        return historyRepo.findTop50ByMsgKeyOrderByChangedAtDesc(key).stream()
                .map(h -> new MessageHistoryView(h.getLanguage(), h.getCountryCode(), h.getAction(), h.getOldText(),
                        h.getNewText(), h.getChangedBy(), h.getChangedAt()))
                .toList();
    }

    /** Resolves "" / null to the active country (or "*" when none is active yet); validates the code. */
    public String scope(String country) {
        if (country == null || country.isBlank()) {
            return countries.findFirstByActiveTrue().map(CountryConfig::getCode).orElse(UssdMessage.ALL_COUNTRIES);
        }
        String c = country.trim().toUpperCase();
        if (UssdMessage.ALL_COUNTRIES.equals(c)) return c;
        if (!countries.existsById(c)) throw new IllegalArgumentException("Unknown country: " + country);
        return c;
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private MessageText view(String key, String language, String scope) {
        UssdMessage own = repo.findByMsgKeyAndLanguageAndCountryCode(key, language, scope).orElse(null);
        UssdMessage shared = UssdMessage.ALL_COUNTRIES.equals(scope) ? null
                : repo.findByMsgKeyAndLanguageAndCountryCode(key, language, UssdMessage.ALL_COUNTRIES).orElse(null);
        return text(key, language, messages.bundle().template(language, key), own, shared);
    }

    /** own: override of this scope; shared: the all-countries override (null when scope is "*"). */
    private MessageText text(String key, String language, String def, UssdMessage own, UssdMessage shared) {
        UssdMessage winner = own != null ? own : shared;
        String effective = winner != null ? winner.getText() : def;
        return new MessageText(def, own == null ? null : own.getText(), shared == null ? null : shared.getText(),
                effective, effective != null, effective == null ? 0 : MessageRules.preview(effective).length(),
                languageService.maxLength(key, language),
                winner == null ? null : winner.getUpdatedBy(), winner == null ? null : winner.getUpdatedAt());
    }

    private String reference(String key) {
        String ref = messages.bundle().template(MessageBundle.REFERENCE, key);
        if (ref == null) throw new IllegalArgumentException("Unknown text key: " + key);
        return ref;
    }

    /** Scope languages: the country's own, or every active language for "*"; plus requested extras. */
    private List<String> languages(String scope, List<String> extra) {
        Set<String> result = new LinkedHashSet<>();
        List<String> requested = extra == null ? List.of() : extra.stream()
                .map(x -> x.trim().toLowerCase()).filter(x -> !x.isEmpty()).toList();
        if (!requested.isEmpty()) {
            result.add(MessageBundle.REFERENCE);
            for (String l : requested) {
                if (!languageRepo.existsById(l)) throw new IllegalArgumentException("Unknown language: " + l);
                result.add(l);
            }
            return List.copyOf(result);
        }
        if (UssdMessage.ALL_COUNTRIES.equals(scope)) {
            languageRepo.findAllByOrderByBuiltInDescEnglishNameAsc().stream()
                    .filter(UssdLanguage::isActive).forEach(l -> result.add(l.getCode()));
        } else {
            countries.findById(scope).ifPresent(c -> result.addAll(c.languageList()));
        }
        return List.copyOf(result);
    }

    /** Translations of any known language may be saved for all countries; a country only edits its own languages. */
    private String requireLanguage(String scope, String language) {
        String lang = language == null ? "" : language.trim().toLowerCase();
        if (!languageRepo.existsById(lang)) throw new IllegalArgumentException("Unknown language: " + language);
        if (!UssdMessage.ALL_COUNTRIES.equals(scope)
                && countries.findById(scope).map(c -> !c.languageList().contains(lang)).orElse(true)) {
            throw new IllegalArgumentException("Language " + lang + " is not offered in " + scope
                    + "; translate it for all countries instead");
        }
        return lang;
    }

    private Map<String, UssdMessage> index(String scope) {
        return repo.findAll().stream().filter(m -> scope.equals(m.getCountryCode()))
                .collect(Collectors.toMap(m -> id(m.getLanguage(), m.getMsgKey()), Function.identity()));
    }

    private static String id(String l, String key) {
        return l + "|" + key;
    }

    private void history(String key, String l, String scope, String action, String oldText, String newText,
                         String actor) {
        UssdMessageHistory h = new UssdMessageHistory();
        h.setMsgKey(key);
        h.setLanguage(l);
        h.setCountryCode(scope);
        h.setAction(action);
        h.setOldText(oldText);
        h.setNewText(newText);
        h.setChangedBy(actor);
        h.setChangedAt(DateUtility.nowKigali());
        historyRepo.save(h);
    }
}
