package com.inkomoko.mobilebanking.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Record of the privacy-notice acknowledgement shown at the start of every session (ToR 3.1(ix)). */
@Entity
@Table(name = "consent_log")
@Getter
@Setter
@NoArgsConstructor
public class ConsentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String msisdn;
    private String sessionId;
    private boolean accepted;
    private String noticeVersion;
    private LocalDateTime createdAt;

    public ConsentLog(String msisdn, String sessionId, boolean accepted, String noticeVersion, LocalDateTime createdAt) {
        this.msisdn = msisdn;
        this.sessionId = sessionId;
        this.accepted = accepted;
        this.noticeVersion = noticeVersion;
        this.createdAt = createdAt;
    }
}
