package com.inkomoko.mobilebanking.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Persists the client's preferred USSD language across sessions. */
@Entity
@Table(name = "language_setting")
@Getter
@Setter
@NoArgsConstructor
public class LanguageSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String msisdn;

    /** ISO 639 language code (ussd_language.code), e.g. "rw". */
    private String language;

    private LocalDateTime updatedAt;

    public LanguageSetting(String msisdn, String language, LocalDateTime updatedAt) {
        this.msisdn = msisdn;
        this.language = language;
        this.updatedAt = updatedAt;
    }
}
