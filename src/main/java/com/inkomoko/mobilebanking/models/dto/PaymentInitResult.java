package com.inkomoko.mobilebanking.models.dto;

/** Outcome of asking the payment gateway to push a MoMo debit request to the client's phone. */
public record PaymentInitResult(boolean accepted, String gatewayRef, String message) {
}
