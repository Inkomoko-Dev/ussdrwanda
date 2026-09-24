package com.inkomoko.mobilebanking.services.integration;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.models.RepaymentTransaction;
import com.inkomoko.mobilebanking.models.dto.PaymentInitResult;
import com.inkomoko.mobilebanking.models.dto.PaymentStatusResult;
import com.inkomoko.mobilebanking.services.RepaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.inkomoko.mobilebanking.models.dto.PaymentReversalResult;
import com.inkomoko.mobilebanking.utils.DateUtility;

import java.math.BigDecimal;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simulated MoMo gateway (inkomoko.payment.mode=mock). Accepts every push and calls back
 * after {@code mock-delay-seconds}: amounts ending in 13 (e.g. 1013) fail, all others succeed.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "inkomoko.payment.mode", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGatewayService implements PaymentGatewayService {

    private final ObjectProvider<RepaymentService> repaymentService;
    private final int delaySeconds;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public MockPaymentGatewayService(ObjectProvider<RepaymentService> repaymentService, InkomokoProperties props) {
        this.repaymentService = repaymentService;
        this.delaySeconds = props.getPayment().getMockDelaySeconds();
        log.warn("Payment gateway is in MOCK mode — payments auto-confirm after {}s", delaySeconds);
    }

    @Override
    public PaymentInitResult requestCollection(RepaymentTransaction tx) {
        String gwRef = "MOCKGW-" + tx.getReference();
        String reference = tx.getReference();
        BigDecimal amount = tx.getAmount();
        boolean fail = amount.toBigInteger().mod(java.math.BigInteger.valueOf(100)).intValue() == 13;
        scheduler.schedule(() -> {
            try {
                repaymentService.getObject().handleGatewayResult(reference, new PaymentStatusResult(
                        fail ? "FAILED" : "SUCCESS", gwRef, "MOCKMNO" + System.currentTimeMillis(), amount,
                        fail ? "Insufficient funds (mock)" : "Approved (mock)"));
            } catch (Exception e) {
                log.error("[MOCK GW] callback failed ref={} error={}", reference, e.getMessage(), e);
            }
        }, delaySeconds, TimeUnit.SECONDS);
        log.info("[MOCK GW] push accepted ref={} amount={} msisdn={}", reference, amount, tx.getMsisdn());
        return new PaymentInitResult(true, gwRef, "Accepted (mock)");
    }

    @Override
    public PaymentStatusResult checkStatus(RepaymentTransaction tx) {
        String gwRef = tx.getGatewayRef() != null ? tx.getGatewayRef() : "MOCKGW-" + tx.getReference();
        boolean fail = fails(tx.getAmount());
        boolean ready = tx.getCreatedAt() == null
                || !tx.getCreatedAt().plusSeconds(delaySeconds).isAfter(DateUtility.nowKigali());
        if (!ready) {
            return new PaymentStatusResult("PENDING", gwRef, null, null, "Awaiting client PIN (mock)");
        }
        if (fail) {
            return new PaymentStatusResult("FAILED", gwRef, null, null, "Insufficient funds (mock)");
        }
        return new PaymentStatusResult("SUCCESS", gwRef, "MOCKMNO" + tx.getReference(), tx.getAmount(), "Approved (mock)");
    }

    @Override
    public PaymentReversalResult requestReversal(RepaymentTransaction tx) {
        log.info("[MOCK GW] refund accepted ref={} amount={}", tx.getReference(), tx.getAmount());
        return new PaymentReversalResult(true, "Refund accepted (mock)");
    }

    private static boolean fails(BigDecimal amount) {
        return amount != null && amount.toBigInteger().mod(java.math.BigInteger.valueOf(100)).intValue() == 13;
    }
}
