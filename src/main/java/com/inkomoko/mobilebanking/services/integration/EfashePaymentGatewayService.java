package com.inkomoko.mobilebanking.services.integration;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.models.RepaymentTransaction;
import com.inkomoko.mobilebanking.models.dto.PaymentInitResult;
import com.inkomoko.mobilebanking.models.dto.PaymentReversalResult;
import com.inkomoko.mobilebanking.models.dto.PaymentStatusResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Live client for the Efashe Payment Gateway (Inkomoko onboarded as a merchant).
 *
 * NOTE: endpoint paths and field names below are provisional and must be aligned with the
 * Efashe Payment Gateway merchant API specification:
 *   POST {base}/collections              — request a MoMo debit push
 *   GET  {base}/collections/{reference}  — query status
 *   POST {base}/collections/{reference}/reversals — refund a collected payment
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "inkomoko.payment.mode", havingValue = "live")
public class EfashePaymentGatewayService implements PaymentGatewayService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final RestClient client;
    private final InkomokoProperties.Payment cfg;

    public EfashePaymentGatewayService(InkomokoProperties props) {
        this.cfg = props.getPayment();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(cfg.getConnectTimeoutMs())).build();
        JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory(http);
        rf.setReadTimeout(Duration.ofMillis(cfg.getReadTimeoutMs()));
        this.client = RestClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .requestFactory(rf)
                .defaultHeaders(h -> h.setBearerAuth(cfg.getApiKey()))
                .build();
    }

    @Override
    public PaymentInitResult requestCollection(RepaymentTransaction tx) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchantCode", cfg.getMerchantCode());
        body.put("reference", tx.getReference());
        body.put("msisdn", tx.getMsisdn());
        body.put("amount", tx.getAmount());
        body.put("currency", tx.getCurrency());
        body.put("provider", tx.getTelecom() == null ? null : tx.getTelecom().name());
        body.put("description", "Inkomoko loan repayment " + tx.getLoanAccountNo());
        body.put("callbackUrl", cfg.getCallbackUrl());
        try {
            Map<String, Object> res = client.post().uri("/collections")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MAP);
            String status = str(res, "status");
            String gwRef = firstNonNull(str(res, "gatewayRef"), str(res, "transactionId"));
            boolean accepted = status == null
                    || !(status.equalsIgnoreCase("FAILED") || status.equalsIgnoreCase("REJECTED"));
            return new PaymentInitResult(accepted, gwRef, str(res, "message"));
        } catch (RestClientException e) {
            log.error("Payment push failed ref={} error={}", tx.getReference(), e.getMessage());
            return new PaymentInitResult(false, null, e.getMessage());
        }
    }

    @Override
    public PaymentStatusResult checkStatus(RepaymentTransaction tx) {
        try {
            Map<String, Object> res = client.get().uri("/collections/{ref}", tx.getReference())
                    .retrieve().body(MAP);
            Object amt = res == null ? null : res.get("amount");
            return new PaymentStatusResult(str(res, "status"),
                    firstNonNull(str(res, "gatewayRef"), str(res, "transactionId")),
                    str(res, "telecomRef"),
                    amt == null ? null : new BigDecimal(amt.toString()),
                    str(res, "message"));
        } catch (RestClientException e) {
            log.warn("Payment status query failed ref={} error={}", tx.getReference(), e.getMessage());
            return new PaymentStatusResult("UNKNOWN", null, null, null, e.getMessage());
        }
    }

    @Override
    public PaymentReversalResult requestReversal(RepaymentTransaction tx) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("reference", tx.getReference());
            body.put("amount", tx.getAmount());
            body.put("reason", "Repayment reversed");
            Map<String, Object> res = client.post().uri("/collections/{ref}/reversals", tx.getReference())
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(MAP);
            String status = str(res, "status");
            boolean ok = status == null || "SUCCESS".equalsIgnoreCase(status) || "ACCEPTED".equalsIgnoreCase(status);
            return new PaymentReversalResult(ok, str(res, "message"));
        } catch (RestClientException e) {
            log.error("Payment reversal failed ref={} error={}", tx.getReference(), e.getMessage());
            return new PaymentReversalResult(false, e.getMessage());
        }
    }

    private static String str(Map<String, Object> m, String key) {
        if (m == null) return null;
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
