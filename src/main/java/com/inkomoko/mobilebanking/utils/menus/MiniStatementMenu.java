package com.inkomoko.mobilebanking.utils.menus;

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
import java.util.Map;
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
    private static final String STEP_NONE = "NONE";
    private static final int PAGE_SIZE = 3;
    private static final int SMS_MAX = 30;
    private static final Set<ELoanStatus> DISBURSED = EnumSet.of(ELoanStatus.ACTIVE, ELoanStatus.IN_ARREARS,
            ELoanStatus.SETTLED, ELoanStatus.CLOSED);

    private final CommonMenus common;
    private final CommonHeadings headings;
    private final FineractService fineract;
    private final SmsService sms;
    private final AuditService audit;

    public String processMenu(UssdSession s, String input) {
        try {
            if (s.getStep() == null) return start(s);
            if ("0".equals(input)) return back(s);
            String step = s.getStep();
            if (STEP_SELECT.equals(step)) return chooseLoan(s, input);
            if (step.startsWith("PAGE:")) return pageInput(s, input, pageOf(step));
            if (step.startsWith("SMS:")) return smsInput(s, input, pageOf(step));
            return start(s);
        } catch (Exception e) {
            log.error("Mini statement failed msisdn={} error={}", s.getMsisdn(), e.getMessage());
            return common.unavailable(s);
        }
    }

    private String start(UssdSession s) {
        String l = s.getLanguage();
        List<LoanSummaryDto> loans = fineract.getClientLoans(s.getClientId()).stream()
                .filter(x -> DISBURSED.contains(x.status()))
                .sorted((a, b) -> Boolean.compare(b.status().isRepayable(), a.status().isRepayable()))
                .toList();
        if (loans.isEmpty()) {
            s.setStep(STEP_NONE);
            return headings.noLoans(l) + headings.back(l);
        }
        if (loans.size() == 1) return statement(s, fineract.getLoanDetails(loans.get(0).loanId()), 0);
        s.setStep(STEP_SELECT);
        return common.loanSelectionScreen(s, loans, common::loanListLabel);
    }

    /** 0. Back — one step: SMS confirm → page → prior page / loan list → main menu. */
    private String back(UssdSession s) {
        String step = s.getStep();
        if (step != null && step.startsWith("SMS:")) {
            return statement(s, fineract.getLoanDetails(s.getLoanId()), pageOf(step));
        }
        if (step != null && step.startsWith("PAGE:")) {
            int page = pageOf(step);
            if (page > 0) return statement(s, fineract.getLoanDetails(s.getLoanId()), page - 1);
            if (s.getLoanOptions() != null) {
                s.setStep(STEP_SELECT);
                List<LoanSummaryDto> loans = fineract.getClientLoans(s.getClientId()).stream()
                        .filter(x -> DISBURSED.contains(x.status()))
                        .sorted((a, b) -> Boolean.compare(b.status().isRepayable(), a.status().isRepayable()))
                        .toList();
                return common.loanSelectionScreen(s, loans, common::loanListLabel);
            }
        }
        return common.home(s);
    }

    private String chooseLoan(UssdSession s, String input) {
        Long loanId = common.resolveLoanChoice(s, input);
        if (loanId == null) {
            s.setStep(null);
            return headings.invalidChoice(s.getLanguage()) + start(s);
        }
        return statement(s, fineract.getLoanDetails(loanId), 0);
    }

    private String pageInput(UssdSession s, String input, int page) {
        if ("1".equals(input)) return confirmSms(s, page);
        if ("9".equals(input)) return statement(s, fineract.getLoanDetails(s.getLoanId()), page + 1);
        return headings.invalidChoice(s.getLanguage()) + statement(s, fineract.getLoanDetails(s.getLoanId()), page);
    }

    private String confirmSms(UssdSession s, int page) {
        s.setStep("SMS:" + page);
        return headings.text(s.getLanguage(), "statement.sms_confirm") + headings.back(s.getLanguage());
    }

    private String smsInput(UssdSession s, String input, int page) {
        if ("1".equals(input)) return sendFullStatement(s);
        return headings.invalidChoice(s.getLanguage()) + confirmSms(s, page);
    }

    private String statement(UssdSession s, LoanSummaryDto loan, int page) {
        String l = s.getLanguage();
        if (loan.clientId() != null && !loan.clientId().equals(s.getClientId())) return common.unavailable(s);
        s.setLoanId(loan.loanId());
        s.setLoanAccountNo(loan.accountNo());
        List<RepaymentDto> rows = fineract.getRepayments(loan.loanId(), SMS_MAX);
        if (rows.isEmpty()) {
            s.setStep(STEP_NONE);
            return headings.text(l, "statement.none", Map.of("account", loan.accountNo())) + headings.back(l);
        }
        int pages = (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int shown = Math.min(Math.max(page, 0), pages - 1);
        if (shown == 0) {
            audit.ussd(s.getMsisdn(), s.getSessionId(), "MINI_STATEMENT", String.valueOf(loan.loanId()), true, null);
        }
        s.setStep("PAGE:" + shown);
        StringBuilder sb = new StringBuilder(headings.text(l, "statement.header", Map.of("account", loan.accountNo())));
        rows.subList(shown * PAGE_SIZE, Math.min(rows.size(), (shown + 1) * PAGE_SIZE)).forEach(r ->
                sb.append('\n').append(DateUtility.shortDate(r.date())).append(' ')
                        .append(GeneralMethods.formatAmount(r.amount())).append(' ')
                        .append(shortRef(r.reference())));
        if (shown + 1 < pages) sb.append('\n').append(headings.text(l, "statement.next"));
        return sb.append('\n').append(headings.text(l, "statement.sms_option")).append(headings.back(l)).toString();
    }

    private static int pageOf(String step) {
        int i = step.indexOf(':');
        if (i < 0) return 0;
        try {
            return Integer.parseInt(step.substring(i + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String sendFullStatement(UssdSession s) {
        String l = s.getLanguage();
        List<RepaymentDto> rows = fineract.getRepayments(s.getLoanId(), SMS_MAX);
        LoanSummaryDto loan = fineract.getLoanDetails(s.getLoanId());
        StringBuilder sb = new StringBuilder(headings.text(l, "sms.statement.header",
                Map.of("account", s.getLoanAccountNo()))).append('\n');
        for (RepaymentDto r : rows) {
            sb.append(DateUtility.shortDate(r.date())).append(' ').append(GeneralMethods.rwf(r.amount()))
                    .append(" Ref ").append(r.reference()).append('\n');
        }
        sb.append(headings.text(l, "sms.statement.balance",
                Map.of("balance", GeneralMethods.rwf(loan.totalOutstanding()))));
        sms.send(s.getMsisdn(), sb.toString());
        audit.ussd(s.getMsisdn(), s.getSessionId(), "STATEMENT_SMS", String.valueOf(s.getLoanId()), true,
                rows.size() + " rows");
        s.end(ESessionEndReason.COMPLETED);
        return headings.text(l, "statement.sms_sent") + " " + headings.thankYou(l);
    }

    private static String shortRef(String ref) {
        if (ref == null) return "";
        return ref.length() > 6 ? ref.substring(ref.length() - 6) : ref;
    }
}
