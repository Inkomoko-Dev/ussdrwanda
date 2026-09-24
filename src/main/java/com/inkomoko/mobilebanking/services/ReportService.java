package com.inkomoko.mobilebanking.services;

import com.inkomoko.mobilebanking.enums.EApprovalStatus;
import com.inkomoko.mobilebanking.enums.ESessionEndReason;
import com.inkomoko.mobilebanking.models.dto.ReportSummary;
import com.inkomoko.mobilebanking.repositories.AuditLogRepository;
import com.inkomoko.mobilebanking.repositories.ClientPinRepository;
import com.inkomoko.mobilebanking.repositories.ReconciliationActionRepository;
import com.inkomoko.mobilebanking.repositories.RepaymentTransactionRepository;
import com.inkomoko.mobilebanking.repositories.UssdSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Figures for the operational reporting dashboard (proposal §3.7). */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final UssdSessionRepository sessionRepo;
    private final RepaymentTransactionRepository repaymentRepo;
    private final AuditLogRepository auditRepo;
    private final ClientPinRepository pinRepo;
    private final ReconciliationActionRepository reconRepo;

    public ReportSummary summary(LocalDateTime from, LocalDateTime to) {
        long sessions = sessionRepo.countByCreatedAtBetween(from, to);
        long authenticated = sessionRepo.countByCreatedAtBetweenAndAuthenticatedTrue(from, to);
        long completed = sessionRepo.countByCreatedAtBetweenAndEndReason(from, to, ESessionEndReason.COMPLETED)
                + sessionRepo.countByCreatedAtBetweenAndEndReason(from, to, ESessionEndReason.USER_EXIT);
        long abandoned = sessionRepo.countByCreatedAtBetweenAndEndReason(from, to, ESessionEndReason.ABANDONED);
        long consentDeclined = sessionRepo.countByCreatedAtBetweenAndEndReason(from, to, ESessionEndReason.CONSENT_DECLINED);
        long notRegistered = sessionRepo.countByCreatedAtBetweenAndEndReason(from, to, ESessionEndReason.NOT_REGISTERED);
        long requests = sessionRepo.sumRequests(from, to);
        long processingMs = sessionRepo.sumProcessingMs(from, to);

        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        for (Object[] row : repaymentRepo.summarizeByStatus(from, to)) {
            String status = String.valueOf(row[0]);
            counts.put(status, ((Number) row[1]).longValue());
            amounts.put(status, row[2] instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(row[2])));
        }

        return new ReportSummary(from, to, sessions, authenticated, completed, abandoned, consentDeclined,
                notRegistered,
                pct(completed, sessions), pct(abandoned, sessions),
                requests, requests == 0 ? 0 : Math.round(processingMs * 10.0 / requests) / 10.0,
                auditRepo.countByActionAndLoggedAtBetween("LOGIN_FAILED", from, to),
                pinRepo.countByLockedAtBetween(from, to),
                counts, amounts,
                reconRepo.findByStatusOrderByCreatedAtAsc(EApprovalStatus.PENDING_APPROVAL).size());
    }

    private static double pct(long part, long total) {
        return total == 0 ? 0 : Math.round(part * 1000.0 / total) / 10.0;
    }
}
