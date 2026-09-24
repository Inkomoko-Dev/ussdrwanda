package com.inkomoko.mobilebanking.models;

import com.inkomoko.mobilebanking.enums.EApprovalStatus;
import com.inkomoko.mobilebanking.enums.EReconciliationAction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Maker/checker record for manual reconciliation of failed, delayed or disputed repayments.
 * The maker proposes an action; a different user (checker) must approve it before it runs.
 */
@Entity
@Table(name = "reconciliation_action")
@Getter
@Setter
@NoArgsConstructor
public class ReconciliationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String transactionReference;

    @Enumerated(EnumType.STRING)
    private EReconciliationAction action;

    @Enumerated(EnumType.STRING)
    private EApprovalStatus status;

    private String maker;
    private String makerComment;
    private String checker;
    private String checkerComment;
    /** For MARK_POSTED: the Fineract transaction id of the manual posting. */
    private String fineractTxnId;
    private String result;

    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;
}
