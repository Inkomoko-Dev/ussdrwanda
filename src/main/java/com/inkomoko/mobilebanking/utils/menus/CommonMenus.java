package com.inkomoko.mobilebanking.utils.menus;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.enums.ELanguage;
import com.inkomoko.mobilebanking.enums.ESessionEndReason;
import com.inkomoko.mobilebanking.models.UssdSession;
import com.inkomoko.mobilebanking.models.dto.LoanSummaryDto;
import com.inkomoko.mobilebanking.utils.GeneralMethods;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/** Shared screen helpers used by all service menus. */
@Component
@RequiredArgsConstructor
public class CommonMenus {

    public static final int LEVEL_MAIN_MENU = 4;
    public static final int LEVEL_SERVICE = 5;

    private final InkomokoProperties props;

    /** Clears service state and shows the main menu. */
    public String backToMain(UssdSession s) {
        s.resetServiceState();
        s.setLevel(LEVEL_MAIN_MENU);
        return CommonHeadings.mainMenu(s.getLanguage());
    }

    public String invalidThen(UssdSession s, String screen) {
        return CommonHeadings.invalidChoice(s.getLanguage()) + screen;
    }

    /** Ends the session with a "service unavailable" screen (core system / network failure). */
    public String unavailable(UssdSession s) {
        s.end(ESessionEndReason.ERROR);
        return CommonHeadings.serviceUnavailable(s.getLanguage());
    }

    public String endWith(UssdSession s, ESessionEndReason reason, String text) {
        s.end(reason);
        return text;
    }

    /**
     * Lists loans for selection and remembers their order in the session
     * so the next input ("1", "2", ...) maps back to a loan id.
     */
    public String loanSelectionScreen(UssdSession s, List<LoanSummaryDto> loans,
                                      Function<LoanSummaryDto, String> label) {
        ELanguage l = s.getLanguage();
        List<LoanSummaryDto> shown = loans.stream().limit(props.getUssd().getMaxLoansListed()).toList();
        StringBuilder sb = new StringBuilder(CommonHeadings.selectLoan(l));
        List<String> ids = new ArrayList<>();
        int i = 1;
        for (LoanSummaryDto loan : shown) {
            sb.append('\n').append(i++).append(". ").append(label.apply(loan));
            ids.add(String.valueOf(loan.loanId()));
        }
        s.setLoanOptions(String.join(",", ids));
        return sb.append(CommonHeadings.back(l)).toString();
    }

    /** Loan id for the chosen list position, or null if the choice is not on the list. */
    public Long resolveLoanChoice(UssdSession s, String input) {
        if (s.getLoanOptions() == null || s.getLoanOptions().isBlank()) return null;
        List<String> ids = Arrays.asList(s.getLoanOptions().split(","));
        int choice = GeneralMethods.parseInputAsInteger(input);
        if (choice < 1 || choice > ids.size()) return null;
        return Long.valueOf(ids.get(choice - 1));
    }

    /** Loan label for selection lists: account number and outstanding balance. */
    public String balanceLabel(LoanSummaryDto loan) {
        return loan.accountNo() + " " + GeneralMethods.rwf(loan.totalOutstanding());
    }

    public static String firstName(String displayName) {
        if (displayName == null || displayName.isBlank()) return null;
        return displayName.trim().split("\\s+")[0];
    }

    public String helpline() {
        return props.getSupport().getHelpline();
    }
}
