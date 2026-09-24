package com.inkomoko.mobilebanking.models;

import com.inkomoko.mobilebanking.enums.EChannel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Immutable audit record (session activity, authentication attempts, transactions, admin actions).
 * No setters; the database also rejects UPDATE/DELETE on this table (see V1 migration trigger).
 *
 * actor    — MSISDN for USSD, operator username for WEB, "SYSTEM" for callbacks / jobs
 * action   — e.g. LOGIN_SUCCESS, LOGIN_FAILED, PIN_LOCKED, REPAYMENT_INITIATED
 * resource — target entity (loan id, transaction reference, ...)
 */
@Entity
@Table(name = "audit_log")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String actor;

    @Enumerated(EnumType.STRING)
    private EChannel channel;

    private String action;
    private String resource;
    private String sessionId;
    private boolean success;
    private String detail;
    private LocalDateTime loggedAt;
}
