package com.inkomoko.mobilebanking.models.api.portal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Admin API shapes for the "USSD texts" editor. */
public final class MessageDtos {

    private MessageDtos() {
    }

    /** Editor catalogue for one scope (a country code, or "*" = every country). */
    public record MessageCatalog(String country, List<String> languages, Map<String, String> sampleValues,
                                 List<MessageEntry> messages) {
    }

    /** One key; {@code texts} has one entry per language of the scope. */
    public record MessageEntry(String key, String group, boolean sms, List<String> placeholders,
                               Map<String, MessageText> texts) {
    }

    /**
     * defaultText: shipped in the release (null for a language added in the portal).
     * override: set for this scope (null = none).
     * inherited: set for every country ('*') and used because this country has none.
     * effective: what customers see now; null = not translated yet (customers would get English).
     * length: effective with sample values filled in; maxLength depends on the language's character set.
     */
    public record MessageText(String defaultText, String override, String inherited, String effective,
                              boolean translated, int length, int maxLength, String updatedBy,
                              LocalDateTime updatedAt) {
    }

    public record MessageUpdateRequest(@NotBlank @Size(max = 1000) String text) {
    }

    /** Checks a draft without saving it (live validation in the editor). */
    public record MessageCheckResponse(boolean valid, List<String> errors, String preview, int length, int maxLength) {
    }

    public record MessageHistoryView(String language, String country, String action, String oldText,
                                     String newText, String changedBy, LocalDateTime changedAt) {
    }
}
