package com.inkomoko.mobilebanking.utils.menus;

import com.inkomoko.mobilebanking.enums.ESessionEndReason;
import com.inkomoko.mobilebanking.models.UssdSession;
import com.inkomoko.mobilebanking.models.dto.LoanSummaryDto;
import com.inkomoko.mobilebanking.services.AuditService;
import com.inkomoko.mobilebanking.services.integration.FineractService;
import com.inkomoko.mobilebanking.utils.DateUtility;
import com.inkomoko.mobilebanking.utils.GeneralMethods;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 1. Check Balance (ToR 3.1(i)) — always read live from Fineract.
 *
 *   entry        → 1 repayable loan: balance screen | several: loan list | none: message
 *   SELECT_LOAN  → balance screen for the chosen loan (0 = Home)
 *   DISPLAY      → 0 = Back to loan list (or Home if only one loan)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckBalanceMenu {

    public static final String SERVICE = "balance";
    private static final String STEP_SELECT = "SELECT_LOAN";
    private static final String STEP_DISPLAY = "DISPLAY";

    private final CommonMenus common;
    private final CommonHeadings headings;
    private final FineractService fineract;
    private final AuditService audit;

    public String processMenu(UssdSession s, String input) {
        String l = s.getLanguage();
        try {
            if (s.getStep() == null) {
                List<LoanSummaryDto> loans = fineract.getClientLoans(s.getClientId()).stream()
                        .filter(x -> x.status().isRepayable()).toList();
                if (loans.isEmpty()) {
                    s.setStep(STEP_DISPLAY);
                    return headings.noActiveLoan(l) + headings.back(l);
                }
                if (loans.size() == 1) return display(s, loans.get(0).loanId());
                s.setStep(STEP_SELECT);
                return common.loanSelectionScreen(s, loans, common::loanListLabel);
            }
            if ("0".equals(input)) return back(s);

            if (STEP_SELECT.equals(s.getStep())) {
                Long loanId = common.resolveLoanChoice(s, input);
                if (loanId == null) {
                    s.setStep(null);
                    return headings.invalidChoice(l) + processMenu(s, null);
                }
                return display(s, loanId);
            }
            // DISPLAY: anything other than 0 re-shows the nav footer
            return common.invalidThen(s, headings.back(l).trim());
        } catch (Exception e) {
            log.error("Balance enquiry failed msisdn={} error={}", s.getMsisdn(), e.getMessage());
            return common.unavailable(s);
        }
    }

    /** 0. Back — one step: balance → loan list → main menu. */
    private String back(UssdSession s) {
        if (STEP_DISPLAY.equals(s.getStep())) {
            List<LoanSummaryDto> loans = fineract.getClientLoans(s.getClientId()).stream()
                    .filter(x -> x.status().isRepayable()).toList();
            if (loans.size() > 1) {
                s.setStep(STEP_SELECT);
                return common.loanSelectionScreen(s, loans, common::loanListLabel);
            }
        }
        return common.home(s);
    }

    private String display(UssdSession s, Long loanId) {
        String l = s.getLanguage();
        LoanSummaryDto d = fineract.getLoanDetails(loanId);
        if (d.clientId() != null && !d.clientId().equals(s.getClientId())) {
            log.warn("Loan {} does not belong to client {}", loanId, s.getClientId());
            return common.unavailable(s);
        }
        s.setStep(STEP_DISPLAY);
        s.setLoanId(loanId);
        audit.ussd(s.getMsisdn(), s.getSessionId(), "BALANCE_ENQUIRY", String.valueOf(loanId), true, null);
        s.setEndReason(ESessionEndReason.COMPLETED); // reached the goal screen (session stays open for Back)
        String principal = GeneralMethods.rwf(d.principalOutstanding());
        String total = GeneralMethods.rwf(d.totalOutstanding());
        String overdue = GeneralMethods.rwf(d.totalOverdue());
        String due = DateUtility.screenDate(d.nextDueDate());
        String inst = d.nextInstallment() == null ? "-" : GeneralMethods.rwf(d.nextInstallment());
        return headings.text(l, "balance.screen", Map.of("account", d.accountNo(), "principal", principal,
                "total", total, "overdue", overdue, "due", due, "installment", inst))
                + headings.back(l);
    }
}
