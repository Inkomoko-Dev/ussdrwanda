package com.inkomoko.mobilebanking.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Extracts user answers from the gateway's {@code userInput} / Comviva {@code input}.
 *
 * FDI sends the full dial string, with every answer so far appended:
 *
 *   serviceCode "RW|MTN|662|105", userInput "*662*105#"      → []          (first request)
 *   serviceCode "RW|MTN|662|105", userInput "*662*105*1#"    → ["1"]
 *   serviceCode "RW|MTN|662|105", userInput "*662*105*1*1#"  → ["1", "1"]
 *
 * Comviva (MTN/Airtel) often sends the shortcode alone on the first dial, without {@code *#}:
 *
 *   serviceCode "311", input "311"      → []
 *   serviceCode "311", input "311*1"    → ["1"]
 *   serviceCode "311", input "1"        → ["1"]   (ongoing session)
 *
 * The service-code segments are removed when they prefix the input; the last remaining segment
 * is the user's latest answer. A plain cumulative form without the code ("1*1") is also accepted.
 */
public final class UssdInputParser {

    private UssdInputParser() {
    }

    public static List<String> segments(String userInput, String serviceCode) {
        if (userInput == null) return List.of();
        String raw = userInput.trim();
        if (raw.isEmpty()) return List.of();

        String s = raw;
        while (s.startsWith("*")) s = s.substring(1);
        if (s.endsWith("#")) s = s.substring(0, s.length() - 1);

        List<String> parts = new ArrayList<>();
        for (String p : s.split("\\*", -1)) {
            if (!p.isEmpty()) parts.add(p);
        }

        // Strip leading shortcode segments whether the gateway sent *311# or plain 311.
        if (serviceCode != null && !serviceCode.isBlank()) {
            List<String> code = codeSegments(serviceCode);
            if (!code.isEmpty() && parts.size() >= code.size() && parts.subList(0, code.size()).equals(code)) {
                parts = new ArrayList<>(parts.subList(code.size(), parts.size()));
            }
        }
        return parts;
    }

    /** True when {@code input} begins with the configured shortcode (e.g. {@code 311} or {@code *662*105#}). */
    public static boolean startsWithServiceCode(String input, String serviceCode) {
        if (input == null || input.isBlank() || serviceCode == null || serviceCode.isBlank()) return false;
        List<String> code = codeSegments(serviceCode);
        if (code.isEmpty()) return false;
        String s = input.trim();
        while (s.startsWith("*")) s = s.substring(1);
        if (s.endsWith("#")) s = s.substring(0, s.length() - 1);
        List<String> parts = new ArrayList<>();
        for (String p : s.split("\\*", -1)) {
            if (!p.isEmpty()) parts.add(p);
        }
        return parts.size() >= code.size() && parts.subList(0, code.size()).equals(code);
    }

    /**
     * Normalises any service-code notation to "662*105" / "311":
     * "RW|MTN|662|105" → "662*105", "*662*105#" → "662*105", "311" → "311".
     * Non-numeric parts (country, network) are dropped. Returns "" if there are no digits.
     */
    public static String normalizeServiceCode(String serviceCode) {
        if (serviceCode == null) return "";
        return String.join("*", codeSegments(serviceCode));
    }

    static List<String> codeSegments(String serviceCode) {
        if (serviceCode == null) return List.of();
        return Arrays.stream(serviceCode.trim().split("[|*#\\s]+"))
                .filter(x -> x.matches("[0-9]+"))
                .toList();
    }
}
