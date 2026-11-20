package com.inkomoko.mobilebanking.utils.menus;

import com.inkomoko.mobilebanking.enums.ELoanStatus;
import com.inkomoko.mobilebanking.enums.ECharset;
import com.inkomoko.mobilebanking.services.CountryService;
import com.inkomoko.mobilebanking.services.LanguageService;
import com.inkomoko.mobilebanking.services.MessageService;
import com.inkomoko.mobilebanking.utils.i18n.MessageBundle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Shared USSD screen texts. The wording lives in {@code i18n/ussd_<lang>.properties}
 * (see {@link MessageService}); this class only composes screens (line breaks, prefixes, suffixes)
 * so translators and portal editors work with whole sentences, never fragments.
 */
@Component
@RequiredArgsConstructor
public class CommonHeadings {

    private final MessageService messages;
    private final LanguageService languageService;
    private final CountryService countries;

    /** Shortcut for any key with named parameters. */
    public String text(String l, String key, Map<String, ?> params) {
        return messages.text(l, key, params);
    }

    public String text(String l, String key) {
        return messages.text(l, key);
    }

    // ── Language screen: shown before a language is known, so it uses every country language ──
    public String languageMenu(List<String> languages) {
        String options = options(languages);
        String full = joinAll(languages, "language.title") + options;
        if (full.length() <= limit(languages)) return full;
        String first = languages.isEmpty() ? MessageBundle.REFERENCE : languages.get(0);
        return messages.text(first, "language.title") + options;
    }

    public String invalidLanguage(List<String> languages) {
        // One short English line so "Invalid choice." is always visible; the multilingual
        // title+options follow. Joining invalid in every language used to blow the 182-char
        // budget and fall back to Kinyarwanda-only, which testers read as "no invalid message".
        return withInvalid(MessageBundle.REFERENCE, languageMenu(languages));
    }

    private String options(List<String> languages) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (String l : languages) sb.append('\n').append(i++).append(". ").append(languageService.nativeName(l));
        return sb.toString();
    }

    /** One non-GSM language on the screen turns the whole screen into UCS-2. */
    private int limit(List<String> languages) {
        boolean ucs2 = languages.stream().anyMatch(l -> languageService.charset(l) == ECharset.UCS2);
        return (ucs2 ? ECharset.UCS2 : ECharset.GSM7).ussdMax();
    }

    private int limit(String language) {
        return limit(List.of(language == null ? MessageBundle.REFERENCE : language));
    }

    private String joinAll(List<String> languages, String key) {
        return String.join(" / ", languages.stream().map(l -> messages.text(l, key)).distinct().toList());
    }

    // ── Terms & Conditions / privacy consent ──────────────────────────────
    public String consent(String l) {
        return messages.text(l, "consent.text");
    }

    /**
     * Version stored with each consent record: language + hash of the exact wording shown, so any
     * change to the notice (release or portal edit) is traceable without bumping a constant.
     */
    public String consentVersion(String l) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256")
                    .digest(messages.template(l, "consent.text").getBytes(StandardCharsets.UTF_8));
            return l + "-" + HexFormat.of().formatHex(h, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String mainMenu(String l) {
        return messages.text(l, countries.repaymentEnabled() ? "menu.main" : "menu.main.no_repay");
    }

    // ── Navigation / generic ──────────────────────────────────────────────
    /**
     * Navigation footer appended under service screens:
     * {@code 0. Back} (one step) and {@code 00. Home} (main menu).
     */
    public String back(String l) {
        return "\n" + messages.text(l, "nav.back");
    }

    /** "Invalid choice.\n" — prefixed to the re-shown screen. */
    public String invalidChoice(String l) {
        return line(l, "common.invalid");
    }

    /**
     * Prefixes {@code common.invalid} and keeps that line when the combined screen would exceed
     * the USSD character budget (gateways otherwise truncate from the top and drop the error).
     */
    public String withInvalid(String l, String screen) {
        String prefix = invalidChoice(l);
        String body = screen == null ? "" : screen;
        String full = prefix + body;
        int max = limit(l);
        if (full.length() <= max) return full;
        int room = Math.max(0, max - prefix.length());
        return prefix + (room == 0 ? "" : body.substring(0, Math.min(room, body.length())));
    }

    public String thankYou(String l) {
        return messages.text(l, "common.thank_you");
    }

    public String serviceUnavailable(String l) {
        return messages.text(l, "common.unavailable");
    }

    public String sessionExpired(String l, String serviceCode) {
        return messages.text(l, "session.expired", Map.of("code", "*" + serviceCode + "#"));
    }

    // ── Client identification ─────────────────────────────────────────────
    public String notRegistered(String l, String helpline) {
        return withHelpline(l, "client.not_registered", helpline);
    }

    public String notOnboarded(String l, String helpline) {
        return withHelpline(l, "client.not_onboarded", helpline);
    }

    public String accessDisabled(String l, String helpline) {
        return withHelpline(l, "client.access_disabled", helpline);
    }

    public String inactiveClient(String l, String helpline) {
        return withHelpline(l, "client.inactive", helpline);
    }

    // ── PIN ───────────────────────────────────────────────────────────────
    public String pinSetup(String l, int digits) {
        return messages.text(l, "pin.setup", Map.of("digits", digits));
    }

    public String pinConfirm(String l) {
        return messages.text(l, "pin.confirm");
    }

    public String pinMismatch(String l) {
        return line(l, "pin.mismatch");
    }

    public String pinBadFormat(String l, int digits) {
        return messages.text(l, "pin.bad_format", Map.of("digits", digits)) + "\n";
    }

    public String pinWeak(String l) {
        return line(l, "pin.weak");
    }

    public String pinCreated(String l) {
        return line(l, "pin.created");
    }

    public String enterPin(String l, int digits) {
        return messages.text(l, "pin.enter", Map.of("digits", digits));
    }

    public String wrongPin(String l, int remaining) {
        return messages.text(l, "pin.wrong", Map.of("remaining", remaining)) + "\n";
    }

    public String pinLocked(String l, String helpline) {
        return withHelpline(l, "pin.locked", helpline);
    }

    // ── Loans ─────────────────────────────────────────────────────────────
    public String selectLoan(String l) {
        return messages.text(l, "loan.select");
    }

    public String noActiveLoan(String l) {
        return messages.text(l, "loan.none_active");
    }

    public String noLoans(String l) {
        return messages.text(l, "loan.none");
    }

    public String loanStatus(String l, ELoanStatus status) {
        return messages.text(l, status.messageKey());
    }

    private String line(String l, String key) {
        return messages.text(l, key) + "\n";
    }

    private String withHelpline(String l, String key, String helpline) {
        return messages.text(l, key, Map.of("helpline", helpline == null ? "" : helpline));
    }
}
