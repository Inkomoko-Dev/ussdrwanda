package com.inkomoko.mobilebanking.services.integration;

import com.inkomoko.mobilebanking.models.RepaymentTransaction;
import com.inkomoko.mobilebanking.models.dto.PaymentInitResult;
import com.inkomoko.mobilebanking.models.dto.PaymentReversalResult;
import com.inkomoko.mobilebanking.models.dto.PaymentStatusResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mobile Money repayments through Omnichannel (inkomoko.payment.mode=omnichannel).
 *
 * POST /ussd/payment/initiate sends the prompt to the payer's phone and returns a requestId, kept
 * as the gateway reference. GET /transactions/{requestId} reports PENDING, SUCCESS or FAILED.
 * The Payment Hub posts successful payments to the loan and Omnichannel texts the customer, so
 * there is no posting or refund call here. A long PENDING is Omnichannel reconciling, not a decline.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "inkomoko.payment.mode", havingValue = "omnichannel", matchIfMissing = true)
public class OmnichannelPaymentGatewayService implements PaymentGatewayService {

    private final OmnichannelClient api;

    public OmnichannelPaymentGatewayService(OmnichannelClient api) {
        this.api = api;
    }

    @Override
    public PaymentInitResult requestCollection(RepaymentTransaction tx) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phoneNumber", tx.getMsisdn());
        body.put("accountNumber", tx.getLoanAccountNo());
        body.put("amount", OmnichannelClient.amount(tx.getAmount()));
        body.put("currency", tx.getCurrency());
        body.put("narrative", "Loan repayment " + tx.getReference());
        body.put("channel", api.channel());
        try {
            Map<String, Object> res = map(api.post("/ussd/payment/initiate", body));
            String requestId = str(res.get("requestId"));
            String status = str(res.get("status"));
            boolean accepted = requestId != null && !Boolean.FALSE.equals(res.get("success"))
                    && !"FAILED".equalsIgnoreCase(status);
            return new PaymentInitResult(accepted, requestId,
                    firstNonNull(str(res.get("message")), str(res.get("hubResponseMessage"))));
        } catch (OmnichannelException e) {
            log.error("Omnichannel payment initiate failed ref={} errorCode={} error={}",
                    tx.getReference(), e.errorCode(), e.getMessage());
            return new PaymentInitResult(false, null, e.getMessage());
        }
    }

    @Override
    public PaymentStatusResult checkStatus(RepaymentTransaction tx) {
        if (tx.getGatewayRef() == null || tx.getGatewayRef().isBlank()) {
            return new PaymentStatusResult("UNKNOWN", null, null, null, "No Omnichannel requestId on this payment");
        }
        try {
            Map<String, Object> res = map(api.get("/transactions/" + tx.getGatewayRef(), null));
            Object amount = res.get("amount");
            return new PaymentStatusResult(str(res.get("status")), tx.getGatewayRef(),
                    firstNonNull(str(res.get("mpesaRef")), str(res.get("externalId"))),
                    amount == null ? null : new BigDecimal(amount.toString()),
                    str(res.get("failureReason")));
        } catch (OmnichannelException e) {
            log.warn("Omnichannel status check failed ref={} requestId={} error={}",
                    tx.getReference(), tx.getGatewayRef(), e.getMessage());
            return new PaymentStatusResult("UNKNOWN", tx.getGatewayRef(), null, null, e.getMessage());
        }
    }

    @Override
    public PaymentReversalResult requestReversal(RepaymentTransaction tx) {
        return new PaymentReversalResult(false, "Refunds are not available through Omnichannel");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
