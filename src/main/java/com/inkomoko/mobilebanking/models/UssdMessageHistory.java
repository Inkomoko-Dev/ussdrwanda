package com.inkomoko.mobilebanking.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One change to a USSD/SMS text override (UPDATE or REVERT to the shipped default). */
@Entity
@Table(name = "ussd_message_history")
@Getter
@Setter
@NoArgsConstructor
public class UssdMessageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "msg_key")
    private String msgKey;

    /** ISO 639 language code (ussd_language.code), e.g. "rw". */
    private String language;

    private String countryCode;
    private String action;
    private String oldText;
    private String newText;
    private String changedBy;
    private LocalDateTime changedAt;
}
