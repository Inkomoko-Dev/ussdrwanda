package com.inkomoko.mobilebanking.models;

import com.inkomoko.mobilebanking.enums.ECharset;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A language the USSD service can speak. Kinyarwanda, English and French ship with the release
 * (built-in bundles); others are added in the portal and translated under "USSD & SMS texts".
 */
@Entity
@Table(name = "ussd_language")
@Getter
@Setter
@NoArgsConstructor
public class UssdLanguage {

    /** ISO 639-1 (or 639-3) code, lower case, e.g. "rw", "sw", "ar". */
    @Id
    private String code;

    /** Name in the language itself, shown on the USSD language screen (e.g. "Kiswahili"). */
    private String nativeName;

    /** Name in English, shown in the portal. */
    private String englishName;

    @Enumerated(EnumType.STRING)
    private ECharset charset;

    /** Shipped with the release as i18n/ussd_&lt;code&gt;.properties; cannot be removed. */
    private boolean builtIn;

    /** Inactive languages cannot be enabled for a country. */
    private boolean active;

    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
