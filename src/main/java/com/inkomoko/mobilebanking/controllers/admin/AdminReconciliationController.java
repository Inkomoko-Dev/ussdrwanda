package com.inkomoko.mobilebanking.controllers.admin;

import com.inkomoko.mobilebanking.enums.EApprovalStatus;
import com.inkomoko.mobilebanking.enums.ETransactionStatus;
import com.inkomoko.mobilebanking.models.ReconciliationAction;
import com.inkomoko.mobilebanking.models.RepaymentTransaction;
import com.inkomoko.mobilebanking.models.api.ApiResponse;
import com.inkomoko.mobilebanking.models.api.ReconciliationDecisionRequest;
import com.inkomoko.mobilebanking.models.api.ReconciliationProposalRequest;
import com.inkomoko.mobilebanking.repositories.ReconciliationActionRepository;
import com.inkomoko.mobilebanking.repositories.RepaymentTransactionRepository;
import com.inkomoko.mobilebanking.security.AdminApiKeyFilter;
import com.inkomoko.mobilebanking.services.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Repayment monitoring and maker/checker reconciliation.
 *
 *   GET  /api/admin/repayments?status=NEEDS_REVIEW       list transactions
 *   GET  /api/admin/repayments/{reference}               one transaction + its reconciliation history
 *   POST /api/admin/repayments/{reference}/reconcile     maker proposes REPOST / MARK_POSTED / MARK_FAILED / MARK_REVERSED
 *   GET  /api/admin/reconciliation-actions               actions awaiting approval
 *   POST /api/admin/reconciliation-actions/{id}/approve  checker (different user) approves → executed
 *   POST /api/admin/reconciliation-actions/{id}/reject
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin - Repayments & Reconciliation")
@RequiredArgsConstructor
public class AdminReconciliationController {

    private final RepaymentTransactionRepository txRepo;
    private final ReconciliationActionRepository actionRepo;
    private final ReconciliationService reconciliationService;

    @GetMapping("/repayments")
    public ResponseEntity<ApiResponse<Page<RepaymentTransaction>>> list(
            @RequestParam(required = false) ETransactionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        Page<RepaymentTransaction> result = status == null
                ? txRepo.findAllByOrderByCreatedAtDesc(pr)
                : txRepo.findByStatusOrderByCreatedAtDesc(status, pr);
        return ResponseEntity.ok(ApiResponse.ok("Repayments", result));
    }

    @GetMapping("/repayments/{reference}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable String reference) {
        return txRepo.findByReference(reference).map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("transaction", t);
            m.put("reconciliationActions", actionRepo.findByTransactionReferenceOrderByCreatedAtDesc(reference));
            return ResponseEntity.ok(ApiResponse.ok("Repayment", m));
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("Not found")));
    }

    @PostMapping("/repayments/{reference}/reconcile")
    @Operation(summary = "Maker: propose a reconciliation action")
    public ResponseEntity<ApiResponse<ReconciliationAction>> propose(
            @PathVariable String reference,
            @Valid @RequestBody ReconciliationProposalRequest req,
            @RequestHeader(AdminApiKeyFilter.ADMIN_USER_HEADER) String maker) {
        return ResponseEntity.ok(ApiResponse.ok("Awaiting checker approval",
                reconciliationService.propose(reference, req, maker)));
    }

    @GetMapping("/reconciliation-actions")
    public ResponseEntity<ApiResponse<List<ReconciliationAction>>> pending() {
        return ResponseEntity.ok(ApiResponse.ok("Pending approval",
                actionRepo.findByStatusOrderByCreatedAtAsc(EApprovalStatus.PENDING_APPROVAL)));
    }

    @PostMapping("/reconciliation-actions/{id}/approve")
    @Operation(summary = "Checker: approve and execute")
    public ResponseEntity<ApiResponse<ReconciliationAction>> approve(
            @PathVariable Long id,
            @RequestBody(required = false) ReconciliationDecisionRequest req,
            @RequestHeader(AdminApiKeyFilter.ADMIN_USER_HEADER) String checker) {
        return ResponseEntity.ok(ApiResponse.ok("Approved",
                reconciliationService.approve(id, checker, req == null ? null : req.getComment())));
    }

    @PostMapping("/reconciliation-actions/{id}/reject")
    @Operation(summary = "Checker: reject")
    public ResponseEntity<ApiResponse<ReconciliationAction>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) ReconciliationDecisionRequest req,
            @RequestHeader(AdminApiKeyFilter.ADMIN_USER_HEADER) String checker) {
        return ResponseEntity.ok(ApiResponse.ok("Rejected",
                reconciliationService.reject(id, checker, req == null ? null : req.getComment())));
    }
}
