package com.inkomoko.mobilebanking.utils.menus;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.enums.ELanguage;
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
    private final FineractService fineract;
    private final RepaymentService repaymentService;
    private final InkomokoProperties props;

    public String processMenu(UssdSession s, String input) {
        ELanguage l = s.getLanguage();
        try {
            if (s.getStep() == null) return start(s);
            if ("0".equals(input) && !STEP_CONFIRM.equals(s.getStep())) return common.backToMain(s);

            return switch (s.getStep()) {
                case STEP_SELECT -> {
                    Long loanId = common.resolveLoanChoice(s, input);
                    if (loanId == null) {
                        s.setStep(null);
                        yield CommonHeadings.invalidChoice(l) + start(s);
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
        ELanguage l = s.getLanguage();
        ETelecom telecom = PhoneUtils.detectTelecom(s.getNetworkCode(), s.getMsisdn());
        if (telecom == null) {
            return common.endWith(s, ESessionEndReason.COMPLETED, l.pick(
                    "Kwishyura ukoresheje Mobile Money bikorwa kuri MTN na Airtel gusa.",
                    "Mobile Money repayment is available for MTN and Airtel numbers only.",
                    "Le remboursement Mobile Money est disponible uniquement pour MTN et Airtel."));
        }
        s.setTelecom(telecom);

        List<LoanSummaryDto> loans = fineract.getClientLoans(s.getClientId()).stream()
                .filter(x -> x.status().isRepayable())
                .toList();
        if (loans.isEmpty()) {
            s.setStep(STEP_SELECT);
            s.setLoanOptions("");
            return CommonHeadings.noActiveLoan(l) + CommonHeadings.back(l);
        }
        if (loans.size() == 1) return amountPrompt(s, fineract.getLoanDetails(loans.get(0).loanId()), "");
        s.setStep(STEP_SELECT);
        return common.loanSelectionScreen(s, loans, common::balanceLabel);
    }

    private String amountPrompt(UssdSession s, LoanSummaryDto loan, String prefix) {
        ELanguage l = s.getLanguage();
        if (loan.clientId() != null && !loan.clientId().equals(s.getClientId())) return common.unavailable(s);
        s.setLoanId(loan.loanId());
        s.setLoanAccountNo(loan.accountNo());
        s.setOutstanding(loan.totalOutstanding());
        s.setStep(STEP_AMOUNT);
        String min = GeneralMethods.rwf(props.getRepayment().getMinAmount());
        String outstanding = GeneralMethods.rwf(loan.totalOutstanding());
        String overdue = GeneralMethods.rwf(loan.totalOverdue());
        return prefix + l.pick(
                "Ideni " + loan.accountNo() + "\nAmafaranga usigaje kwishyura: " + outstanding
                        + "\nAyarengeje igihe: " + overdue + "\nAndika amafaranga ushaka kwishyura (min " + min + ")",
                "Loan " + loan.accountNo() + "\nOutstanding: " + outstanding + "\nOverdue: " + overdue
                        + "\nEnter amount to pay (min " + min + ")",
                "Pret " + loan.accountNo() + "\nReste a payer: " + outstanding + "\nEn retard: " + overdue
                        + "\nSaisissez le montant (min " + min + ")")
                + CommonHeadings.back(l);
    }

    private String handleAmount(UssdSession s, String input) {
        ELanguage l = s.getLanguage();
        InkomokoProperties.Repayment cfg = props.getRepayment();
        BigDecimal amount = GeneralMethods.parseAmount(input);
        BigDecimal outstanding = GeneralMethods.nz(s.getOutstanding());
        String error = null;
        if (amount == null) {
            error = l.pick("Umubare si wo.\n", "Invalid amount.\n", "Montant invalide.\n");
        } else if (amount.compareTo(cfg.getMinAmount()) < 0) {
            error = l.pick("Nibura ni ", "Minimum is ", "Le minimum est ") + GeneralMethods.rwf(cfg.getMinAmount()) + ".\n";
        } else if (outstanding.signum() > 0 && amount.compareTo(outstanding) > 0) {
            error = l.pick("Birenze ayo usigaje: ", "Amount exceeds balance: ", "Montant superieur au solde: ")
                    + GeneralMethods.rwf(outstanding) + ".\n";
        } else if (amount.compareTo(cfg.getMaxAmount()) > 0) {
            error = l.pick("Ntarengwa ni ", "Maximum is ", "Le maximum est ") + GeneralMethods.rwf(cfg.getMaxAmount()) + ".\n";
        }
        if (error != null) {
            return error + l.pick("Andika amafaranga:", "Enter amount:", "Saisissez le montant:") + CommonHeadings.back(l);
        }

        s.setAmount(amount);
        s.setStep(STEP_CONFIRM);
        String wallet = walletName(s.getTelecom()) + " " + PhoneUtils.mask(s.getMsisdn());
        String amt = GeneralMethods.rwf(amount);
        return l.pick(
                "Urashaka kwishyura " + amt + " ku ideni " + s.getLoanAccountNo() + " ukoresheje " + wallet
                        + ". Emeza?\n1. Emeza\n2. Hagarika",
                "Pay " + amt + " to loan " + s.getLoanAccountNo() + " from " + wallet + ". Confirm?\n1. Confirm\n2. Cancel",
                "Payer " + amt + " pour le pret " + s.getLoanAccountNo() + " depuis " + wallet
                        + ". Confirmer?\n1. Confirmer\n2. Annuler");
    }

    private String handleConfirm(UssdSession s, String input) {
        ELanguage l = s.getLanguage();
        if ("2".equals(input) || "0".equals(input)) {
            return common.endWith(s, ESessionEndReason.USER_EXIT,
                    l.pick("Kwishyura byahagaritswe. ", "Payment cancelled. ", "Paiement annule. ")
                            + CommonHeadings.thankYou(l));
        }
        if (!"1".equals(input)) {
            return CommonHeadings.invalidChoice(l)
                    + l.pick("1. Emeza\n2. Hagarika", "1. Confirm\n2. Cancel", "1. Confirmer\n2. Annuler");
        }

        RepaymentService.InitOutcome outcome = repaymentService.initiate(s);
        String amt = GeneralMethods.rwf(s.getAmount());
        return switch (outcome.status()) {
            case ACCEPTED -> common.endWith(s, ESessionEndReason.COMPLETED, l.pick(
                    "Shyiramo umubare w'ibanga wa Mobile Money ku butumwa ugiye kubona kugira ngo wemeze " + amt
                            + ". Urabona SMS yemeza. Ref: " + outcome.tx().getReference(),
                    "Enter your Mobile Money PIN on the prompt you will receive to approve " + amt
                            + ". An SMS receipt will follow. Ref: " + outcome.tx().getReference(),
                    "Entrez votre PIN Mobile Money sur la demande recue pour approuver " + amt
                            + ". Un recu SMS suivra. Ref: " + outcome.tx().getReference()));
            case DUPLICATE_PENDING -> common.endWith(s, ESessionEndReason.COMPLETED, l.pick(
                    "Ufite ubwishyu bugitegereje kwemezwa. Bwemeze kuri telefoni cyangwa ugerageze nyuma y'iminota mike.",
                    "You have a payment awaiting approval. Approve it on your phone or try again in a few minutes.",
                    "Un paiement est en attente d'approbation. Validez-le ou reessayez dans quelques minutes."));
            default -> common.endWith(s, ESessionEndReason.ERROR, l.pick(
                    "Ntibyakunze kohereza icyifuzo cyo kwishyura. Mwongere mugerageze nyuma.",
                    "The payment request could not be sent. Please try again later.",
                    "La demande de paiement n'a pas pu etre envoyee. Reessayez plus tard."));
        };
    }

    private static String walletName(ETelecom t) {
        return t == ETelecom.AIRTEL ? "Airtel Money" : "MTN MoMo";
    }
}
