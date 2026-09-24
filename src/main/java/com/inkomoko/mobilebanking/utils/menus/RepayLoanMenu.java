package com.inkomoko.mobilebanking.utils.menus;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.enums.ESessionEndReason;
import com.inkomoko.mobilebanking.enums.ETelecom;
import com.inkomoko.mobilebanking.models.UssdSession;
import com.inkomoko.mobilebanking.models.dto.LoanSummaryDto;
import com.inkomoko.mobilebanking.services.RepaymentService;
import com.inkomoko.mobilebanking.services.integration.FineractService;
import com.inkomoko.mobilebanking.utils.GeneralMethods;
import com.inkomoko.mobilebanking.utils.PhoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 4. Repay Loan (ToR 3.1(iv), proposal §3.5 / Screens 5–7).
 *
 *   entry         → telecom check → 1 repayable loan: amount prompt | several: loan list
 *   SELECT_LOAN   → amount prompt (shows outstanding balance, live from Fineract)
 *   ENTER_AMOUNT  → validate (min RWF 500, ≤ outstanding, ≤ max) → confirm screen
 *   CONFIRM       → 1: push MoMo debit request (client approves with MoMo PIN on the phone prompt)
 *                   2: cancel
 *
 * The session ends after the push: the MNO shows its own PIN prompt, and the result is confirmed
 * by SMS once the gateway callback arrives and the repayment is posted to Fineract.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RepayLoanMenu {

    public static final String SERVICE = "repay";
    private static final String STEP_SELECT = "SELECT_LOAN";
    private static final String STEP_AMOUNT = "ENTER_AMOUNT";
    private static final String STEP_CONFIRM = "CONFIRM";

    private final CommonMenus common;
    private final CommonHeadings headings;
    private final FineractService fineract;
    private final RepaymentService repaymentService;
    private final InkomokoProperties props;
    private final com.inkomoko.mobilebanking.services.CountryService countries;

    public String processMenu(UssdSession s, String input) {
        String l = s.getLanguage();
        try {
            if (s.getStep() == null) return start(s);
            if ("0".equals(input) && !STEP_CONFIRM.equals(s.getStep())) return common.backToMain(s);

            return switch (s.getStep()) {
                case STEP_SELECT -> {
                    Long loanId = common.resolveLoanChoice(s, input);
                    if (loanId == null) {
                        s.setStep(null);
                        yield headings.invalidChoice(l) + start(s);
                    }
                    yield amountPrompt(s, fineract.getLoanDetails(loanId), "");
                }
                case STEP_AMOUNT -> handleAmount(s, input);
                case STEP_CONFIRM -> handleConfirm(s, input);
                default -> common.backToMain(s);
            };
        } catch (Exception e) {
            log.error("Repay flow failed msisdn={} step={} error={}", s.getMsisdn(), s.getStep(), e.getMessage());
            return common.unavailable(s);
        }
    }

    private String start(UssdSession s) {
        String l = s.getLanguage();
        ETelecom telecom = PhoneUtils.detectTelecom(s.getNetworkCode(), s.getMsisdn());
        if (telecom == null) {
            return common.endWith(s, ESessionEndReason.COMPLETED, headings.text(l, "repay.telecom_unsupported"));
        }
        s.setTelecom(telecom);

        List<LoanSummaryDto> loans = fineract.getClientLoans(s.getClientId()).stream()
                .filter(x -> x.status().isRepayable())
                .toList();
        if (loans.isEmpty()) {
            s.setStep(STEP_SELECT);
            s.setLoanOptions("");
            return headings.noActiveLoan(l) + headings.back(l);
        }
        if (loans.size() == 1) return amountPrompt(s, fineract.getLoanDetails(loans.get(0).loanId()), "");
        s.setStep(STEP_SELECT);
        return common.loanSelectionScreen(s, loans, common::loanListLabel);
    }

    private String amountPrompt(UssdSession s, LoanSummaryDto loan, String prefix) {
        String l = s.getLanguage();
        if (loan.clientId() != null && !loan.clientId().equals(s.getClientId())) return common.unavailable(s);
        s.setLoanId(loan.loanId());
        s.setLoanAccountNo(loan.accountNo());
        s.setOutstanding(loan.totalOutstanding());
        s.setStep(STEP_AMOUNT);
        String min = GeneralMethods.rwf(countries.minRepayment());
        String outstanding = GeneralMethods.rwf(loan.totalOutstanding());
        String overdue = GeneralMethods.rwf(loan.totalOverdue());
        return prefix + headings.text(l, "repay.amount_prompt", Map.of("account", loan.accountNo(),
                "outstanding", outstanding, "overdue", overdue, "min", min))
                + headings.back(l);
    }

    private String handleAmount(UssdSession s, String input) {
        String l = s.getLanguage();
        BigDecimal minAmount = countries.minRepayment();
        BigDecimal maxAmount = countries.maxRepayment();
        BigDecimal amount = GeneralMethods.parseAmount(input);
        BigDecimal outstanding = GeneralMethods.nz(s.getOutstanding());
        String error = null;
        if (amount == null) {
            error = headings.text(l, "repay.invalid_amount");
        } else if (amount.compareTo(minAmount) < 0) {
            error = headings.text(l, "repay.below_min", Map.of("min", GeneralMethods.rwf(minAmount)));
        } else if (outstanding.signum() > 0 && amount.compareTo(outstanding) > 0) {
            error = headings.text(l, "repay.above_balance", Map.of("balance", GeneralMethods.rwf(outstanding)));
        } else if (amount.compareTo(maxAmount) > 0) {
            error = headings.text(l, "repay.above_max", Map.of("max", GeneralMethods.rwf(maxAmount)));
        }
        if (error != null) {
            return error + "\n" + headings.text(l, "repay.enter_amount") + headings.back(l);
        }

        s.setAmount(amount);
        s.setStep(STEP_CONFIRM);
        String wallet = walletName(s.getTelecom()) + " " + PhoneUtils.mask(s.getMsisdn());
        String amt = GeneralMethods.rwf(amount);
        return headings.text(l, "repay.confirm", Map.of("amount", amt, "account", s.getLoanAccountNo(),
                "wallet", wallet)) + "\n" + headings.text(l, "repay.confirm_options");
    }

    private String handleConfirm(UssdSession s, String input) {
        String l = s.getLanguage();
        if ("2".equals(input) || "0".equals(input)) {
            return common.endWith(s, ESessionEndReason.USER_EXIT,
                    headings.text(l, "repay.cancelled") + " " + headings.thankYou(l));
        }
        if (!"1".equals(input)) {
            return headings.invalidChoice(l)
                    + headings.text(l, "repay.confirm_options");
        }

        RepaymentService.InitOutcome outcome = repaymentService.initiate(s);
        String amt = GeneralMethods.rwf(s.getAmount());
        return switch (outcome.status()) {
            case ACCEPTED -> common.endWith(s, ESessionEndReason.COMPLETED, headings.text(l, "repay.accepted",
                    Map.of("amount", amt, "ref", outcome.tx().getReference())));
            case DUPLICATE_PENDING -> common.endWith(s, ESessionEndReason.COMPLETED, headings.text(l, "repay.duplicate"));
            default -> common.endWith(s, ESessionEndReason.ERROR, headings.text(l, "repay.failed"));
        };
    }

    private static String walletName(ETelecom t) {
        return t == ETelecom.AIRTEL ? "Airtel Money" : "MTN MoMo";
    }
}
