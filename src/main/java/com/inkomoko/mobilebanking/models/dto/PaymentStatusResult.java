package com.inkomoko.mobilebanking.models.dto;

import java.math.BigDecimal;

/** Status of a collection request as reported by the payment gateway (status query / callback). */
public record PaymentStatusResult(String status, String gatewayRef, String telecomRef,
                                  BigDecimal amount, String message) {

    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(status) || "SUCCESSFUL".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        return "FAILED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status)
                || "CANCELLED".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status);
    }
}
