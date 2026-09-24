package com.inkomoko.mobilebanking.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Extracts user answers from the gateway's {@code userInput}.
 *
 * The FDI gateway sends an empty string on the first request and afterwards the cumulative
 * input joined with '*'. It may also include the dial string (e.g. "*311*1*2#" or the sample
 * "*662*101*1#" with serviceCode "662*101"); in that case the service-code segments are removed.
 */
public final class UssdInputParser {

    private UssdInputParser() {
    }

    public static List<String> segments(String userInput, String serviceCode) {
        if (userInput == null) return List.of();
        String raw = userInput.trim();
        if (raw.isEmpty()) return List.of();

        boolean dialString = raw.startsWith("*") || raw.endsWith("#");
        String s = raw;
        while (s.startsWith("*")) s = s.substring(1);
        if (s.endsWith("#")) s = s.substring(0, s.length() - 1);

        List<String> parts = new ArrayList<>();
        for (String p : s.split("\\*", -1)) {
            if (!p.isEmpty()) parts.add(p);
        }

        if (dialString && serviceCode != null && !serviceCode.isBlank()) {
            List<String> code = codeSegments(serviceCode);
            if (!code.isEmpty() && parts.size() >= code.size() && parts.subList(0, code.size()).equals(code)) {
                parts = new ArrayList<>(parts.subList(code.size(), parts.size()));
            }
        }
        return parts;
    }

    static List<String> codeSegments(String serviceCode) {
        String c = serviceCode.trim();
        while (c.startsWith("*")) c = c.substring(1);
        if (c.endsWith("#")) c = c.substring(0, c.length() - 1);
        if (c.isEmpty()) return List.of();
        return Arrays.stream(c.split("\\*")).filter(x -> !x.isEmpty()).toList();
    }
}
