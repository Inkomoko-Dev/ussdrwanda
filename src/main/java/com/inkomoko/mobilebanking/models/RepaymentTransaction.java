package com.inkomoko.mobilebanking.models;

import com.inkomoko.mobilebanking.enums.ETelecom;
import com.inkomoko.mobilebanking.enums.ETransactionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One Mobile Money loan repayment, tracked end-to-end: USSD confirm → MoMo push →
 * gateway callback → Fineract posting. {@link #reference} is also sent to Fineract as the
 * transaction externalId, so a retry can never create a duplicate posting.
 */
@Entity
@Table(name = "repayment_transaction")
@Getter
@Setter
@NoArgsConstructor
public class RepaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String reference;

    private String sessionId;
    private String msisdn;
    private Long clientId;
    private Long loanId;
    private String loanAccountNo;
    private BigDecimal amount;
    private String currency;

    @Enumerated(EnumType.STRING)
    private ETelecom telecom;

    @Enumerated(EnumType.STRING)
    private ETransactionStatus status;

    /** Payment gateway's reference for the collection request. */
    private String gatewayRef;
    /** MNO (MTN / Airtel) transaction id reported in the callback. */
    private String telecomRef;
    /** Fineract loan transaction id once posted. */
    private String fineractTxnId;

    private int postingAttempts;
    private String failureReason;
    private BigDecimal balanceAfter;

    /** Explicit per-transaction consent (client pressed Confirm). */
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime paidAt;
    private LocalDateTime postedAt;
}
