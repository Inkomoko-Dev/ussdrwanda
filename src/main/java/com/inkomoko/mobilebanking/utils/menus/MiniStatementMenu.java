package com.inkomoko.mobilebanking.utils.menus;

import com.inkomoko.mobilebanking.enums.ELanguage;
import com.inkomoko.mobilebanking.enums.ELoanStatus;
import com.inkomoko.mobilebanking.enums.ESessionEndReason;
import com.inkomoko.mobilebanking.models.UssdSession;
import com.inkomoko.mobilebanking.models.dto.LoanSummaryDto;
import com.inkomoko.mobilebanking.models.dto.RepaymentDto;
import com.inkomoko.mobilebanking.services.AuditService;
import com.inkomoko.mobilebanking.services.integration.FineractService;
import com.inkomoko.mobilebanking.services.integration.SmsService;
import com.inkomoko.mobilebanking.utils.DateUtility;
import com.inkomoko.mobilebanking.utils.GeneralMethods;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 3. Mini Statement (ToR 3.1(iii)) — last 5 repayments (date, amount, reference) with an
 * option to receive the full repayment history by SMS.
 *
 *   entry        → 1 disbursed loan: statement | several: loan list
 *   SELECT_LOAN  → statement for chosen loan
 *   DISPLAY      → 1 = send full statement by SMS (ends session), 0 = back
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MiniStatementMenu {

    public static final String SERVICE = "statement";
    private static final String STEP_SELECT = "SELECT_LOAN";
    private static final String STEP_DISPLAY = "DISPLAY";
    private static final int ON_SCREEN = 5;
    private static final int SMS_MAX = 30;
    private static final Set<ELoanStatus> DISBURSED = EnumSet.of(ELoanStatus.ACTIVE, ELoanStatus.IN_ARREARS,
            ELoanStatus.SETTLED, ELoanStatus.CLOSED);

    private final CommonMenus common;
    private final FineractService fineract;
    private final SmsService sms;
    private final AuditService audit;

    public String processMenu(UssdSession s, String input) {
        ELanguage l = s.getLanguage();
        try {
            if (s.getStep() == null) {
                List<LoanSummaryDto> loans = fineract.getClientLoans(s.getClientId()).stream()
                        .filter(x -> DISBURSED.contains(x.status()))
                        .sorted((a, b) -> Boolean.compare(b.status().isRepayable(), a.status().isRepayable()))
                        .toList();
                if (loans.isEmpty()) {
                    s.setStep(STEP_DISPLAY);
                    return CommonHeadings.noLoans(l) + CommonHeadings.back(l);
                }
                if (loans.size() == 1) return statement(s, loans.get(0));
                s.setStep(STEP_SELECT);
                return common.loanSelectionScreen(s, loans, x -> x.accountNo() + " " + x.status().label(l));
            }
            if ("0".equals(input)) return common.backToMain(s);

            if (STEP_SELECT.equals(s.getStep())) {
                Long loanId = common.resolveLoanChoice(s, input);
                if (loanId == null) {
                    s.setStep(null);
                    return CommonHeadings.invalidChoice(l) + processMenu(s, null);
                }
                return statement(s, fineract.getLoanDetails(loanId));
            }

            if ("1".equals(input) && s.getLoanId() != null) return sendFullStatement(s);
            return common.invalidThen(s, smsOption(l) + CommonHeadings.back(l));
        } catch (Exception e) {
            log.error("Mini statement failed msisdn={} error={}", s.getMsisdn(), e.getMessage());
            return common.unavailable(s);
        }
    }

    private String statement(UssdSession s, LoanSummaryDto loan) {
        ELanguage l = s.getLanguage();
        if (loan.clientId() != null && !loan.clientId().equals(s.getClientId())) return common.unavailable(s);
        s.setLoanId(loan.loanId());
        s.setLoanAccountNo(loan.accountNo());
        s.setStep(STEP_DISPLAY);
        s.setEndReason(ESessionEndReason.COMPLETED);
        audit.ussd(s.getMsisdn(), s.getSessionId(), "MINI_STATEMENT", String.valueOf(loan.loanId()), true, null);

        List<RepaymentDto> rows = fineract.getRepayments(loan.loanId(), ON_SCREEN);
        if (rows.isEmpty()) {
            return l.pick("Nta bwishyu buraboneka kuri " + loan.accountNo() + ".",
                    "No repayments yet on " + loan.accountNo() + ".",
                    "Aucun remboursement sur " + loan.accountNo() + ".") + CommonHeadings.back(l);
        }
        StringBuilder sb = new StringBuilder(l.pick("Ubwishyu ", "Repayments ", "Paiements "))
                .append(loan.accountNo()).append(':');
        for (RepaymentDto r : rows) {
            sb.append('\n').append(DateUtility.shortDate(r.date())).append(' ')
                    .append(GeneralMethods.formatAmount(r.amount())).append(' ')
                    .append(shortRef(r.reference()));
        }
        return sb.append('\n').append(smsOption(l)).append(CommonHeadings.back(l)).toString();
    }

    private String sendFullStatement(UssdSession s) {
        ELanguage l = s.getLanguage();
        List<RepaymentDto> rows = fineract.getRepayments(s.getLoanId(), SMS_MAX);
        LoanSummaryDto loan = fineract.getLoanDetails(s.getLoanId());
        StringBuilder sb = new StringBuilder("Inkomoko ")
                .append(l.pick("raporo y'ideni ", "statement loan ", "releve pret "))
                .append(s.getLoanAccountNo()).append(":\n");
        for (RepaymentDto r : rows) {
            sb.append(DateUtility.shortDate(r.date())).append(' ').append(GeneralMethods.rwf(r.amount()))
                    .append(" Ref ").append(r.reference()).append('\n');
        }
        sb.append(l.pick("Asigaye: ", "Balance: ", "Solde: ")).append(GeneralMethods.rwf(loan.totalOutstanding()));
        sms.send(s.getMsisdn(), sb.toString());
        audit.ussd(s.getMsisdn(), s.getSessionId(), "STATEMENT_SMS", String.valueOf(s.getLoanId()), true,
                rows.size() + " rows");
        s.end(ESessionEndReason.COMPLETED);
        return l.pick("Raporo yuzuye yoherejwe kuri SMS. ", "Full statement sent by SMS. ",
                "Releve complet envoye par SMS. ") + CommonHeadings.thankYou(l);
    }

    private static String smsOption(ELanguage l) {
        return l.pick("1. SMS raporo yose", "1. Full stmt by SMS", "1. Releve par SMS");
    }

    private static String shortRef(String ref) {
        if (ref == null) return "";
        return ref.length() > 6 ? ref.substring(ref.length() - 6) : ref;
    }
}
