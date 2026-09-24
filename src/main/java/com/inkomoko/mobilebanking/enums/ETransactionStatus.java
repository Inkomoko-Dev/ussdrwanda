package com.inkomoko.mobilebanking.enums;

/**
 * Lifecycle of a Mobile Money loan repayment.
 *
 * INITIATED      → row written, push not yet accepted by the gateway
 * PENDING        → push sent; waiting for the client to authorise with their MoMo PIN
 * PAID           → gateway confirmed funds collected; Fineract posting in progress
 * POSTED         → repayment posted to Fineract (final, success)
 * POSTING_FAILED → funds collected but Fineract posting failed; retried automatically
 * NEEDS_REVIEW   → automatic handling exhausted; maker/checker reconciliation required
 * FAILED         → payment declined / cancelled / timed out (final, client not debited)
 * REVERSED       → funds returned to the client after review (final)
 */
public enum ETransactionStatus {
    INITIATED, PENDING, PAID, POSTED, POSTING_FAILED, NEEDS_REVIEW, FAILED, REVERSED;

    public boolean isFinal() {
        return this == POSTED || this == FAILED || this == REVERSED;
    }
}
