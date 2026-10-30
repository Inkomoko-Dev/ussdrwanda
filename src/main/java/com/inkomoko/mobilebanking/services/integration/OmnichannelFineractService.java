package com.inkomoko.mobilebanking.services.integration;

import com.inkomoko.mobilebanking.enums.ELoanStatus;
import com.inkomoko.mobilebanking.models.dto.ClientDto;
import com.inkomoko.mobilebanking.models.dto.LoanSummaryDto;
import com.inkomoko.mobilebanking.models.dto.RepaymentDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Core banking through the Inkomoko Omnichannel API (inkomoko.fineract.mode=omnichannel).
 *
 * Client ids are the core-banking customer ids returned by /customer/profile and loan ids are
 * the core-banking loan ids from /loans/active, so the rest of the platform keeps working on the
 * same numeric ids as in live Fineract mode. Repayments are posted by the Payment Hub, not by
 * this platform, so the posting and reversal operations are not available here.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "inkomoko.fineract.mode", havingValue = "omnichannel", matchIfMissing = true)
public class OmnichannelFineractService implements FineractService {

    private final OmnichannelClient api;

    public OmnichannelFineractService(OmnichannelClient api) {
        this.api = api;
    }

    @Override
    public Optional<ClientDto> findClientByMsisdn(String msisdn) {
        Map<String, Object> v = map(api.post("/ussd/phone/validate", Map.of("phoneNumber", msisdn)));
        if (!Boolean.TRUE.equals(v.get("registered"))) return Optional.empty();
        Map<String, Object> profile;
        try {
            profile = map(api.get("/customer/profile", Map.of("phoneNumber", msisdn)));
        } catch (OmnichannelException e) {
            if (e.isNotFound()) return Optional.empty();
            throw e;
        }
        boolean active = !Boolean.FALSE.equals(v.get("valid"))
                && "ACTIVE".equalsIgnoreCase(str(v.get("accountStatus")))
                && !Boolean.FALSE.equals(v.get("ussdEnabled"));
        String name = firstNonBlank(str(profile.get("displayName")), str(v.get("customerName")));
        return Optional.of(new ClientDto(customerId(profile), name,
                firstNonBlank(str(profile.get("phoneNumber")), msisdn), str(profile.get("email")), active));
    }

    @Override
    public Optional<ClientDto> findClientById(Long clientId) {
        Map<String, Object> profile;
        try {
            profile = map(api.get("/customer/profile", Map.of("customerId", clientId)));
        } catch (OmnichannelException e) {
            if (e.isNotFound()) return Optional.empty();
            throw e;
        }
        if (profile.isEmpty()) return Optional.empty();
        return Optional.of(new ClientDto(customerId(profile), str(profile.get("displayName")),
                str(profile.get("phoneNumber")), str(profile.get("email")),
                "ACTIVE".equalsIgnoreCase(str(profile.get("accountStatus")))));
    }

    /** Omnichannel has no client update endpoint; the portal keeps its own copy of the edit. */
    @Override
    public void updateClient(Long clientId, String displayName, String mobileNo, String email) {
        log.info("Omnichannel mode: client {} details changed locally only (no core-banking update API)", clientId);
    }

    @Override
    public List<LoanSummaryDto> getClientLoans(Long clientId) {
        List<LoanSummaryDto> loans = new ArrayList<>();
        for (Object o : list(api.get("/loans/active", Map.of("customerId", clientId)))) {
            Map<String, Object> l = map(o);
            BigDecimal overdue = overdue(l);
            loans.add(new LoanSummaryDto(toLong(l.get("loanId")), clientId, str(l.get("accountNo")),
                    str(l.get("productName")), status(str(l.get("status")), overdue, null),
                    num(l.get("principalOutstanding")), num(l.get("outstandingBalance")), overdue,
                    date(l.get("nextDueDate")), num(l.get("nextPaymentDue"))));
        }
        return loans;
    }

    @Override
    public LoanSummaryDto getLoanDetails(Long loanId) {
        Map<String, Object> d = map(api.get("/loans/details", Map.of("loanId", loanId)));
        String accountNo = str(d.get("accountNo"));
        BigDecimal overdue = null;
        if (accountNo != null) {
            try {
                overdue = overdue(map(api.get("/ussd/loans/balance", Map.of("loanNumber", accountNo))));
            } catch (OmnichannelException e) {
                log.warn("Overdue lookup failed for loan {}: {}", loanId, e.getMessage());
            }
        }
        Integer daysInArrears = d.get("daysInArrears") instanceof Number n ? n.intValue() : null;
        return new LoanSummaryDto(toLong(d.get("loanId")) == null ? loanId : toLong(d.get("loanId")),
                toLong(d.get("customerId")), accountNo, str(d.get("productName")),
                status(str(d.get("loanStatus")), overdue, daysInArrears),
                num(d.get("principalOutstanding")), num(d.get("outstandingBalance")), overdue,
                date(d.get("nextDueDate")), num(d.get("nextPaymentDue")));
    }

    @Override
    public List<RepaymentDto> getRepayments(Long loanId, int limit) {
        String accountNo = str(map(api.get("/loans/details", Map.of("loanId", loanId))).get("accountNo"));
        if (accountNo == null) return List.of();
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("loanNumber", accountNo);
        query.put("limit", Math.max(limit * 3, limit));
        Map<String, Object> statement = map(api.get("/ussd/loans/mini-statement", query));
        List<RepaymentDto> out = new ArrayList<>();
        for (Object o : list(statement.get("transactions"))) {
            Map<String, Object> t = map(o);
            if (Boolean.TRUE.equals(t.get("reversed")) || !isRepayment(t)) continue;
            String txnId = str(t.get("transactionId"));
            out.add(new RepaymentDto(toLong(txnId),
                    date(t.get("transactionDate") != null ? t.get("transactionDate") : t.get("transactionDateTime")),
                    num(t.get("amount")), firstNonBlank(str(t.get("externalId")), txnId)));
            if (out.size() >= limit) break;
        }
        return out;
    }

    @Override
    public Optional<String> findRepaymentByExternalId(Long loanId, String externalId) {
        throw notSupported();
    }

    @Override
    public String postRepayment(Long loanId, BigDecimal amount, LocalDate date, String externalId,
                                String receiptNumber, String note) {
        throw notSupported();
    }

    @Override
    public void reverseRepayment(Long loanId, String fineractTxnId) {
        throw notSupported();
    }

    // ── mapping ────────────────────────────────────────────────────────────

    static ELoanStatus status(String raw, BigDecimal overdue, Integer daysInArrears) {
        String s = raw == null ? "" : raw.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        boolean arrears = (overdue != null && overdue.signum() > 0) || (daysInArrears != null && daysInArrears > 0);
        if (s.contains("ARREAR") || s.contains("OVERDUE")) return ELoanStatus.IN_ARREARS;
        if (s.contains("ACTIVE") || s.contains("DISBURSED")) return arrears ? ELoanStatus.IN_ARREARS : ELoanStatus.ACTIVE;
        if (s.contains("OBLIGATIONS_MET") || s.contains("SETTLED") || s.contains("OVERPAID") || s.equals("PAID")) {
            return ELoanStatus.SETTLED;
        }
        if (s.contains("REJECT")) return ELoanStatus.REJECTED;
        if (s.contains("WITHDRAW")) return ELoanStatus.WITHDRAWN;
        if (s.contains("APPROVED")) return ELoanStatus.APPROVED;
        if (s.contains("SUBMITTED") || s.contains("PENDING") || s.contains("REVIEW")) return ELoanStatus.UNDER_REVIEW;
        if (s.contains("CLOSED") || s.contains("WRITTEN")) return ELoanStatus.CLOSED;
        return arrears ? ELoanStatus.IN_ARREARS : ELoanStatus.ACTIVE;
    }

    private static BigDecimal overdue(Map<String, Object> loan) {
        Map<String, Object> total = map(map(loan.get("summary")).get("total"));
        return num(total.get("overdue"));
    }

    private static boolean isRepayment(Map<String, Object> t) {
        String type = (str(t.get("type")) + " " + str(t.get("description"))).toUpperCase();
        return type.contains("REPAY") || type.contains("PAYMENT");
    }

    private static Long customerId(Map<String, Object> profile) {
        Long id = toLong(profile.get("customerId"));
        if (id == null) {
            throw new OmnichannelException("Omnichannel profile has no numeric customerId", 0, "BAD_PROFILE", null);
        }
        return id;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static List<?> list(Object o) {
        return o instanceof List<?> l ? l : List.of();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static BigDecimal num(Object o) {
        if (o == null) return null;
        try {
            return new BigDecimal(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        try {
            return new BigDecimal(o.toString().trim()).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    /** Accepts "2026-10-30", "2026-10-30T10:15:00" or a [yyyy, m, d] array. */
    private static LocalDate date(Object o) {
        if (o == null) return null;
        if (o instanceof List<?> l && l.size() >= 3) {
            return LocalDate.of(((Number) l.get(0)).intValue(), ((Number) l.get(1)).intValue(),
                    ((Number) l.get(2)).intValue());
        }
        String s = o.toString();
        try {
            return LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static UnsupportedOperationException notSupported() {
        return new UnsupportedOperationException(
                "Repayments are posted to core banking by the Omnichannel Payment Hub in this mode");
    }
}
