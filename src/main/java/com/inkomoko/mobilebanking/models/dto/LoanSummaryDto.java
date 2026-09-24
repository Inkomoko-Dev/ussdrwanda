package com.inkomoko.mobilebanking.models.dto;

import com.inkomoko.mobilebanking.enums.ELoanStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Loan view used by the menus. The list call fills the basic fields; the details call
 * (real-time, per request) adds the schedule-derived fields.
 */
public record LoanSummaryDto(
        Long loanId,
        Long clientId,
        String accountNo,
        String productName,
        ELoanStatus status,
        BigDecimal principalOutstanding,
        BigDecimal totalOutstanding,
        BigDecimal totalOverdue,
        LocalDate nextDueDate,
        BigDecimal nextInstallment
) {
}
