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
import com.inkomoko.mobilebanking.security.AdminActor;
import com.inkomoko.mobilebanking.services.ReconciliationService;
import com.inkomoko.mobilebanking.services.RepaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
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
    private final RepaymentService repayments;

    private static byte[] workbook(List<RepaymentTransaction> rows) throws java.io.IOException {
        String[] headers = { "Reference", "Phone", "Loan", "Amount", "Currency", "Status", "Network",
                "Gateway ref", "Telecom ref", "Core-banking txn", "Created", "Paid", "Posted", "Note" };
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Repayments");
            Row head = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) head.createCell(i).setCellValue(headers[i]);
            int r = 1;
            for (RepaymentTransaction t : rows) {
                Row row = sheet.createRow(r++);
                int c = 0;
                row.createCell(c++).setCellValue(text(t.getReference()));
                row.createCell(c++).setCellValue(text(t.getMsisdn()));
                row.createCell(c++).setCellValue(text(t.getLoanAccountNo()));
                if (t.getAmount() != null) row.createCell(c++).setCellValue(t.getAmount().doubleValue());
                else row.createCell(c++).setCellValue("");
                row.createCell(c++).setCellValue(text(t.getCurrency()));
                row.createCell(c++).setCellValue(t.getStatus() == null ? "" : t.getStatus().name());
                row.createCell(c++).setCellValue(t.getTelecom() == null ? "" : t.getTelecom().name());
                row.createCell(c++).setCellValue(text(t.getGatewayRef()));
                row.createCell(c++).setCellValue(text(t.getTelecomRef()));
                row.createCell(c++).setCellValue(text(t.getFineractTxnId()));
                row.createCell(c++).setCellValue(text(t.getCreatedAt()));
                row.createCell(c++).setCellValue(text(t.getPaidAt()));
                row.createCell(c++).setCellValue(text(t.getPostedAt()));
                row.createCell(c).setCellValue(text(t.getFailureReason()));
            }
            book.write(out);
            return out.toByteArray();
        }
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    @GetMapping("/repayments")
    @PreAuthorize("hasAuthority('REPAYMENT_VIEW')")
    public ResponseEntity<ApiResponse<Page<RepaymentTransaction>>> list(
            @RequestParam(required = false) ETransactionStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        String query = q == null ? "" : q.trim();
        Page<RepaymentTransaction> result = query.isEmpty() && status == null
                ? txRepo.findAllByOrderByCreatedAtDesc(pr)
                : txRepo.search(query, status, pr);
        return ResponseEntity.ok(ApiResponse.ok("Repayments", result));
    }

    @GetMapping("/repayments/export")
    @PreAuthorize("hasAuthority('REPAYMENT_VIEW')")
    @Operation(summary = "Excel of repayments matching the same search and status as the list")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) ETransactionStatus status,
                                         @RequestParam(required = false) String q) throws java.io.IOException {
        String query = q == null ? "" : q.trim();
        List<RepaymentTransaction> rows = txRepo.search(query, status, PageRequest.of(0, 20_000)).getContent();
        byte[] body = workbook(rows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=repayments.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @GetMapping("/repayments/{reference}")
    @PreAuthorize("hasAuthority('REPAYMENT_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable String reference) {
        return txRepo.findByReference(reference).map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("transaction", t);
            m.put("reconciliationActions", actionRepo.findByTransactionReferenceOrderByCreatedAtDesc(reference));
            return ResponseEntity.ok(ApiResponse.ok("Repayment", m));
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("Not found")));
    }

    @PostMapping("/repayments/{reference}/telecom-status")
    @PreAuthorize("hasAuthority('RECON_PROPOSE')")
    @Operation(summary = "Ask the telecom for the latest result; post to the loan if it is now paid")
    public ResponseEntity<ApiResponse<RepaymentService.TelecomCheck>> telecomStatus(@PathVariable String reference) {
        return ResponseEntity.ok(ApiResponse.ok("Telecom status", repayments.checkTelecom(reference, AdminActor.current())));
    }

    @PostMapping("/repayments/{reference}/post")
    @PreAuthorize("hasAuthority('RECON_PROPOSE')")
    @Operation(summary = "Post a telecom-confirmed collection to core banking")
    public ResponseEntity<ApiResponse<RepaymentTransaction>> post(@PathVariable String reference) {
        return ResponseEntity.ok(ApiResponse.ok("Posted to core banking", repayments.postCollected(reference, AdminActor.current())));
    }

    @PostMapping("/repayments/{reference}/reconcile")
    @PreAuthorize("hasAuthority('RECON_PROPOSE')")
    @Operation(summary = "Maker: propose a reconciliation action")
    public ResponseEntity<ApiResponse<ReconciliationAction>> propose(
            @PathVariable String reference,
            @Valid @RequestBody ReconciliationProposalRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Awaiting checker approval",
                reconciliationService.propose(reference, req, AdminActor.current())));
    }

    @GetMapping("/reconciliation-actions")
    @PreAuthorize("hasAnyAuthority('RECON_APPROVE','RECON_PROPOSE')")
    public ResponseEntity<ApiResponse<List<ReconciliationAction>>> pending() {
        return ResponseEntity.ok(ApiResponse.ok("Pending approval",
                actionRepo.findByStatusOrderByCreatedAtAsc(EApprovalStatus.PENDING_APPROVAL)));
    }

    @PostMapping("/reconciliation-actions/{id}/approve")
    @PreAuthorize("hasAuthority('RECON_APPROVE')")
    @Operation(summary = "Checker: approve and execute")
    public ResponseEntity<ApiResponse<ReconciliationAction>> approve(
            @PathVariable Long id,
            @RequestBody(required = false) ReconciliationDecisionRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Approved",
                reconciliationService.approve(id, AdminActor.current(),
                        req == null ? null : req.getComment())));
    }

    @PostMapping("/reconciliation-actions/{id}/reject")
    @PreAuthorize("hasAuthority('RECON_APPROVE')")
    @Operation(summary = "Checker: reject")
    public ResponseEntity<ApiResponse<ReconciliationAction>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) ReconciliationDecisionRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Rejected",
                reconciliationService.reject(id, AdminActor.current(),
                        req == null ? null : req.getComment())));
    }
}
