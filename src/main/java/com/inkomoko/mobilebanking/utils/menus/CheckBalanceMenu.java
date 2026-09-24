package com.inkomoko.mobilebanking.utils.menus;

import com.inkomoko.mobilebanking.enums.ELanguage;
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

/**
 * 1. Check Balance (ToR 3.1(i)) — always read live from Fineract.
 *
 *   entry        → 1 repayable loan: balance screen | several: loan list | none: message
 *   SELECT_LOAN  → balance screen for the chosen loan
 *   DISPLAY      → 0 = back to main menu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckBalanceMenu {

    public static final String SERVICE = "balance";
    private static final String STEP_SELECT = "SELECT_LOAN";
    private static final String STEP_DISPLAY = "DISPLAY";

    private final CommonMenus common;
    private final FineractService fineract;
    private final AuditService audit;

    public String processMenu(UssdSession s, String input) {
        ELanguage l = s.getLanguage();
        try {
            if (s.getStep() == null) {
                List<LoanSummaryDto> loans = fineract.getClientLoans(s.getClientId()).stream()
                        .filter(x -> x.status().isRepayable()).toList();
                if (loans.isEmpty()) {
                    s.setStep(STEP_DISPLAY);
                    return CommonHeadings.noActiveLoan(l) + CommonHeadings.back(l);
                }
                if (loans.size() == 1) return display(s, loans.get(0).loanId());
                s.setStep(STEP_SELECT);
                return common.loanSelectionScreen(s, loans, common::balanceLabel);
            }
            if ("0".equals(input)) return common.backToMain(s);

            if (STEP_SELECT.equals(s.getStep())) {
                Long loanId = common.resolveLoanChoice(s, input);
                if (loanId == null) {
                    s.setStep(null);
                    return CommonHeadings.invalidChoice(l) + processMenu(s, null);
                }
                return display(s, loanId);
            }
            // DISPLAY: anything other than 0 re-shows main menu options
            return common.invalidThen(s, CommonHeadings.back(l).trim());
        } catch (Exception e) {
            log.error("Balance enquiry failed msisdn={} error={}", s.getMsisdn(), e.getMessage());
            return common.unavailable(s);
        }
    }

    private String display(UssdSession s, Long loanId) {
        ELanguage l = s.getLanguage();
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
        return l.pick(
                "Ideni " + d.accountNo() + "\nAmafaranga y'ideni: " + principal + "\nAyo usigaje: " + total
                        + "\nAyarengeje igihe: " + overdue + "\nItariki ikurikira: " + due + "\nIcyiciro: " + inst,
                "Loan " + d.accountNo() + "\nPrincipal: " + principal + "\nTotal balance: " + total
                        + "\nOverdue: " + overdue + "\nNext due: " + due + "\nInstallment: " + inst,
                "Pret " + d.accountNo() + "\nCapital: " + principal + "\nSolde total: " + total
                        + "\nEn retard: " + overdue + "\nEcheance: " + due + "\nMensualite: " + inst)
                + CommonHeadings.back(l);
    }
}
