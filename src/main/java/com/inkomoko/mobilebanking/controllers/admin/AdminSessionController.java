package com.inkomoko.mobilebanking.controllers.admin;

import com.inkomoko.mobilebanking.models.api.ApiResponse;
import com.inkomoko.mobilebanking.services.SessionAnalyticsService;
import com.inkomoko.mobilebanking.services.SessionAnalyticsService.MsisdnVisits;
import com.inkomoko.mobilebanking.services.SessionAnalyticsService.SessionSummary;
import com.inkomoko.mobilebanking.services.SessionAnalyticsService.SessionView;
import com.inkomoko.mobilebanking.utils.DateUtility;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** USSD session log and visits per phone number (the basis for checking the monthly USSD bill). */
@RestController
@RequestMapping("/api/admin/ussd-sessions")
@Tag(name = "Admin - USSD sessions")
@PreAuthorize("hasAuthority('SESSION_VIEW')")
@RequiredArgsConstructor
public class AdminSessionController {

    private static final int MAX_EXPORT_ROWS = 100_000;

    private final SessionAnalyticsService service;

    @GetMapping("/summary")
    @Operation(summary = "Sessions, unique phone numbers, outcomes, networks and daily totals for a date range")
    public ResponseEntity<ApiResponse<SessionSummary>> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Range r = Range.of(from, to);
        return ResponseEntity.ok(ApiResponse.ok("USSD session summary", service.summary(r.start, r.end)));
    }

    @GetMapping
    @Operation(summary = "Session log, newest first; status is IN_PROGRESS, ENDED or an end reason (COMPLETED, ABANDONED, …)")
    public ResponseEntity<ApiResponse<Page<SessionView>>> sessions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String msisdn,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Range r = Range.of(from, to);
        return ResponseEntity.ok(ApiResponse.ok("USSD sessions",
                service.sessions(r.start, r.end, msisdn, status, pageRequest(page, size))));
    }

    @GetMapping("/by-msisdn")
    @Operation(summary = "Visits per phone number, most frequent first")
    public ResponseEntity<ApiResponse<Page<MsisdnVisits>>> byMsisdn(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String msisdn,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Range r = Range.of(from, to);
        return ResponseEntity.ok(ApiResponse.ok("Visits per phone number",
                service.visitsByMsisdn(r.start, r.end, msisdn, pageRequest(page, size))));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Session log as CSV")
    public ResponseEntity<byte[]> exportSessions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String msisdn,
            @RequestParam(required = false) String status) {
        Range r = Range.of(from, to);
        return csv("ussd-sessions_" + r.label() + ".csv",
                service.sessionsCsv(r.start, r.end, msisdn, status, MAX_EXPORT_ROWS));
    }

    @GetMapping(value = "/by-msisdn/export", produces = "text/csv")
    @Operation(summary = "Visits per phone number as CSV")
    public ResponseEntity<byte[]> exportByMsisdn(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String msisdn) {
        Range r = Range.of(from, to);
        return csv("ussd-visits-by-phone_" + r.label() + ".csv",
                service.visitsCsv(r.start, r.end, msisdn, MAX_EXPORT_ROWS));
    }

    private static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
    }

    private static ResponseEntity<byte[]> csv(String filename, String body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body.getBytes(StandardCharsets.UTF_8));
    }

    /** Inclusive calendar-day range; defaults to the current month to date (Kigali time). */
    private record Range(LocalDate from, LocalDate to, LocalDateTime start, LocalDateTime end) {
        static Range of(LocalDate from, LocalDate to) {
            LocalDate end = to != null ? to : DateUtility.todayKigali();
            LocalDate start = from != null ? from : end.withDayOfMonth(1);
            if (start.isAfter(end)) throw new IllegalArgumentException("'from' must not be after 'to'");
            return new Range(start, end, start.atStartOfDay(), end.plusDays(1).atStartOfDay().minusNanos(1));
        }

        String label() {
            return from + "_to_" + to;
        }
    }
}
