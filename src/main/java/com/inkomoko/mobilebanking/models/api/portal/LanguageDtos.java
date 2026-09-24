package com.inkomoko.mobilebanking.models.api.portal;

import com.inkomoko.mobilebanking.enums.ECharset;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Admin API shapes for USSD languages. */
public final class LanguageDtos {

    private LanguageDtos() {
    }

    /**
     * translated / total: texts available in this language (shipped or translated for all countries).
     * usedBy: country codes that offer this language on USSD.
     */
    public record LanguageView(String code, String nativeName, String englishName, ECharset charset, boolean builtIn,
                               boolean active, int translated, int total, List<String> usedBy, String createdBy,
                               LocalDateTime createdAt) {
    }

    public record LanguageCreateRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z]{2,3}", message = "must be a 2 or 3 letter ISO 639 code") String code,
            @NotBlank @Size(max = 30) String nativeName,
            @NotBlank @Size(max = 60) String englishName,
            @NotNull ECharset charset) {
    }

    public record LanguageUpdateRequest(@NotBlank @Size(max = 30) String nativeName,
                                        @NotBlank @Size(max = 60) String englishName,
                                        @NotNull ECharset charset,
                                        boolean active) {
    }
}
