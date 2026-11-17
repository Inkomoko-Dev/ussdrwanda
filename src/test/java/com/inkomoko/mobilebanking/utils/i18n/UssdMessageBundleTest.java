package com.inkomoko.mobilebanking.utils.i18n;

import com.inkomoko.mobilebanking.enums.ECharset;
import com.inkomoko.mobilebanking.enums.ELoanStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the USSD message bundles (i18n/ussd_*.properties) so a translation change cannot
 * break a screen in production: missing keys, renamed placeholders, non-GSM characters and
 * screens over the 182-character USSD limit all fail the build.
 */
class UssdMessageBundleTest {

    private static final int MAX = MessageRules.USSD_MAX;
    private static final MessageBundle B = MessageBundle.load();

    private static final Map<String, String> WORST = MessageRules.SAMPLE_VALUES;
    /** Native names seeded by V9__ussd_languages.sql (shown on the language screen). */
    private static final Map<String, String> NATIVE_NAMES = Map.of("rw", "Kinyarwanda", "en", "English", "fr", "Francais");

    private static String t(String l, String key) {
        String tpl = B.template(l, key);
        assertTrue(tpl != null, key + " missing in " + l);
        return MessageBundle.format(tpl, WORST);
    }

    private static String back(String l) {
        return "\n" + t(l, "nav.back");
    }

    @Test
    void everyLanguageHasTheSameKeysAsEnglish() {
        Set<String> reference = B.keys(MessageBundle.REFERENCE);
        assertFalse(reference.isEmpty(), "English bundle not found on the classpath");
        for (String l : MessageBundle.BUNDLED) {
            Set<String> missing = new TreeSet<>(reference);
            missing.removeAll(B.keys(l));
            Set<String> extra = new TreeSet<>(B.keys(l));
            extra.removeAll(reference);
            assertTrue(missing.isEmpty(), l + " is missing keys " + missing);
            assertTrue(extra.isEmpty(), l + " has keys not in English " + extra);
        }
    }

    @Test
    void placeholdersMatchEnglish() {
        for (String key : B.keys(MessageBundle.REFERENCE)) {
            Set<String> expected = new TreeSet<>(MessageBundle.placeholders(B.template(MessageBundle.REFERENCE, key)));
            for (String l : MessageBundle.BUNDLED) {
                Set<String> actual = new TreeSet<>(MessageBundle.placeholders(B.template(l, key)));
                assertEquals(expected, actual, "placeholders of " + key + " in " + l);
            }
        }
    }

    @Test
    void everyPlaceholderHasAWorstCaseSample() {
        for (String l : MessageBundle.BUNDLED) {
            for (String key : B.keys(l)) {
                String out = MessageBundle.format(B.template(l, key), WORST);
                assertTrue(MessageBundle.placeholders(out).isEmpty(), "unfilled placeholder in " + key + ": " + out);
            }
        }
    }

    @Test
    void textsAreGsm7AndTrimmed() {
        for (String l : MessageBundle.BUNDLED) {
            for (String key : B.keys(l)) {
                String v = B.template(l, key);
                assertFalse(v.isBlank(), l + " " + key + " is empty");
                assertEquals(v.strip(), v, l + " " + key + " has leading/trailing whitespace");
                assertTrue(MessageBundle.isGsm7(v), l + " " + key + " has non GSM-7 characters: " + v);
            }
        }
    }

    @Test
    void loanStatusLabelsExistForEveryStatus() {
        for (ELoanStatus s : ELoanStatus.values()) {
            for (String l : MessageBundle.BUNDLED) t(l, s.messageKey());
        }
    }

    @Test
    void singleScreensFitOneUssdPage() {
        for (String l : MessageBundle.BUNDLED) {
            for (String key : B.keys(l)) {
                if (key.startsWith("sms.")) continue;
                assertFits(l + " " + key, t(l, key));
            }
        }
    }

    @Test
    void composedScreensFitOneUssdPage() {
        List<String> all = MessageBundle.BUNDLED;
        String titles = String.join(" / ", all.stream().map(l -> t(l, "language.title")).toList());
        StringBuilder langMenu = new StringBuilder(titles);
        for (int i = 0; i < all.size(); i++) langMenu.append('\n').append(i + 1).append(". ").append(NATIVE_NAMES.get(all.get(i)));
        String invalidAll = String.join(" / ", all.stream().map(l -> t(l, "common.invalid")).toList());
        assertFits("language menu", langMenu.toString());
        assertFits("invalid + language menu", invalidAll + "\n" + langMenu);

        for (String l : all) {
            String c = l + " ";
            String inv = t(l, "common.invalid") + "\n";
            assertFits(c + "invalid + consent", inv + t(l, "consent.text"));
            assertFits(c + "invalid + main menu", inv + t(l, "menu.main"));
            assertFits(c + "invalid + main menu (no repay)", inv + t(l, "menu.main.no_repay"));
            assertFits(c + "pin created + main menu", t(l, "pin.created") + "\n" + t(l, "menu.main"));
            assertFits(c + "loan menu (no repay)", t(l, "menu.loan.no_repay") + back(l));
            assertFits(c + "due (no repay)", t(l, "account.due.no_repay") + back(l));
            assertFits(c + "wrong pin + enter pin", t(l, "pin.wrong") + "\n" + t(l, "pin.enter"));
            for (String err : List.of("pin.bad_format", "pin.weak", "pin.mismatch")) {
                assertFits(c + err + " + pin setup", t(l, err) + "\n" + t(l, "pin.setup"));
            }
            assertFits(c + "balance", t(l, "balance.screen") + back(l));
            assertFits(c + "amount prompt", t(l, "repay.amount_prompt") + back(l));
            for (String err : List.of("repay.invalid_amount", "repay.below_min", "repay.above_balance", "repay.above_max")) {
                assertFits(c + err, t(l, err) + "\n" + t(l, "repay.enter_amount") + back(l));
            }
            assertFits(c + "confirm", t(l, "repay.confirm") + "\n" + t(l, "repay.confirm_options"));
            assertFits(c + "cancelled", t(l, "repay.cancelled") + " " + t(l, "common.thank_you"));
            assertFits(c + "sms sent", t(l, "statement.sms_sent") + " " + t(l, "common.thank_you"));
            assertFits(c + "no loans", t(l, "loan.none") + back(l));
            assertFits(c + "help (no FAQ)", t(l, "help.header") + "\n" + t(l, "help.call") + back(l));

            // Mini statement: 5 rows "dd/MM/yy amount ref6"
            StringBuilder st = new StringBuilder(t(l, "statement.header"));
            for (int i = 0; i < 5; i++) st.append("\n25/09/26 250,000 123456");
            assertFits(c + "mini statement", st.append('\n').append(t(l, "statement.sms_option")).append(back(l)).toString());

            // Loan status: the 5 most relevant loans, one per status (rank order)
            StringBuilder ls = new StringBuilder(t(l, "loan.status.header"));
            for (ELoanStatus s : List.of(ELoanStatus.IN_ARREARS, ELoanStatus.ACTIVE, ELoanStatus.APPROVED,
                    ELoanStatus.UNDER_REVIEW, ELoanStatus.SETTLED)) {
                ls.append('\n').append(WORST.get("account")).append(": ").append(t(l, s.messageKey()));
            }
            assertFits(c + "loan status", ls.append(back(l)).toString());

            // Selection lists: "product (loan id)", name capped at 14 chars (CommonMenus.loanListLabel)
            String loanLabel = "Refugee Enterp (000000123)";
            StringBuilder sel = new StringBuilder(t(l, "loan.select"));
            for (int i = 1; i <= 5; i++) sel.append('\n').append(i).append(". ").append(loanLabel);
            assertFits(c + "loan selection", sel.append(back(l)).toString());
        }
    }

    @Test
    void shippedTextsPassTheEditorRules() {
        for (String l : MessageBundle.BUNDLED) {
            for (String key : B.keys(l)) {
                List<String> errors = MessageRules.validate(key, B.template(MessageBundle.REFERENCE, key), B.template(l, key));
                assertTrue(errors.isEmpty(), l + " " + key + ": " + errors);
            }
        }
    }

    @Test
    void editorRulesRejectBadTexts() {
        String ref = B.template(MessageBundle.REFERENCE, "pin.wrong");
        assertFalse(MessageRules.validate("pin.wrong", ref, "PIN incorrect.").isEmpty(), "missing placeholder");
        assertFalse(MessageRules.validate("pin.wrong", ref, "{remaining} {foo}").isEmpty(), "unknown placeholder");
        assertFalse(MessageRules.validate("pin.wrong", ref, "{remaining} r\u00e9ussi \u00ea").isEmpty(), "non GSM-7");
        assertFalse(MessageRules.validate("pin.wrong", ref, "{remaining} " + "x".repeat(200)).isEmpty(), "too long");
        assertTrue(MessageRules.validate("pin.wrong", ref, "Reka! {remaining} gusa.").isEmpty(), "valid");
    }

    @Test
    void ucs2LanguagesMayUseAnyScriptButGetShorterScreens() {
        String ref = B.template(MessageBundle.REFERENCE, "pin.wrong");
        String arabic = "\u0631\u0642\u0645 \u0633\u0631\u064a \u062e\u0627\u0637\u0626. {remaining}";
        assertFalse(MessageRules.validate("pin.wrong", ref, arabic, ECharset.GSM7).isEmpty(), "GSM-7 rejects Arabic");
        assertTrue(MessageRules.validate("pin.wrong", ref, arabic, ECharset.UCS2).isEmpty(), "UCS-2 accepts Arabic");
        String longText = "{remaining} " + "x".repeat(100);
        assertTrue(MessageRules.validate("pin.wrong", ref, longText, ECharset.GSM7).isEmpty(), "fits GSM-7");
        assertFalse(MessageRules.validate("pin.wrong", ref, longText, ECharset.UCS2).isEmpty(), "too long for UCS-2");
        assertEquals(459, MessageRules.maxLength("sms.repay.failed", ECharset.GSM7));
        assertEquals(70, MessageRules.maxLength("balance.screen", ECharset.UCS2));
    }

    @Test
    void formatLeavesApostrophesAndUnknownPlaceholdersAlone() {
        Map<String, Object> p = new HashMap<>();
        p.put("n", 3);
        assertEquals("n'Amabwiriza 3 {x}", MessageBundle.format("n'Amabwiriza {n} {x}", p));
        assertEquals(List.of("a", "b"), new ArrayList<>(MessageBundle.placeholders("{a} {b} {a}")));
    }

    private static void assertFits(String what, String screen) {
        assertTrue(screen.length() <= MAX, what + " is " + screen.length() + " chars (max " + MAX + "):\n" + screen);
    }
}
