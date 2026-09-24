package com.inkomoko.mobilebanking.enums;

public enum EPinStatus {
    /** Onboarded by staff (or PIN reset): the client creates a PIN on their next dial. */
    NOT_SET,
    ACTIVE,
    LOCKED,
    /** USSD access switched off by staff (portal). */
    DISABLED
}
