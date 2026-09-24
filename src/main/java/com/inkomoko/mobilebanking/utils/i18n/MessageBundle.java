package com.inkomoko.mobilebanking.utils.i18n;


import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default USSD/SMS texts loaded from {@code classpath:i18n/ussd_<lang>.properties} (UTF-8)
 * for the languages shipped with the release ({@link #BUNDLED}).
 * Pure Java (no Spring) so the bundle tests and tooling can use it directly.
 *
 * Templates use named placeholders, e.g. {@code "Wrong PIN. {remaining} attempt(s) left."}.
 * java.text.MessageFormat is deliberately not used: it treats apostrophes as quotes, and
 * Kinyarwanda/French texts are full of them (n'Amabwiriza, d'Inkomoko).
 */
public final class MessageBundle {

    public static final String BASE = "i18n/ussd_";
    /** Reference language: every key exists in English and every fallback ends here. */
    public static final String REFERENCE = "en";
    /** Languages shipped as bundles in the release. Others are translated in the portal (ussd_message rows). */
    public static final List<String> BUNDLED = List.of("rw", "en", "fr");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_]*)}");

    private final Map<String, Map<String, String>> texts = new HashMap<>();

    private MessageBundle() {
    }

    /** Loads every bundled language; a missing file yields an empty map (caught by the bundle tests). */
    public static MessageBundle load() {
        MessageBundle b = new MessageBundle();
        ClassLoader cl = MessageBundle.class.getClassLoader();
        for (String l : BUNDLED) {
            Map<String, String> map = new TreeMap<>();
            String path = BASE + l + ".properties";
            try (InputStream in = cl.getResourceAsStream(path)) {
                if (in != null) {
                    Properties p = new Properties();
                    p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                    p.stringPropertyNames().forEach(k -> map.put(k, p.getProperty(k)));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read " + path, e);
            }
            b.texts.put(l, Collections.unmodifiableMap(map));
        }
        return b;
    }

    public boolean isBundled(String l) {
        return texts.containsKey(l);
    }

    /** Raw template for this language only, or null. */
    public String template(String l, String key) {
        return texts.getOrDefault(l, Map.of()).get(key);
    }

    /** All keys of a language, sorted. */
    public Set<String> keys(String l) {
        return texts.getOrDefault(l, Map.of()).keySet();
    }

    /** Placeholder names used in a template, in order of first use. */
    public static Set<String> placeholders(String template) {
        Set<String> names = new LinkedHashSet<>();
        if (template == null) return names;
        Matcher m = PLACEHOLDER.matcher(template);
        while (m.find()) names.add(m.group(1));
        return names;
    }

    /** Replaces {name} with its value; unknown placeholders are left as-is so gaps are visible. */
    public static String format(String template, Map<String, ?> params) {
        if (template == null) return null;
        if (params == null || params.isEmpty() || template.indexOf('{') < 0) return template;
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder(template.length() + 32);
        while (m.find()) {
            Object v = params.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? m.group() : String.valueOf(v)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * True when every character is in the GSM 03.38 default alphabet (basic set + extension table).
     * Anything else (accents like e-acute outside the set, curly quotes, emoji) forces UCS-2 and
     * cuts the screen/SMS capacity by more than half.
     */
    public static boolean isGsm7(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (GSM7.indexOf(s.charAt(i)) < 0) return false;
        }
        return true;
    }

    private static final String GSM7 =
            "@£$¥èéùìòÇ\nØø\rÅå"
                    + "Δ_ΦΓΛΩΠΨΣΘΞÆæßÉ"
                    + " !\"#¤%&'()*+,-./0123456789:;<=>?"
                    + "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§"
                    + "¿abcdefghijklmnopqrstuvwxyzäöñüà"
                    + "^{}\\[~]|€"; // extension table (each counts as 2 septets)
}
