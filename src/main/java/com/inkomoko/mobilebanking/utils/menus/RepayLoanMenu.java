package com.inkomoko.mobilebanking.utils.menus;

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
 * Repay a loan from the caller's number or another Mobile Money number.
 *
 *   entry        → loan list
 *   SELECT_LOAN  → pay the balance, or enter another amount
 *   OFFER        → 1 balance, 2 other amount
 *   ENTER_AMOUNT → amount
 *   PAYER        → this number, or another number
 *   NETWORK      → MTN / Airtel (country networks)
 *   PHONE        → the other number, then the push is sent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RepayLoanMenu {

    public static final String SERVICE = "repay";
    public static final String STEP_OFFER = "OFFER";
    private static final String STEP_SELECT = "SELECT_LOAN";
    private static final String STEP_AMOUNT = "ENTER_AMOUNT";
    private static final String STEP_PAYER = "PAYER";
    private static final String STEP_NETWORK = "NETWORK";
    private static final String STEP_PHONE = "PHONE";

    private final CommonMenus common;
    private final CommonHeadings headings;
    private final FineractService fineract;
    private final RepaymentService repaymentService;
    private final com.inkomoko.mobilebanking.services.CountryService countries;

    public String processMenu(UssdSession s, String input) {
        if (!countries.repaymentEnabled()) return common.backToMain(s);
        String l = s.getLanguage();
        try {
            if (s.getStep() == null) return start(s);
            if ("0".equals(input)) return back(s);
            return switch (s.getStep()) {
                case STEP_SELECT -> chooseLoan(s, input);
                case STEP_OFFER -> chooseOffer(s, input);
                case STEP_AMOUNT -> handleAmount(s, input);
                case STEP_PAYER -> choosePayer(s, input);
                case STEP_NETWORK -> chooseNetwork(s, input);
                case STEP_PHONE -> handlePhone(s, input);
                default -> start(s);
            };
        } catch (Exception e) {
            log.error("Repay flow failed msisdn={} step={} error={}", s.getMsisdn(), s.getStep(), e.getMessage());
            return common.unavailable(s);
        }
    }

    /** Opens the pay screen for a loan already chosen (for example from next due date). */
    public String showOffer(UssdSession s, LoanSummaryDto loan) {
        if (!countries.repaymentEnabled()) return common.backToMain(s);
        return offer(s, loan);
    }

    /** 0. Back — one step toward the loan list / main menu. */
    private String back(UssdSession s) {
        return switch (s.getStep()) {
            case STEP_SELECT -> common.home(s);
            case STEP_OFFER -> leaveOffer(s);
            case STEP_AMOUNT, STEP_PAYER -> offer(s, fineract.getLoanDetails(s.getLoanId()));
            case STEP_NETWORK -> payer(s);
            case STEP_PHONE -> network(s);
            default -> start(s);
        };
    }

    /** One loan has no list to return to, so Back leaves the repayment flow. */
    private String leaveOffer(UssdSession s) {
        List<LoanSummaryDto> loans = fineract.getClientLoans(s.getClientId()).stream()
                .filter(x -> x.status().isRepayable()).toList();
        if (loans.size() <= 1) return common.home(s);
        s.setStep(STEP_SELECT);
        return common.loanSelectionScreen(s, loans, common::loanListLabel);
    }

    private String start(UssdSession s) {
        String l = s.getLanguage();
        s.setPayerMsisdn(null);
        List<LoanSummaryDto> loans = fineract.getClientLoans(s.getClientId()).stream()
                .filter(x -> x.status().isRepayable())
                .toList();
        if (loans.isEmpty()) {
            s.setStep(STEP_SELECT);
            return headings.noActiveLoan(l) + headings.back(l);
        }
        if (loans.size() == 1) return offer(s, fineract.getLoanDetails(loans.get(0).loanId()));
        s.setStep(STEP_SELECT);
        return common.loanSelectionScreen(s, loans, common::loanListLabel);
    }

    private String chooseLoan(UssdSession s, String input) {
        Long loanId = common.resolveLoanChoice(s, input);
        if (loanId == null) {
            s.setStep(null);
            return headings.invalidChoice(s.getLanguage()) + start(s);
        }
        return offer(s, fineract.getLoanDetails(loanId));
    }

    private String offer(UssdSession s, LoanSummaryDto loan) {
        String l = s.getLanguage();
        if (loan.clientId() != null && !loan.clientId().equals(s.getClientId())) return common.unavailable(s);
        s.setLoanId(loan.loanId());
        s.setLoanAccountNo(loan.accountNo());
        s.setOutstanding(loan.totalOutstanding());
        s.setPayerMsisdn(null);
        s.setStep(STEP_OFFER);
        String balance = GeneralMethods.rwf(loan.totalOutstanding());
        return headings.text(l, "repay.offer", Map.of("account", loan.accountNo(), "balance", balance))
                + headings.back(l);
    }

    private String chooseOffer(UssdSession s, String input) {
        String l = s.getLanguage();
        if ("1".equals(input)) {
            BigDecimal balance = GeneralMethods.nz(s.getOutstanding());
            if (balance.signum() <= 0) {
                return headings.text(l, "repay.above_balance", Map.of("balance", GeneralMethods.rwf(balance)))
                        + "\n" + offer(s, fineract.getLoanDetails(s.getLoanId()));
            }
            s.setAmount(balance);
            return payer(s);
        }
        if ("2".equals(input)) {
            s.setStep(STEP_AMOUNT);
            return headings.text(l, "repay.enter_amount") + headings.back(l);
        }
        return headings.invalidChoice(l) + offer(s, fineract.getLoanDetails(s.getLoanId()));
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
        return payer(s);
    }

    private String payer(UssdSession s) {
        s.setStep(STEP_PAYER);
        s.setPayerMsisdn(null);
        return headings.text(s.getLanguage(), "repay.payer", Map.of("amount", GeneralMethods.rwf(s.getAmount())))
                + headings.back(s.getLanguage());
    }

    private String choosePayer(UssdSession s, String input) {
        if ("1".equals(input)) {
            ETelecom telecom = PhoneUtils.detectTelecom(s.getNetworkCode(), s.getMsisdn());
            if (telecom == null) {
                return common.endWith(s, ESessionEndReason.COMPLETED,
                        headings.text(s.getLanguage(), "repay.telecom_unsupported"));
            }
            s.setTelecom(telecom);
            s.setPayerMsisdn(null);
            return push(s);
        }
        if ("2".equals(input)) return network(s);
        return headings.invalidChoice(s.getLanguage()) + payer(s);
    }

    private String network(UssdSession s) {
        s.setStep(STEP_NETWORK);
        StringBuilder sb = new StringBuilder(headings.text(s.getLanguage(), "repay.network"));
        int i = 1;
        for (String name : countries.mobileNetworks()) {
            sb.append('\n').append(i++).append(". ").append(name);
        }
        return sb.append(headings.back(s.getLanguage())).toString();
    }

    private String chooseNetwork(UssdSession s, String input) {
        List<String> networks = countries.mobileNetworks();
        int choice = GeneralMethods.parseInputAsInteger(input);
        if (choice < 1 || choice > networks.size()) {
            return headings.invalidChoice(s.getLanguage()) + network(s);
        }
        ETelecom telecom = PhoneUtils.detectTelecom(networks.get(choice - 1), null);
        if (telecom == null) {
            return headings.invalidChoice(s.getLanguage()) + network(s);
        }
        s.setTelecom(telecom);
        s.setStep(STEP_PHONE);
        return headings.text(s.getLanguage(), "repay.phone") + headings.back(s.getLanguage());
    }

    private String handlePhone(UssdSession s, String input) {
        String phone = PhoneUtils.purify(input);
        if (!phone.matches("[0-9]{9,15}")) {
            return headings.text(s.getLanguage(), "repay.phone_invalid") + "\n"
                    + headings.text(s.getLanguage(), "repay.phone") + headings.back(s.getLanguage());
        }
        s.setPayerMsisdn(phone);
        return push(s);
    }

    private String push(UssdSession s) {
        String l = s.getLanguage();
        RepaymentService.InitOutcome outcome = repaymentService.initiate(s);
        String amount = GeneralMethods.rwf(s.getAmount());
        String wallet = walletName(s.getTelecom());
        return switch (outcome.status()) {
            case ACCEPTED -> common.endWith(s, ESessionEndReason.COMPLETED,
                    headings.text(l, "repay.initiated", Map.of("amount", amount, "wallet", wallet)));
            case DUPLICATE_PENDING -> common.endWith(s, ESessionEndReason.COMPLETED, headings.text(l, "repay.duplicate"));
            default -> common.endWith(s, ESessionEndReason.ERROR, headings.text(l, "repay.failed"));
        };
    }

    private static String walletName(ETelecom t) {
        return t == ETelecom.AIRTEL ? "Airtel Money" : "MTN MoMo";
    }
}
