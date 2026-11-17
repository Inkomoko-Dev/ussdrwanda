package com.inkomoko.mobilebanking.utils.menus;

import com.inkomoko.mobilebanking.enums.ESessionEndReason;
import com.inkomoko.mobilebanking.models.UssdSession;
import com.inkomoko.mobilebanking.models.dto.LoanSummaryDto;
import com.inkomoko.mobilebanking.services.CountryService;
import com.inkomoko.mobilebanking.services.integration.FineractService;
import com.inkomoko.mobilebanking.utils.DateUtility;
import com.inkomoko.mobilebanking.utils.GeneralMethods;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 3. Account info: mini statement, next due date, registered phone.
 * Changing the phone is not done on USSD; the customer is told to contact Inkomoko.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountInfoMenu {

    public static final String SERVICE = "account";
    private static final String STEP_MENU = "MENU";
    private static final String STEP_SELECT = "SELECT_LOAN";
    private static final String STEP_DUE = "DUE";
    private static final String STEP_PHONE = "PHONE";

    private final CommonMenus common;
    private final CommonHeadings headings;
    private final CountryService countries;
    private final FineractService fineract;
    private final MiniStatementMenu miniStatementMenu;
    private final RepayLoanMenu repayLoanMenu;

    public String processMenu(UssdSession s, String input) {
        try {
            if (s.getStep() == null) return menu(s);
            if ("0".equals(input) && !STEP_MENU.equals(s.getStep())) return menu(s);
            if ("0".equals(input)) return common.backToMain(s);
            return switch (s.getStep()) {
                case STEP_MENU -> choose(s, input);
                case STEP_SELECT -> chooseLoan(s, input);
                case STEP_DUE -> repay(s, input);
                case STEP_PHONE -> changePhone(s, input);
                default -> menu(s);
            };
        } catch (Exception e) {
            log.error("Account info failed msisdn={} error={}", s.getMsisdn(), e.getMessage());
            return common.unavailable(s);
        }
    }

    private String menu(UssdSession s) {
        s.setStep(STEP_MENU);
        return headings.text(s.getLanguage(), "menu.account") + headings.back(s.getLanguage());
    }

    private String choose(UssdSession s, String input) {
        return switch (input) {
            case "1" -> openStatement(s);
            case "2" -> startDue(s);
            case "3" -> phone(s);
            default -> headings.invalidChoice(s.getLanguage()) + menu(s);
        };
    }

    private String openStatement(UssdSession s) {
        s.resetServiceState();
        s.setService(MiniStatementMenu.SERVICE);
        s.setStep(null);
        return miniStatementMenu.processMenu(s, null);
    }

    private String startDue(UssdSession s) {
        List<LoanSummaryDto> loans = fineract.getClientLoans(s.getClientId()).stream()
                .filter(x -> x.status().isRepayable()).toList();
        if (loans.isEmpty()) return headings.noActiveLoan(s.getLanguage()) + headings.back(s.getLanguage());
        if (loans.size() == 1) return due(s, fineract.getLoanDetails(loans.get(0).loanId()));
        s.setStep(STEP_SELECT);
        return common.loanSelectionScreen(s, loans, common::loanListLabel);
    }

    private String chooseLoan(UssdSession s, String input) {
        Long loanId = common.resolveLoanChoice(s, input);
        if (loanId == null) {
            s.setStep(null);
            return headings.invalidChoice(s.getLanguage()) + startDue(s);
        }
        return due(s, fineract.getLoanDetails(loanId));
    }

    private String due(UssdSession s, LoanSummaryDto loan) {
        if (loan.clientId() != null && !loan.clientId().equals(s.getClientId())) return common.unavailable(s);
        s.setLoanId(loan.loanId());
        s.setLoanAccountNo(loan.accountNo());
        s.setOutstanding(loan.totalOutstanding());
        s.setStep(STEP_DUE);
        String due = DateUtility.screenDate(loan.nextDueDate());
        String amount = loan.nextInstallment() == null ? "-" : GeneralMethods.rwf(loan.nextInstallment());
        String key = countries.repaymentEnabled() ? "account.due" : "account.due.no_repay";
        return headings.text(s.getLanguage(), key, Map.of("account", loan.accountNo(), "due", due, "amount", amount))
                + headings.back(s.getLanguage());
    }

    private String repay(UssdSession s, String input) {
        if (!countries.repaymentEnabled() || !"1".equals(input)) {
            return headings.invalidChoice(s.getLanguage()) + due(s, fineract.getLoanDetails(s.getLoanId()));
        }
        s.setService(RepayLoanMenu.SERVICE);
        return repayLoanMenu.showOffer(s, fineract.getLoanDetails(s.getLoanId()));
    }

    private String phone(UssdSession s) {
        s.setStep(STEP_PHONE);
        return headings.text(s.getLanguage(), "account.phone", Map.of("msisdn", s.getMsisdn()))
                + headings.back(s.getLanguage());
    }

    private String changePhone(UssdSession s, String input) {
        if (!"1".equals(input)) return headings.invalidChoice(s.getLanguage()) + phone(s);
        return common.endWith(s, ESessionEndReason.COMPLETED,
                headings.text(s.getLanguage(), "account.phone_change", Map.of("helpline", common.helpline())));
    }
}
