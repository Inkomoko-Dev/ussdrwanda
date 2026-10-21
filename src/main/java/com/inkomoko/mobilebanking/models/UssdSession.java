package com.inkomoko.mobilebanking.models;

import com.inkomoko.mobilebanking.enums.EClientState;
import com.inkomoko.mobilebanking.enums.ESessionEndReason;
import com.inkomoko.mobilebanking.enums.ETelecom;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Full state of one USSD dialogue (one row per gateway sessionId).
 *
 * level:
 *   0 = new session          → language screen
 *   1 = language shown       → awaiting 1/2/3
 *   2 = T&C / consent shown  → awaiting 1 (accept) / 2 (exit), in the chosen language
 *   3 = authentication       → PIN setup (first time) or PIN entry
 *   4 = main menu            → awaiting service choice
 *   5 = inside a service     → {@link #service} + {@link #step} drive the sub-flow
 */
@Entity
@Table(name = "ussd_session")
@Getter
@Setter
@NoArgsConstructor
public class UssdSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─── Gateway identifiers ───────────────────────────────────────────────
    private String sessionId;
    private String msisdn;
    private String gwRef;
    /** Our reference returned to the gateway as appRef (reconciliation). */
    private String appRef;
    private String networkCode;

    // ─── Navigation ────────────────────────────────────────────────────────
    private Integer level = 0;
    private String service;            // balance | status | statement | repay | help
    private String step;               // sub-step inside the service

    /** ISO 639 language code (ussd_language.code), e.g. "rw". */
    private String language;

    // ─── Client identification / authentication ────────────────────────────
    @Enumerated(EnumType.STRING)
    private EClientState clientState;
    private Long clientId;             // Fineract client id
    private String clientName;
    private boolean consentAccepted;
    private boolean authenticated;

    /** bcrypt hash of the first PIN entry during first-time setup — never the raw PIN. */
    @ToString.Exclude
    private String pendingPinHash;

    // ─── Loan / repayment working data ─────────────────────────────────────
    private Long loanId;
    private String loanAccountNo;
    /** Comma-separated loan ids in the order they were listed on screen (selection mapping). */
    private String loanOptions;
    private BigDecimal amount;
    private BigDecimal outstanding;

    @Enumerated(EnumType.STRING)
    private ETelecom telecom;
    /** Phone that receives the Mobile Money prompt. Null means the caller's own number. */
    @Column(length = 20)
    private String payerMsisdn;

    // ─── Input tracking / idempotency ──────────────────────────────────────
    /** Number of user-input segments already consumed from the cumulative userInput. */
    private Integer processedInputs = 0;
    @ToString.Exclude
    private String lastRawInput;       // may contain a PIN — never logged
    private String lastResponse;
    private Integer lastContinueSession;

    // ─── Lifecycle / metrics ───────────────────────────────────────────────
    private boolean closeCurrentSession;
    @Enumerated(EnumType.STRING)
    private ESessionEndReason endReason;
    private Integer requestCount = 0;
    private Long totalProcessingMs = 0L;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime endedAt;

    /** Marks the session as finished; the response for this request ends the dialogue. */
    public void end(ESessionEndReason reason) {
        this.closeCurrentSession = true;
        if (this.endReason == null) this.endReason = reason;
    }

    /** Clears per-service working data when returning to the main menu. */
    public void resetServiceState() {
        this.service = null;
        this.step = null;
        this.loanId = null;
        this.loanAccountNo = null;
        this.loanOptions = null;
        this.amount = null;
        this.outstanding = null;
        this.payerMsisdn = null;
        this.telecom = null;
    }
}
