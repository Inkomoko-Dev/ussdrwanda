package com.inkomoko.mobilebanking.models;

import com.inkomoko.mobilebanking.utils.i18n.MessageBundle;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Country profile. One row is active per deployment and drives the USSD service
 * (languages, currency, repayment limits, helpline).
 */
@Entity
@Table(name = "country_config")
@Getter
@Setter
@NoArgsConstructor
public class CountryConfig {

    @Id
    private String code;

    private String name;
    private String dialCode;
    private Integer nationalNumberLength;
    private String currency;
    private String timezone;
    /** Ordered, comma-separated language codes (ussd_language.code) — order = numbering on the language screen. */
    private String languages;
    private String mobileNetworks;
    private String helpline;
    /** Digits customers dial, without * and #. Example: 662*105 for *662*105#. */
    private String ussdServiceCode;
    private BigDecimal minRepayment;
    private BigDecimal maxRepayment;
    /** When false, USSD does not offer loan repayment (main menu, loan submenu, next-due shortcut). */
    private boolean repaymentEnabled;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public List<String> languageList() {
        if (languages == null || languages.isBlank()) return List.of(MessageBundle.REFERENCE);
        return Arrays.stream(languages.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .distinct()
                .toList();
    }
}
