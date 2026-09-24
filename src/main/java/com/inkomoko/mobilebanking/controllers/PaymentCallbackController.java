package com.inkomoko.mobilebanking.controllers;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.models.api.ApiResponse;
import com.inkomoko.mobilebanking.models.api.PaymentCallbackRequest;
import com.inkomoko.mobilebanking.models.dto.PaymentStatusResult;
import com.inkomoko.mobilebanking.services.AuditService;
import com.inkomoko.mobilebanking.services.RepaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Called by the Efashe Payment Gateway when the client approves or declines the MoMo prompt.
 * Authenticated with a shared secret (X-Callback-Token). Idempotent: repeated callbacks for the
 * same reference are acknowledged without re-posting.
 */
@Slf4j
@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Mobile Money payment callbacks")
@RequiredArgsConstructor
public class PaymentCallbackController {

    private final RepaymentService repaymentService;
    private final InkomokoProperties props;
    private final AuditService audit;

    @PostMapping("/callback")
    @Operation(summary = "Payment gateway callback for loan repayments")
    public ResponseEntity<ApiResponse<String>> callback(
            @RequestHeader(name = "X-Callback-Token", required = false) String token,
            @RequestBody PaymentCallbackRequest req) {

        if (!validToken(token)) {
            audit.system("PAYMENT_CALLBACK_REJECTED", req.getReference(), false, "invalid callback token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("Invalid callback token"));
        }
        if (req.getReference() == null || req.getReference().isBlank() || req.getStatus() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("reference and status are required"));
        }
        log.info("Payment callback ref={} status={} gatewayRef={}", req.getReference(), req.getStatus(), req.getGatewayRef());
        String outcome = repaymentService.handleGatewayResult(req.getReference(), new PaymentStatusResult(
                req.getStatus(), req.getGatewayRef(), req.getTelecomRef(), req.getAmount(), req.getMessage()));
        if ("NOT_FOUND".equals(outcome)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("Unknown reference"));
        }
        return ResponseEntity.ok(ApiResponse.ok("Callback processed", outcome));
    }

    private boolean validToken(String token) {
        String expected = props.getPayment().getCallbackToken();
        if (expected == null || expected.isBlank() || token == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }
}
