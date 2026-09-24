package com.inkomoko.mobilebanking.models.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Callback from the payment gateway once the client approves / declines the MoMo prompt.
 * Field names to be aligned with the Efashe Payment Gateway callback specification.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentCallbackRequest {
    /** Our transaction reference (sent when the push was requested). */
    private String reference;
    private String gatewayRef;
    /** SUCCESS | FAILED (also accepts SUCCESSFUL / REJECTED / CANCELLED / EXPIRED). */
    private String status;
    private String telecomRef;
    private BigDecimal amount;
    private String message;
}
