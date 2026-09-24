package com.inkomoko.mobilebanking.services;

import com.inkomoko.mobilebanking.config.DemoMode;
import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.enums.ELoanStatus;
import com.inkomoko.mobilebanking.models.api.demo.DemoClientRequest;
import com.inkomoko.mobilebanking.models.api.demo.DemoLoanRequest;
import com.inkomoko.mobilebanking.models.api.demo.DemoRepaymentRequest;
import com.inkomoko.mobilebanking.models.demo.DemoClient;
import com.inkomoko.mobilebanking.models.demo.DemoLoan;
import com.inkomoko.mobilebanking.models.demo.DemoRepayment;
import com.inkomoko.mobilebanking.repositories.DemoClientRepository;
import com.inkomoko.mobilebanking.repositories.DemoLoanRepository;
import com.inkomoko.mobilebanking.repositories.DemoRepaymentRepository;
import com.inkomoko.mobilebanking.services.integration.DemoFineractService;
import com.inkomoko.mobilebanking.utils.DateUtility;
import com.inkomoko.mobilebanking.utils.PhoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Management of demo core-banking data: allowed numbers (clients), loans and repayment history.
 * Demo mode only — this bean does not exist when inkomoko.fineract.mode=live.
 */
@Slf4j
@Service
@ConditionalOnExpression(DemoMode.ENABLED)
@RequiredArgsConstructor
public class DemoDataService {

    public record ClientView(DemoClient client, List<DemoLoan> loans) {
    }

    private final DemoClientRepository clientRepo;
    private final DemoLoanRepository loanRepo;
    private final DemoRepaymentRepository repaymentRepo;
    private final DemoFineractService demoFineract;
    private final InkomokoProperties props;

    /** Registers numbers from inkomoko.demo.allowed-msisdns (with sample loans) on startup. */
    @EventListener(ApplicationReadyEvent.class)
    public void seedAllowedNumbers() {
        for (String raw : props.getDemo().getAllowedMsisdns()) {
            String msisdn = PhoneUtils.purify(raw);
            if (!PhoneUtils.isValid(msisdn) || clientRepo.existsByMsisdn(msisdn)) continue;
            try {
                DemoClientRequest req = new DemoClientRequest();
                req.setMsisdn(msisdn);
                req.setSampleLoans(true);
                createClient(req);
                log.info("[DEMO] registered allowed number {} with sample loans", msisdn);
            } catch (Exception e) {
                log.warn("[DEMO] could not register {}: {}", msisdn, e.getMessage());
            }
        }
        log.warn("Core banking is in DEMO mode — {} demo client(s). Manage them at /api/admin/demo/clients",
                clientRepo.count());
    }

    // ── Clients (allowed numbers) ──────────────────────────────────────────

    public List<ClientView> listClients() {
        return clientRepo.findAllByOrderByIdAsc().stream().map(this::view).toList();
    }

    public ClientView getClient(String msisdn) {
        return view(requireClient(msisdn));
    }

    @Transactional
    public ClientView createClient(DemoClientRequest req) {
        String msisdn = PhoneUtils.purify(req.getMsisdn());
        if (!PhoneUtils.isValid(msisdn)) {
            throw new IllegalArgumentException("msisdn must be a Rwandan number, e.g. 250788123456");
        }
        if (clientRepo.existsByMsisdn(msisdn)) {
            throw new IllegalStateException("Demo client already exists for " + msisdn);
        }
        LocalDateTime now = DateUtility.nowKigali();
        DemoClient c = new DemoClient();
        c.setMsisdn(msisdn);
        c.setDisplayName(blank(req.getDisplayName()) ? "Demo Client " + msisdn.substring(msisdn.length() - 4)
                : req.getDisplayName().trim());
        c.setActive(req.getActive() == null || req.getActive());
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        c = clientRepo.save(c);
        if (req.getSampleLoans() == null || req.getSampleLoans()) addSampleLoans(c);
        return view(c);
    }

    @Transactional
    public ClientView updateClient(String msisdn, DemoClientRequest req) {
        DemoClient c = requireClient(msisdn);
        if (!blank(req.getDisplayName())) c.setDisplayName(req.getDisplayName().trim());
        if (req.getActive() != null) c.setActive(req.getActive());
        c.setUpdatedAt(DateUtility.nowKigali());
        return view(clientRepo.save(c));
    }

    /** Removes the client with its loans and repayments (cascade). */
    @Transactional
    public void deleteClient(String msisdn) {
        clientRepo.delete(requireClient(msisdn));
    }

    @Transactional
    public ClientView addSampleLoans(String msisdn) {
        DemoClient c = requireClient(msisdn);
        addSampleLoans(c);
        return view(c);
    }

    /** Standard demo portfolio: one active loan and one loan in arrears, each with repayment history. */
    private void addSampleLoans(DemoClient c) {
        LocalDate today = DateUtility.todayKigali();
        DemoLoan active = newLoan(c.getId(), "SME Growth Loan", ELoanStatus.ACTIVE,
                "250000", "15000", "15000", "15000", today.plusDays(11));
        history(active.getId(), today, 5, "15000");
        DemoLoan arrears = newLoan(c.getId(), "Asset Loan", ELoanStatus.IN_ARREARS,
                "120000", "8000", "32000", "16000", today.minusDays(4));
        history(arrears.getId(), today.minusMonths(1), 2, "16000");
    }

    // ── Loans ─────────────────────────────────────────────────────────────

    @Transactional
    public DemoLoan addLoan(String msisdn, DemoLoanRequest req) {
        DemoClient c = requireClient(msisdn);
        ELoanStatus status = req.getStatus() == null ? ELoanStatus.ACTIVE : req.getStatus();
        DemoLoan l = new DemoLoan();
        l.setClientId(c.getId());
        l.setProductName(blank(req.getProductName()) ? "Demo Loan" : req.getProductName().trim());
        l.setStatus(status);
        l.setPrincipalOutstanding(nz(req.getPrincipalOutstanding()));
        l.setInterestOutstanding(nz(req.getInterestOutstanding()));
        l.setTotalOverdue(nz(req.getTotalOverdue()));
        l.setInstallment(nz(req.getInstallment()));
        l.setNextDueDate(req.getNextDueDate() != null ? req.getNextDueDate()
                : status.isRepayable() ? DateUtility.todayKigali().plusDays(30) : null);
        return saveNewLoan(l, req.getAccountNo());
    }

    @Transactional
    public DemoLoan updateLoan(Long loanId, DemoLoanRequest req) {
        DemoLoan l = requireLoan(loanId);
        if (!blank(req.getAccountNo()) && !req.getAccountNo().trim().equals(l.getAccountNo())) {
            if (loanRepo.existsByAccountNo(req.getAccountNo().trim())) {
                throw new IllegalStateException("Account number already used: " + req.getAccountNo());
            }
            l.setAccountNo(req.getAccountNo().trim());
        }
        if (!blank(req.getProductName())) l.setProductName(req.getProductName().trim());
        if (req.getStatus() != null) l.setStatus(req.getStatus());
        if (req.getPrincipalOutstanding() != null) l.setPrincipalOutstanding(req.getPrincipalOutstanding());
        if (req.getInterestOutstanding() != null) l.setInterestOutstanding(req.getInterestOutstanding());
        if (req.getTotalOverdue() != null) l.setTotalOverdue(req.getTotalOverdue());
        if (req.getInstallment() != null) l.setInstallment(req.getInstallment());
        if (req.getNextDueDate() != null) l.setNextDueDate(req.getNextDueDate());
        l.setUpdatedAt(DateUtility.nowKigali());
        return loanRepo.save(l);
    }

    @Transactional
    public void deleteLoan(Long loanId) {
        loanRepo.delete(requireLoan(loanId));
    }

    // ── Repayment history ─────────────────────────────────────────────────

    public List<DemoRepayment> repayments(Long loanId) {
        requireLoan(loanId);
        return repaymentRepo.findByLoanIdOrderByTxnDateDescIdDesc(loanId, PageRequest.of(0, 500));
    }

    @Transactional
    public DemoRepayment addRepayment(Long loanId, DemoRepaymentRequest req) {
        String ref = blank(req.getReference()) ? "DEMO" + System.currentTimeMillis() : req.getReference().trim();
        LocalDate date = req.getDate() == null ? DateUtility.todayKigali() : req.getDate();
        if (Boolean.TRUE.equals(req.getApplyToBalance())) {
            String id = demoFineract.postRepayment(loanId, req.getAmount(), date, ref, ref, "Demo admin repayment");
            return repaymentRepo.findById(Long.valueOf(id)).orElseThrow();
        }
        requireLoan(loanId);
        if (repaymentRepo.existsByLoanIdAndReference(loanId, ref)) {
            throw new IllegalStateException("Reference already used on this loan: " + ref);
        }
        DemoRepayment r = new DemoRepayment();
        r.setLoanId(loanId);
        r.setAmount(req.getAmount());
        r.setTxnDate(date);
        r.setReference(ref);
        r.setCreatedAt(DateUtility.nowKigali());
        return repaymentRepo.save(r);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private DemoLoan newLoan(Long clientId, String product, ELoanStatus status, String principal, String interest,
                             String overdue, String installment, LocalDate due) {
        DemoLoan l = new DemoLoan();
        l.setClientId(clientId);
        l.setProductName(product);
        l.setStatus(status);
        l.setPrincipalOutstanding(new BigDecimal(principal));
        l.setInterestOutstanding(new BigDecimal(interest));
        l.setTotalOverdue(new BigDecimal(overdue));
        l.setInstallment(new BigDecimal(installment));
        l.setNextDueDate(due);
        return saveNewLoan(l, null);
    }

    /** Saves the loan; blank account numbers become "9" + 8-digit id (e.g. 900000012). */
    private DemoLoan saveNewLoan(DemoLoan l, String accountNo) {
        LocalDateTime now = DateUtility.nowKigali();
        l.setCreatedAt(now);
        l.setUpdatedAt(now);
        boolean generate = blank(accountNo);
        if (!generate && loanRepo.existsByAccountNo(accountNo.trim())) {
            throw new IllegalStateException("Account number already used: " + accountNo);
        }
        l.setAccountNo(generate ? "PENDING-" + System.nanoTime() : accountNo.trim());
        l = loanRepo.saveAndFlush(l);
        if (generate) {
            l.setAccountNo(String.format("9%08d", l.getId()));
            l = loanRepo.save(l);
        }
        return l;
    }

    private void history(Long loanId, LocalDate lastDate, int count, String amount) {
        for (int i = count - 1; i >= 0; i--) {
            DemoRepayment r = new DemoRepayment();
            r.setLoanId(loanId);
            r.setTxnDate(lastDate.minusMonths(i));
            r.setAmount(new BigDecimal(amount));
            r.setReference("DEMO" + loanId + "-" + i);
            r.setCreatedAt(DateUtility.nowKigali());
            repaymentRepo.save(r);
        }
    }

    private DemoClient requireClient(String msisdn) {
        String m = PhoneUtils.purify(msisdn);
        return clientRepo.findByMsisdn(m)
                .orElseThrow(() -> new IllegalArgumentException("No demo client for " + m));
    }

    private DemoLoan requireLoan(Long loanId) {
        return loanRepo.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Demo loan not found: " + loanId));
    }

    private ClientView view(DemoClient c) {
        return new ClientView(c, loanRepo.findByClientIdOrderByIdAsc(c.getId()));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
