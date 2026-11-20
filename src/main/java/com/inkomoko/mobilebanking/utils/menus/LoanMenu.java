package com.inkomoko.mobilebanking.utils.menus;

import com.inkomoko.mobilebanking.models.UssdSession;
import com.inkomoko.mobilebanking.services.CountryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 1. Loan: direct repayment (when enabled), or the mini statement for a loan. */
@Component
@RequiredArgsConstructor
public class LoanMenu {

    public static final String SERVICE = "loan";
    private static final String STEP_MENU = "MENU";

    private final CommonMenus common;
    private final CommonHeadings headings;
    private final CountryService countries;
    private final RepayLoanMenu repayLoanMenu;
    private final MiniStatementMenu miniStatementMenu;

    public String processMenu(UssdSession s, String input) {
        if (s.getStep() == null) return menu(s);
        // First (only) loan screen: 0. Back → main menu
        if ("0".equals(input)) return common.home(s);
        if (!countries.repaymentEnabled()) {
            return switch (input) {
                case "1" -> openStatement(s);
                default -> headings.withInvalid(s.getLanguage(), menu(s));
            };
        }
        return switch (input) {
            case "1" -> openRepay(s);
            case "2" -> openStatement(s);
            default -> headings.withInvalid(s.getLanguage(), menu(s));
        };
    }

    private String menu(UssdSession s) {
        s.setStep(STEP_MENU);
        String key = countries.repaymentEnabled() ? "menu.loan" : "menu.loan.no_repay";
        return headings.text(s.getLanguage(), key) + headings.back(s.getLanguage());
    }

    private String openRepay(UssdSession s) {
        s.resetServiceState();
        s.setService(RepayLoanMenu.SERVICE);
        s.setStep(null);
        return repayLoanMenu.processMenu(s, null);
    }

    private String openStatement(UssdSession s) {
        s.resetServiceState();
        s.setService(MiniStatementMenu.SERVICE);
        s.setStep(null);
        return miniStatementMenu.processMenu(s, null);
    }
}
