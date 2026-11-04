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
import java.util.Optional;

/**
 * Mobile Money repayments through Omnichannel (inkomoko.payment.mode=omnichannel).
 *
 * POST /ussd/payment/initiate sends the prompt to the payer's phone and returns a requestId, kept
 * as the gateway reference. GET /transactions/{requestId} reports PENDING, SUCCESS or FAILED.
 * The Payment Hub posts successful payments to the loan and Omnichannel texts the customer, so
 * there is no posting or refund call here. A long PENDING is Omnichannel reconciling, not a decline.
 * While the telecom side is not live, POST /transactions/{requestId}/manual-success completes a
 * pushed payment (see {@link #markSuccessful}).
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
            log.info("CBS payment initiate ref={} payer={} account={} amount={} -> requestId={} status={} accepted={} hub={} {}",
                    tx.getReference(), tx.getMsisdn(), tx.getLoanAccountNo(), tx.getAmount(), requestId, status, accepted,
                    res.get("hubResponseCode"), res.get("hubResponseMessage"));
            return new PaymentInitResult(accepted, requestId,
                    firstNonNull(str(res.get("message")), str(res.get("hubResponseMessage"))));
        } catch (OmnichannelException e) {
            // A refused push may still name Omnichannel's transaction; keep it so staff can mark it as paid.
            String requestId = str(map(e.data()).get("requestId"));
            log.error("Omnichannel payment initiate failed ref={} errorCode={} requestId={} error={}",
                    tx.getReference(), e.errorCode(), requestId, e.getMessage());
            return new PaymentInitResult(false, requestId, e.getMessage());
        }
    }

    @Override
    public PaymentStatusResult checkStatus(RepaymentTransaction tx) {
        if (tx.getGatewayRef() == null || tx.getGatewayRef().isBlank()) {
            return new PaymentStatusResult("UNKNOWN", null, null, null, "No Omnichannel requestId on this payment");
        }
        try {
            Map<String, Object> res = map(api.get("/transactions/" + tx.getGatewayRef(), null));
            log.info("CBS payment status ref={} requestId={} -> status={} amount={} mpesaRef={} failureReason={}",
                    tx.getReference(), tx.getGatewayRef(), res.get("status"), res.get("amount"), res.get("mpesaRef"),
                    res.get("failureReason"));
            return toStatus(tx, res);
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

    /**
     * POST /transactions/{requestId}/manual-success, only while inkomoko.omnichannel.manual-success-enabled
     * is true. Omnichannel answers 403 MANUAL_SUCCESS_DISABLED once its own switch is off; that and any
     * other error return empty so the payment carries on with normal status checks.
     */
    @Override
    public Optional<PaymentStatusResult> markSuccessful(RepaymentTransaction tx) {
        if (!api.manualSuccessEnabled() || tx.getGatewayRef() == null || tx.getGatewayRef().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(callManualSuccess(tx));
        } catch (OmnichannelException e) {
            if ("MANUAL_SUCCESS_DISABLED".equalsIgnoreCase(e.errorCode())) {
                log.warn("Omnichannel refused manual success ref={} requestId={}: switched off on their side;"
                        + " set OMNICHANNEL_MANUAL_SUCCESS_ENABLED=false", tx.getReference(), tx.getGatewayRef());
            } else {
                log.warn("Omnichannel manual success failed ref={} requestId={} errorCode={} error={}",
                        tx.getReference(), tx.getGatewayRef(), e.errorCode(), e.getMessage());
            }
            return Optional.empty();
        }
    }

    @Override
    public PaymentStatusResult requestManualSuccess(RepaymentTransaction tx) {
        if (!api.manualSuccessEnabled()) {
            throw new IllegalStateException("Mark as paid is switched off (OMNICHANNEL_MANUAL_SUCCESS_ENABLED=false)");
        }
        if (tx.getGatewayRef() == null || tx.getGatewayRef().isBlank()) {
            // manual-success is addressed by Omnichannel's requestId; without one there is no URL to call.
            throw new IllegalStateException("Omnichannel returned no transaction id (requestId) for this payment, so it"
                    + " cannot be marked as paid on their side. Their reply was: "
                    + (tx.getFailureReason() == null ? "none" : tx.getFailureReason()));
        }
        try {
            return callManualSuccess(tx);
        } catch (OmnichannelException e) {
            String reason = "MANUAL_SUCCESS_DISABLED".equalsIgnoreCase(e.errorCode())
                    ? "Omnichannel has switched manual success off; set OMNICHANNEL_MANUAL_SUCCESS_ENABLED=false"
                    : e.getMessage();
            throw new IllegalStateException("Omnichannel refused to mark the payment as paid: " + reason);
        }
    }

    private PaymentStatusResult callManualSuccess(RepaymentTransaction tx) {
        Map<String, Object> res = map(api.post("/transactions/" + tx.getGatewayRef() + "/manual-success", null));
        log.warn("CBS manual success ref={} requestId={} -> status={} amount={} (no telecom debit)",
                tx.getReference(), tx.getGatewayRef(), res.get("status"), res.get("amount"));
        return toStatus(tx, res);
    }

    /** Maps a ChannelTransactionResponse. */
    private static PaymentStatusResult toStatus(RepaymentTransaction tx, Map<String, Object> res) {
        Object amount = res.get("amount");
        return new PaymentStatusResult(str(res.get("status")), tx.getGatewayRef(),
                firstNonNull(str(res.get("mpesaRef")), str(res.get("externalId"))),
                amount == null ? null : new BigDecimal(amount.toString()),
                str(res.get("failureReason")));
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
