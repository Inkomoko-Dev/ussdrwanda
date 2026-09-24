package com.inkomoko.mobilebanking.controllers.admin;

import com.inkomoko.mobilebanking.models.AuditLog;
import com.inkomoko.mobilebanking.models.api.ApiResponse;
import com.inkomoko.mobilebanking.models.dto.ReportSummary;
import com.inkomoko.mobilebanking.repositories.AuditLogRepository;
import com.inkomoko.mobilebanking.services.ReportService;
import com.inkomoko.mobilebanking.utils.DateUtility;
import com.inkomoko.mobilebanking.utils.PhoneUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Data for the operational reporting dashboard and audit trail. */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin - Reports & Audit")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;
    private final AuditLogRepository auditRepo;

    @GetMapping("/reports/summary")
    @Operation(summary = "Sessions, completion/abandonment, response time, auth failures, repayments by status")
    public ResponseEntity<ApiResponse<ReportSummary>> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : DateUtility.todayKigali();
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        return ResponseEntity.ok(ApiResponse.ok("Summary",
                reportService.summary(start.atStartOfDay(), end.plusDays(1).atStartOfDay().minusNanos(1))));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> audit(
            @RequestParam(required = false) String actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        Page<AuditLog> result = actor == null || actor.isBlank()
                ? auditRepo.findAllByOrderByLoggedAtDesc(pr)
                : auditRepo.findByActorOrderByLoggedAtDesc(actor.matches("[+0-9]+") ? PhoneUtils.purify(actor) : actor, pr);
        return ResponseEntity.ok(ApiResponse.ok("Audit log", result));
    }
}
