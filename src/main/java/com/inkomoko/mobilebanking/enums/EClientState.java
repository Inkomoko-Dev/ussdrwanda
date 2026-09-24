package com.inkomoko.mobilebanking.enums;

/** Result of identifying the caller (MSISDN → Fineract client + local PIN record). */
public enum EClientState {
    ACTIVE,          // registered, PIN set → PIN entry
    PIN_NOT_SET,     // registered, first-time → PIN setup
    LOCKED,          // PIN locked after too many failed attempts
    NOT_REGISTERED,  // MSISDN not found in Fineract
    INACTIVE         // client exists in Fineract but is not active
}
