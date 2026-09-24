package com.inkomoko.mobilebanking.models.dto;

/** Outcome of asking the telecom to refund a collected repayment. */
public record PaymentReversalResult(boolean accepted, String message) {
}
