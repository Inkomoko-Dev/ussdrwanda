package com.inkomoko.mobilebanking.models.dto;

/**
 * Response expected by the FDI USSD gateway.
 * continueSession: 1 = keep the dialogue open, 0 = final screen (end session).
 */
public record UssdResponse(
        String sessionId,
        String gwRef,
        String appRef,
        String appTstamp,
        String text,
        int continueSession
) {
}
