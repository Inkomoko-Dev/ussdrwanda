package com.inkomoko.mobilebanking.utils.menus;

import com.inkomoko.mobilebanking.enums.ELanguage;
import com.inkomoko.mobilebanking.enums.ESessionEndReason;
import com.inkomoko.mobilebanking.models.UssdSession;
import com.inkomoko.mobilebanking.models.dto.LoanSummaryDto;
import com.inkomoko.mobilebanking.services.AuditService;
import com.inkomoko.mobilebanking.services.integration.FineractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 2. Loan Status (ToR 3.1(ii)) — every loan of the client with its mapped status:
 * Under Review / Approved - Pending Disbursement / Active / In Arrears / Settled.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanStatusMenu {

    public static final String SERVICE = "status";
    private static final String STEP_DISPLAY = "DISPLAY";

    private final CommonMenus common;
    private final FineractService fineract;
    private final AuditService audit;
    private final com.inkomoko.mobilebanking.config.InkomokoProperties props;

    public String processMenu(UssdSession s, String input) {
        ELanguage l = s.getLanguage();
        if (s.getStep() != null) {
            if ("0".equals(input)) return common.backToMain(s);
            return common.invalidThen(s, CommonHeadings.back(l).trim());
        }
        try {
            List<LoanSummaryDto> loans = fineract.getClientLoans(s.getClientId()).stream()
                    // most relevant first: open loans, then pipeline, then closed
                    .sorted(Comparator.comparingInt(x -> rank(x.status().name())))
                    .limit(props.getUssd().getMaxLoansListed())
                    .toList();
            s.setStep(STEP_DISPLAY);
            s.setEndReason(ESessionEndReason.COMPLETED);
            audit.ussd(s.getMsisdn(), s.getSessionId(), "LOAN_STATUS", String.valueOf(s.getClientId()), true, null);
            if (loans.isEmpty()) return CommonHeadings.noLoans(l) + CommonHeadings.back(l);

            StringBuilder sb = new StringBuilder(l.pick("UKO IDENI RIHAGAZE", "LOAN STATUS", "STATUT DU PRET"));
            for (LoanSummaryDto loan : loans) {
                sb.append('\n').append(loan.accountNo()).append(": ").append(loan.status().label(l));
            }
            return sb.append(CommonHeadings.back(l)).toString();
        } catch (Exception e) {
            log.error("Loan status failed msisdn={} error={}", s.getMsisdn(), e.getMessage());
            return common.unavailable(s);
        }
    }

    private static int rank(String status) {
        return switch (status) {
            case "IN_ARREARS" -> 0;
            case "ACTIVE" -> 1;
            case "APPROVED" -> 2;
            case "UNDER_REVIEW" -> 3;
            case "SETTLED" -> 4;
            default -> 5;
        };
    }
}
