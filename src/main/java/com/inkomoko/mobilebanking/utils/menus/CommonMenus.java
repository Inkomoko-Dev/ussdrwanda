package com.inkomoko.mobilebanking.utils.menus;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
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
    private final com.inkomoko.mobilebanking.services.CountryService countries;
    private final CommonHeadings headings;

    /** {@code 00. Home} — clears service state and shows the main menu. */
    public String backToMain(UssdSession s) {
        s.resetServiceState();
        s.setLevel(LEVEL_MAIN_MENU);
        return headings.mainMenu(s.getLanguage());
    }

    /** Alias for {@link #backToMain(UssdSession)} (USSD {@code 00}). */
    public String home(UssdSession s) {
        return backToMain(s);
    }

    public String invalidThen(UssdSession s, String screen) {
        return headings.withInvalid(s.getLanguage(), screen);
    }

    /** Ends the session with a "service unavailable" screen (core system / network failure). */
    public String unavailable(UssdSession s) {
        s.end(ESessionEndReason.ERROR);
        return headings.serviceUnavailable(s.getLanguage());
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
        String l = s.getLanguage();
        List<LoanSummaryDto> shown = loans.stream().limit(props.getUssd().getMaxLoansListed()).toList();
        StringBuilder sb = new StringBuilder(headings.selectLoan(l));
        List<String> ids = new ArrayList<>();
        int i = 1;
        for (LoanSummaryDto loan : shown) {
            sb.append('\n').append(i++).append(". ").append(label.apply(loan));
            ids.add(String.valueOf(loan.loanId()));
        }
        s.setLoanOptions(String.join(",", ids));
        return sb.append(headings.back(l)).toString();
    }

    /** Loan id for the chosen list position, or null if the choice is not on the list. */
    public Long resolveLoanChoice(UssdSession s, String input) {
        if (s.getLoanOptions() == null || s.getLoanOptions().isBlank()) return null;
        List<String> ids = Arrays.asList(s.getLoanOptions().split(","));
        int choice = GeneralMethods.parseInputAsInteger(input);
        if (choice < 1 || choice > ids.size()) return null;
        return Long.valueOf(ids.get(choice - 1));
    }

    /**
     * Selection-list label: product name and loan id, e.g. {@code SME Growth (000001001)}.
     * The name is shortened so five loans plus the two-line Back/Home footer still fit
     * one 182-character USSD screen.
     */
    public String loanListLabel(LoanSummaryDto loan) {
        String id = loan.accountNo() != null && !loan.accountNo().isBlank()
                ? loan.accountNo().trim()
                : String.valueOf(loan.loanId());
        String name = loan.productName() == null ? "" : loan.productName().trim();
        if (name.isEmpty()) return id;
        int room = 23 - (id.length() + 3);
        if (room < 4) return name + " (" + id + ")";
        if (name.length() > room) {
            String cut = name.substring(0, room);
            int space = cut.lastIndexOf(' ');
            name = (space >= 4 ? cut.substring(0, space) : cut).trim();
        }
        return name + " (" + id + ")";
    }

    public static String firstName(String displayName) {
        if (displayName == null || displayName.isBlank()) return null;
        return displayName.trim().split("\\s+")[0];
    }

    public String helpline() {
        return countries.helpline();
    }
}
