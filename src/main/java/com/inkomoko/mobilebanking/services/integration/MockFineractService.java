package com.inkomoko.mobilebanking.services.integration;

import com.inkomoko.mobilebanking.enums.ELoanStatus;
import com.inkomoko.mobilebanking.models.dto.ClientDto;
import com.inkomoko.mobilebanking.models.dto.LoanSummaryDto;
import com.inkomoko.mobilebanking.models.dto.RepaymentDto;
import com.inkomoko.mobilebanking.utils.DateUtility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory stand-in for Fineract, used until sandbox access is available
 * (inkomoko.fineract.mode=mock). Test clients:
 *
 *   250788000001  Jean Uwimana     — 1 active loan (+1 settled)
 *   250788000002  Aline Mukamana   — 2 repayable loans (1 in arrears) + 1 under review
 *   250722000003  Eric Habimana    — 1 approved loan pending disbursement (Airtel)
 *   250788000004  Inactive client
 *   any other     — not registered
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "inkomoko.fineract.mode", havingValue = "mock", matchIfMissing = true)
public class MockFineractService implements FineractService {

    private final Map<String, ClientDto> clients = new HashMap<>();
    private final Map<Long, MockLoan> loans = new LinkedHashMap<>();
    private final AtomicLong txSeq = new AtomicLong(90000);

    private static final class MockLoan {
        Long id;
        Long clientId;
        String accountNo;
        String product;
        ELoanStatus status;
        BigDecimal principal;
        BigDecimal interest;
        BigDecimal overdue;
        BigDecimal installment;
        LocalDate nextDue;
        final List<RepaymentDto> repayments = new ArrayList<>();
        final Map<String, String> externalIds = new HashMap<>();

        BigDecimal total() {
            return principal.add(interest);
        }
    }

    public MockFineractService() {
        LocalDate today = DateUtility.todayKigali();
        clients.put("250788000001", new ClientDto(1L, "Jean Uwimana", "0788000001", true));
        clients.put("250788000002", new ClientDto(2L, "Aline Mukamana", "0788000002", true));
        clients.put("250722000003", new ClientDto(3L, "Eric Habimana", "0722000003", true));
        clients.put("250788000004", new ClientDto(4L, "Closed Client", "0788000004", false));

        MockLoan l1 = loan(1001L, 1L, "000001001", "SME Growth Loan", ELoanStatus.ACTIVE,
                "250000", "15000", "15000", "15000", today.plusDays(11));
        history(l1, today, 5, "15000");
        MockLoan settled = loan(1002L, 1L, "000001002", "Starter Loan", ELoanStatus.SETTLED,
                "0", "0", "0", "0", null);
        history(settled, today.minusMonths(8), 3, "20000");

        MockLoan l2 = loan(2001L, 2L, "000002001", "Refugee Enterprise Loan", ELoanStatus.IN_ARREARS,
                "120000", "8000", "32000", "16000", today.minusDays(4));
        history(l2, today.minusMonths(1), 2, "16000");
        loan(2002L, 2L, "000002002", "Asset Loan", ELoanStatus.ACTIVE,
                "80000", "4000", "0", "10000", today.plusDays(20));
        loan(2003L, 2L, "000002003", "Working Capital", ELoanStatus.UNDER_REVIEW,
                "0", "0", "0", "0", null);

        loan(3001L, 3L, "000003001", "SME Growth Loan", ELoanStatus.APPROVED,
                "0", "0", "0", "0", null);
        log.warn("Fineract is in MOCK mode — using in-memory test clients 250788000001/2, 250722000003");
    }

    private MockLoan loan(Long id, Long clientId, String accountNo, String product, ELoanStatus status,
                          String principal, String interest, String overdue, String installment, LocalDate nextDue) {
        MockLoan l = new MockLoan();
        l.id = id;
        l.clientId = clientId;
        l.accountNo = accountNo;
        l.product = product;
        l.status = status;
        l.principal = new BigDecimal(principal);
        l.interest = new BigDecimal(interest);
        l.overdue = new BigDecimal(overdue);
        l.installment = new BigDecimal(installment);
        l.nextDue = nextDue;
        loans.put(id, l);
        return l;
    }

    private void history(MockLoan l, LocalDate lastDate, int count, String amount) {
        for (int i = count - 1; i >= 0; i--) {
            long id = txSeq.incrementAndGet();
            l.repayments.add(new RepaymentDto(id, lastDate.minusMonths(i), new BigDecimal(amount), "MM" + id));
        }
    }

    @Override
    public Optional<ClientDto> findClientByMsisdn(String msisdn) {
        return Optional.ofNullable(clients.get(msisdn));
    }

    @Override
    public synchronized List<LoanSummaryDto> getClientLoans(Long clientId) {
        return loans.values().stream()
                .filter(l -> l.clientId.equals(clientId))
                .map(this::toDto)
                .toList();
    }

    @Override
    public synchronized LoanSummaryDto getLoanDetails(Long loanId) {
        MockLoan l = loans.get(loanId);
        if (l == null) throw new FineractException("Loan " + loanId + " not found");
        return toDto(l);
    }

    @Override
    public synchronized List<RepaymentDto> getRepayments(Long loanId, int limit) {
        MockLoan l = loans.get(loanId);
        if (l == null) return List.of();
        List<RepaymentDto> list = new ArrayList<>(l.repayments);
        Collections.reverse(list);
        return limit > 0 && list.size() > limit ? list.subList(0, limit) : list;
    }

    @Override
    public synchronized Optional<String> findRepaymentByExternalId(Long loanId, String externalId) {
        MockLoan l = loans.get(loanId);
        return l == null ? Optional.empty() : Optional.ofNullable(l.externalIds.get(externalId));
    }

    @Override
    public synchronized String postRepayment(Long loanId, BigDecimal amount, LocalDate date, String externalId,
                                             String receiptNumber, String note) {
        MockLoan l = loans.get(loanId);
        if (l == null) throw new FineractException("Loan " + loanId + " not found");
        if (l.externalIds.containsKey(externalId)) {
            throw new FineractException("error.msg.loan.transaction.duplicate.external.id");
        }
        long id = txSeq.incrementAndGet();
        BigDecimal remaining = amount;
        BigDecimal toInterest = remaining.min(l.interest);
        l.interest = l.interest.subtract(toInterest);
        remaining = remaining.subtract(toInterest);
        l.principal = l.principal.subtract(remaining.min(l.principal)).max(BigDecimal.ZERO);
        l.overdue = l.overdue.subtract(amount).max(BigDecimal.ZERO);
        if (l.overdue.signum() == 0 && l.status == ELoanStatus.IN_ARREARS) l.status = ELoanStatus.ACTIVE;
        if (l.total().signum() == 0) {
            l.status = ELoanStatus.SETTLED;
            l.nextDue = null;
        }
        l.repayments.add(new RepaymentDto(id, date, amount, externalId));
        l.externalIds.put(externalId, String.valueOf(id));
        log.info("[MOCK FINERACT] repayment posted loanId={} amount={} txnId={} newBalance={}",
                loanId, amount, id, l.total());
        return String.valueOf(id);
    }

    private LoanSummaryDto toDto(MockLoan l) {
        return new LoanSummaryDto(l.id, l.clientId, l.accountNo, l.product, l.status, l.principal, l.total(),
                l.overdue, l.nextDue, l.installment.signum() > 0 ? l.installment : null);
    }
}
