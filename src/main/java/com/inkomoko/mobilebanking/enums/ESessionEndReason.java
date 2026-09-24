package com.inkomoko.mobilebanking.enums;

public enum ESessionEndReason {
    COMPLETED,          // reached a final screen (balance shown, payment initiated, ...)
    USER_EXIT,          // chose Exit
    CONSENT_DECLINED,
    NOT_REGISTERED,
    ACCOUNT_LOCKED,
    ABANDONED,          // no response within the session timeout
    ERROR
}
