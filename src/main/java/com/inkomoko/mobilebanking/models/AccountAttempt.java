package com.inkomoko.mobilebanking.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Consecutive failed PIN attempts per MSISDN.
 * Deleted on successful login; when attempts reach the configured maximum the PIN is locked.
 */
@Entity
@Table(name = "account_attempt")
@Getter
@Setter
@NoArgsConstructor
public class AccountAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String msisdn;

    private int attempts;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AccountAttempt(String msisdn, int attempts, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.msisdn = msisdn;
        this.attempts = attempts;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
