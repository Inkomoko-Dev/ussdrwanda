package com.inkomoko.mobilebanking.services.integration;

import com.inkomoko.mobilebanking.config.DemoMode;
import com.inkomoko.mobilebanking.enums.ELoanStatus;
import com.inkomoko.mobilebanking.models.demo.DemoClient;
import com.inkomoko.mobilebanking.models.demo.DemoLoan;
import com.inkomoko.mobilebanking.models.demo.DemoRepayment;
import com.inkomoko.mobilebanking.models.dto.ClientDto;
import com.inkomoko.mobilebanking.models.dto.LoanSummaryDto;
import com.inkomoko.mobilebanking.models.dto.RepaymentDto;
import com.inkomoko.mobilebanking.repositories.DemoClientRepository;
import com.inkomoko.mobilebanking.repositories.DemoLoanRepository;
import com.inkomoko.mobilebanking.repositories.DemoRepaymentRepository;
import com.inkomoko.mobilebanking.utils.DateUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Demo core banking backed by the demo_* tables (pilot on *662*105#).
 *
 * Implements the same {@link FineractService} contract as {@link FineractRestService}, so the
 * USSD menus, PIN handling, repayment/reconciliation and reporting are identical in both modes —
 * switching to live only swaps this bean. Data is managed through /api/admin/demo/**.
 */
@Slf4j
@Service
@ConditionalOnExpression(DemoMode.ENABLED)
@RequiredArgsConstructor
public class DemoFineractService implements FineractService {

    private final DemoClientRepository clientRepo;
    private final DemoLoanRepository loanRepo;
    private final DemoRepaymentRepository repaymentRepo;

    @Override
    public Optional<ClientDto> findClientByMsisdn(String msisdn) {
        Optional<DemoClient> c = clientRepo.findByMsisdn(msisdn);
        if (c.isEmpty()) {
            log.info("[DEMO CORE BANKING] {} is not an allowed demo number (add it via POST /api/admin/demo/clients)", msisdn);
        }
        return c.map(DemoFineractService::toClient);
    }

    @Override
    public Optional<ClientDto> findClientById(Long clientId) {
        return clientId == null ? Optional.empty() : clientRepo.findById(clientId).map(DemoFineractService::toClient);
    }

    @Override
    @Transactional
    public void updateClient(Long clientId, String displayName, String mobileNo, String email) {
        DemoClient c = clientRepo.findById(clientId).orElseThrow(() -> new FineractException("Client " + clientId + " not found"));
        clientRepo.findByMsisdn(mobileNo).filter(other -> !other.getId().equals(c.getId())).ifPresent(other -> {
            throw new FineractException("Core banking already has a client with phone " + mobileNo);
        });
        c.setDisplayName(displayName);
        c.setMsisdn(mobileNo);
        c.setEmail(email);
        c.setUpdatedAt(DateUtility.nowKigali());
        clientRepo.save(c);
    }

    private static ClientDto toClient(DemoClient x) {
        return new ClientDto(x.getId(), x.getDisplayName(), x.getMsisdn(), x.getEmail(), x.isActive());
    }

    @Override
    public List<LoanSummaryDto> getClientLoans(Long clientId) {
        return loanRepo.findByClientIdOrderByIdAsc(clientId).stream().map(DemoFineractService::toDto).toList();
    }

    @Override
    public LoanSummaryDto getLoanDetails(Long loanId) {
        return loanRepo.findById(loanId).map(DemoFineractService::toDto)
                .orElseThrow(() -> new FineractException("Loan " + loanId + " not found"));
    }

    @Override
    public List<RepaymentDto> getRepayments(Long loanId, int limit) {
        return repaymentRepo.findByLoanIdOrderByTxnDateDescIdDesc(loanId, PageRequest.of(0, limit > 0 ? limit : 1000))
                .stream()
                .map(r -> new RepaymentDto(r.getId(), r.getTxnDate(), r.getAmount(), r.getReference()))
                .toList();
    }

    @Override
    public Optional<String> findRepaymentByExternalId(Long loanId, String externalId) {
        return repaymentRepo.findByLoanIdAndReference(loanId, externalId).map(r -> String.valueOf(r.getId()));
    }

    @Override
    @Transactional
    public String postRepayment(Long loanId, BigDecimal amount, LocalDate date, String externalId,
                                String receiptNumber, String note) {
        DemoLoan loan = loanRepo.lockById(loanId).orElseThrow(() -> new FineractException("Loan " + loanId + " not found"));
        if (repaymentRepo.existsByLoanIdAndReference(loanId, externalId)) {
            // same behaviour as Fineract's unique externalId
            throw new FineractException("error.msg.loan.transaction.duplicate.external.id");
        }
        applyRepayment(loan, amount);
        loanRepo.save(loan);
        DemoRepayment r = saveRepayment(loanId, amount, date, externalId);
        log.info("[DEMO CORE BANKING] repayment posted loanId={} amount={} txnId={} newBalance={}",
                loanId, amount, r.getId(), loan.getTotalOutstanding());
        return String.valueOf(r.getId());
    }

    @Override
    @Transactional
    public void reverseRepayment(Long loanId, String fineractTxnId) {
        long id;
        try {
            id = Long.parseLong(fineractTxnId);
        } catch (NumberFormatException e) {
            throw new FineractException("Unknown core-banking transaction " + fineractTxnId);
        }
        DemoRepayment repayment = repaymentRepo.findById(id)
                .orElseThrow(() -> new FineractException("Core-banking transaction " + fineractTxnId + " not found"));
        DemoLoan loan = loanRepo.lockById(loanId).orElseThrow(() -> new FineractException("Loan " + loanId + " not found"));
        loan.setPrincipalOutstanding(nz(loan.getPrincipalOutstanding()).add(nz(repayment.getAmount())));
        if (loan.getStatus() == ELoanStatus.SETTLED) loan.setStatus(ELoanStatus.ACTIVE);
        loan.setUpdatedAt(DateUtility.nowKigali());
        loanRepo.save(loan);
        repaymentRepo.delete(repayment);
    }

    DemoRepayment saveRepayment(Long loanId, BigDecimal amount, LocalDate date, String reference) {
        DemoRepayment r = new DemoRepayment();
        r.setLoanId(loanId);
        r.setAmount(amount);
        r.setTxnDate(date);
        r.setReference(reference);
        r.setCreatedAt(DateUtility.nowKigali());
        return repaymentRepo.save(r);
    }

    /** Applies a repayment like Fineract's default strategy: overdue first, interest before principal. */
    static void applyRepayment(DemoLoan l, BigDecimal amount) {
        BigDecimal interest = nz(l.getInterestOutstanding());
        BigDecimal principal = nz(l.getPrincipalOutstanding());
        BigDecimal toInterest = amount.min(interest);
        BigDecimal toPrincipal = amount.subtract(toInterest).min(principal);
        l.setInterestOutstanding(interest.subtract(toInterest));
        l.setPrincipalOutstanding(principal.subtract(toPrincipal));
        l.setTotalOverdue(nz(l.getTotalOverdue()).subtract(amount).max(BigDecimal.ZERO));
        if (l.getStatus() == ELoanStatus.IN_ARREARS && l.getTotalOverdue().signum() == 0) {
            l.setStatus(ELoanStatus.ACTIVE);
        }
        if (l.getTotalOutstanding().signum() == 0 && l.getStatus().isRepayable()) {
            l.setStatus(ELoanStatus.SETTLED);
            l.setNextDueDate(null);
            l.setInstallment(BigDecimal.ZERO);
        }
        l.setUpdatedAt(DateUtility.nowKigali());
    }

    static LoanSummaryDto toDto(DemoLoan l) {
        BigDecimal inst = nz(l.getInstallment());
        return new LoanSummaryDto(l.getId(), l.getClientId(), l.getAccountNo(), l.getProductName(), l.getStatus(),
                nz(l.getPrincipalOutstanding()), l.getTotalOutstanding(), nz(l.getTotalOverdue()),
                l.getNextDueDate(), inst.signum() > 0 ? inst : null);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
