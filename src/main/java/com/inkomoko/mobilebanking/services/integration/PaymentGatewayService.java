package com.inkomoko.mobilebanking.services.integration;

import com.inkomoko.mobilebanking.models.RepaymentTransaction;
import com.inkomoko.mobilebanking.models.dto.PaymentInitResult;
import com.inkomoko.mobilebanking.models.dto.PaymentReversalResult;
import com.inkomoko.mobilebanking.models.dto.PaymentStatusResult;

import java.util.Optional;

/**
 * Mobile Money collection (MTN MoMo / Airtel Money) through the Efashe Payment Gateway.
 * The client authorises the debit on their own phone with their MoMo PIN; the outcome
 * arrives asynchronously at /api/payments/callback.
 */
public interface PaymentGatewayService {

    PaymentInitResult requestCollection(RepaymentTransaction tx);

    PaymentStatusResult checkStatus(RepaymentTransaction tx);

    /** Asks the telecom to return collected funds to the client. */
    PaymentReversalResult requestReversal(RepaymentTransaction tx);

    /**
     * Pilot override: asks the gateway to mark a pushed collection as successful without a telecom
     * confirmation. Empty when the override is switched off, unsupported, or refused; the payment
     * then follows the normal status checks.
     */
    default Optional<PaymentStatusResult> markSuccessful(RepaymentTransaction tx) {
        return Optional.empty();
    }

    /**
     * Staff-triggered form of {@link #markSuccessful}: same override, but a refusal is thrown
     * with the gateway's reason instead of being swallowed.
     */
    default PaymentStatusResult requestManualSuccess(RepaymentTransaction tx) {
        throw new IllegalStateException("Mark as paid is only available for Omnichannel payments");
    }
}
