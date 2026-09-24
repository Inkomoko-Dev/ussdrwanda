package com.inkomoko.mobilebanking.utils.i18n;

import com.inkomoko.mobilebanking.enums.ECharset;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Validation rules shared by the bundle tests and the portal text editor, so a text an
 * editor saves is held to exactly the same standard as one shipped in a release.
 */
public final class MessageRules {

    /** One GSM-7 USSD screen (FDI gateway / MNO limit). */
    public static final int USSD_MAX = ECharset.GSM7.ussdMax();

    /** Screens the menus always render with the "0. Back" line appended. */
    private static final Set<String> WITH_BACK = Set.of(
            "balance.screen", "repay.amount_prompt", "statement.none", "loan.none", "loan.none_active");

    /** Longest realistic value of every placeholder, used for length checks and previews. */
    public static final Map<String, String> SAMPLE_VALUES = Map.ofEntries(
            Map.entry("account", "000000123"),            // Fineract account no (9 digits)
            Map.entry("amount", "RWF 5,000,000"),         // max repayment
            Map.entry("principal", "RWF 5,000,000"),
            Map.entry("total", "RWF 5,000,000"),
            Map.entry("outstanding", "RWF 5,000,000"),
            Map.entry("overdue", "RWF 5,000,000"),
            Map.entry("installment", "RWF 5,000,000"),
            Map.entry("balance", "RWF 5,000,000"),
            Map.entry("min", "RWF 5,000"),
            Map.entry("max", "RWF 5,000,000"),
            Map.entry("due", "25/09/2026"),
            Map.entry("helpline", "+250 788 000 000"),
            Map.entry("code", "*662*105#"),
            Map.entry("digits", "6"),
            Map.entry("remaining", "3"),
            Map.entry("name", "Jean-Baptist"),            // first name, 12 chars
            Map.entry("wallet", "Airtel Money 0788***456"),
            Map.entry("ref", "IKR260925143012123456"));

    private MessageRules() {
    }

    public static boolean isSms(String key) {
        return key.startsWith("sms.");
    }

    /** Maximum length of the text itself once placeholders are filled with {@link #SAMPLE_VALUES}. */
    public static int maxLength(String key) {
        return maxLength(key, ECharset.GSM7);
    }

    public static int maxLength(String key, ECharset charset) {
        ECharset c = charset == null ? ECharset.GSM7 : charset;
        if (isSms(key)) return c.smsMax();
        return WITH_BACK.contains(key) ? c.ussdMax() - c.backReserve() : c.ussdMax();
    }

    public static String preview(String text) {
        return MessageBundle.format(text, SAMPLE_VALUES);
    }

    /**
     * Problems with {@code text} as a value for {@code key}; empty when valid.
     * {@code reference} is the shipped English template — the placeholders must match it exactly.
     */
    public static List<String> validate(String key, String reference, String text) {
        return validate(key, reference, text, ECharset.GSM7);
    }

    /** As above, for a language with the given charset (UCS-2 languages may use any script but get fewer characters). */
    public static List<String> validate(String key, String reference, String text, ECharset charset) {
        ECharset cs = charset == null ? ECharset.GSM7 : charset;
        List<String> errors = new ArrayList<>();
        if (text == null || text.isBlank()) {
            errors.add("Text is empty");
            return errors;
        }
        if (!text.equals(text.strip())) errors.add("Remove spaces or line breaks at the start/end");
        if (cs == ECharset.GSM7 && !MessageBundle.isGsm7(text)) {
            errors.add("Only GSM-7 characters can be used (write without accents; no curly quotes or emoji). Not allowed: "
                    + nonGsm(text));
        }
        Set<String> expected = new TreeSet<>(MessageBundle.placeholders(reference));
        Set<String> actual = new TreeSet<>(MessageBundle.placeholders(text));
        if (!expected.equals(actual)) {
            Set<String> missing = new TreeSet<>(expected);
            missing.removeAll(actual);
            Set<String> unknown = new TreeSet<>(actual);
            unknown.removeAll(expected);
            if (!missing.isEmpty()) errors.add("Missing placeholder(s) " + braces(missing));
            if (!unknown.isEmpty()) errors.add("Unknown placeholder(s) " + braces(unknown) + "; allowed: " + braces(expected));
        }
        int len = preview(text).length();
        int max = maxLength(key, cs);
        if (len > max) errors.add("Too long: " + len + " characters with realistic values (max " + max + ")");
        return errors;
    }

    private static String nonGsm(String text) {
        StringBuilder sb = new StringBuilder();
        text.codePoints().filter(c -> !MessageBundle.isGsm7(new String(Character.toChars(c))))
                .distinct().forEach(c -> sb.append(sb.isEmpty() ? "" : " ").append(Character.toChars(c)));
        return sb.toString();
    }

    private static String braces(Set<String> names) {
        return String.join(", ", names.stream().map(n -> "{" + n + "}").toList());
    }
}
