package com.inkomoko.mobilebanking.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Portal password policy (single row, id = 1). */
@Entity
@Table(name = "password_policy")
@Getter
@Setter
@NoArgsConstructor
public class PasswordPolicy {

    @Id
    private Integer id;

    private int minLength;
    private boolean requireUpper;
    private boolean requireLower;
    private boolean requireDigit;
    private boolean requireSpecial;
    /** 0 = passwords never expire. */
    private int expiryDays;
    /** The last N passwords cannot be reused. */
    private int historyCount;
    private int maxFailedAttempts;
    private int lockoutMinutes;
    /** Login session (JWT) lifetime in minutes. */
    private int sessionMinutes;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
