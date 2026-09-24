package com.inkomoko.mobilebanking.models;

import com.inkomoko.mobilebanking.enums.EPinStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/** USSD PIN credential for a client, keyed by MSISDN and bound to the Fineract client id. */
@Entity
@Table(name = "client_pin")
@Getter
@Setter
@NoArgsConstructor
public class ClientPin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String msisdn;

    private Long clientId;

    /** bcrypt hash (salted, one-way). */
    @ToString.Exclude
    private String pinHash;

    @Enumerated(EnumType.STRING)
    private EPinStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lockedAt;
    private LocalDateTime lastLoginAt;

    // ─── Onboarding metadata ────────────────────────────────────────────────
    private String clientName;
    private String email;
    /** SELF (registered on first dial) | PORTAL (onboarded by staff). */
    private String registrationChannel;
    private String onboardedBy;
    private LocalDateTime onboardedAt;
}
