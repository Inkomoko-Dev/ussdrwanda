package com.inkomoko.mobilebanking.enums;

public enum EPinStatus {
    /** No PIN yet, or reset by support — client goes through self-service setup on next dial. */
    NOT_SET,
    ACTIVE,
    LOCKED
}
