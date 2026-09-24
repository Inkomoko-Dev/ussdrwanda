package com.inkomoko.mobilebanking.enums;

public enum EReconciliationAction {
    /** Retry posting the collected funds to Fineract. */
    REPOST,
    /** Funds were posted to Fineract manually — record the Fineract transaction id. */
    MARK_POSTED,
    /** Payment never collected — close as failed. */
    MARK_FAILED,
    /** Funds refunded to the client — close as reversed. */
    MARK_REVERSED
}
