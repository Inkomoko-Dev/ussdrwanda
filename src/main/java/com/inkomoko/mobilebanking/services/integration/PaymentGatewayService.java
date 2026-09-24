package com.inkomoko.mobilebanking.services.integration;

import com.inkomoko.mobilebanking.models.RepaymentTransaction;
import com.inkomoko.mobilebanking.models.dto.PaymentInitResult;
import com.inkomoko.mobilebanking.models.dto.PaymentStatusResult;

/**
 * Mobile Money collection (MTN MoMo / Airtel Money) through the Efashe Payment Gateway.
 * The client authorises the debit on their own phone with their MoMo PIN; the outcome
 * arrives asynchronously at /api/payments/callback.
 */
public interface PaymentGatewayService {

    PaymentInitResult requestCollection(RepaymentTransaction tx);

    PaymentStatusResult checkStatus(RepaymentTransaction tx);
}
