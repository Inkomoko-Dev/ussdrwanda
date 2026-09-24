package com.inkomoko.mobilebanking.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A portal override of one shipped USSD/SMS text (see i18n/ussd_*.properties). */
@Entity
@Table(name = "ussd_message")
@Getter
@Setter
@NoArgsConstructor
public class UssdMessage {

    /** country_code value meaning "every country". */
    public static final String ALL_COUNTRIES = "*";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "msg_key")
    private String msgKey;

    /** ISO 639 language code (ussd_language.code), e.g. "rw". */
    private String language;

    private String countryCode;
    private String text;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
