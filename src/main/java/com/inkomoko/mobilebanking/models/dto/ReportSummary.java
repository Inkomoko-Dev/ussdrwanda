package com.inkomoko.mobilebanking.models.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** Operational dashboard figures for a period. */
public record ReportSummary(
        LocalDateTime from,
        LocalDateTime to,
        long sessions,
        long authenticatedSessions,
        long completedSessions,
        long abandonedSessions,
        long consentDeclined,
        long notRegistered,
        double completionRatePct,
        double abandonmentRatePct,
        long requests,
        double avgResponseMs,
        long loginFailures,
        long pinLocks,
        Map<String, Long> repaymentCountByStatus,
        Map<String, BigDecimal> repaymentAmountByStatus,
        long pendingReconciliationActions
) {
}
