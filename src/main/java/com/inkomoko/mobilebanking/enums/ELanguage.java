package com.inkomoko.mobilebanking.enums;

/** Menu languages, in the order shown on the language screen (1, 2, 3). */
public enum ELanguage {
    KINYARWANDA, ENGLISH, FRENCH;

    public static ELanguage fromMenuChoice(String input) {
        if (input == null) return null;
        return switch (input.trim()) {
            case "1" -> KINYARWANDA;
            case "2" -> ENGLISH;
            case "3" -> FRENCH;
            default -> null;
        };
    }

    /** Picks the text matching this language. */
    public String pick(String rw, String en, String fr) {
        return switch (this) {
            case KINYARWANDA -> rw;
            case ENGLISH -> en;
            case FRENCH -> fr;
        };
    }
}
