package com.inkomoko.mobilebanking.services;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.enums.ETransactionStatus;
import com.inkomoko.mobilebanking.models.RepaymentTransaction;
import com.inkomoko.mobilebanking.models.UssdSession;
import com.inkomoko.mobilebanking.models.dto.PaymentInitResult;
import com.inkomoko.mobilebanking.models.dto.PaymentReversalResult;
import com.inkomoko.mobilebanking.models.dto.PaymentStatusResult;
import com.inkomoko.mobilebanking.repositories.RepaymentTransactionRepository;
import com.inkomoko.mobilebanking.services.integration.FineractService;
import com.inkomoko.mobilebanking.services.integration.PaymentGatewayService;
import com.inkomoko.mobilebanking.services.integration.SmsService;
import com.inkomoko.mobilebanking.utils.DateUtility;
import com.inkomoko.mobilebanking.utils.GeneralMethods;
import com.inkomoko.mobilebanking.utils.Utility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Mobile Money repayment and reconciliation (proposal §3.5):
 *
 *  1. {@link #initiate}         — client confirmed on USSD → INITIATED → push sent → PENDING
 *  2. {@link #handleGatewayResult} — gateway callback / status query → PAID or FAILED
 *  3. {@link #postToFineract}   — PAID → posted to the client's loan in Fineract → POSTED + SMS receipt
 *  4. {@link #reconcile}        — scheduled: retries failed postings, queries stale pending payments,
 *                                 escalates anything unresolved to NEEDS_REVIEW (maker/checker).
 *
 * Duplicate protection: row locks on every state change, idempotent callbacks (final states are
 * never reprocessed), and the transaction reference used as Fineract externalId (checked before
 * posting and enforced unique by Fineract).
 */
@Slf4j
@Service
public class RepaymentService {

    public enum InitStatus { ACCEPTED, DUPLICATE_PENDING, GATEWAY_REJECTED, ERROR }

    public record InitOutcome(InitStatus status, RepaymentTransaction tx) {
    }

    /** What the telecom reported, and the repayment status after this check. */
    public record TelecomCheck(String telecomStatus, String transactionStatus, String message) {
    }

    private enum CallbackOutcome { NOT_FOUND, ALREADY_PROCESSED, PAID, FAILED, REVIEW, STILL_PENDING }

    private final RepaymentTransactionRepository repo;
    private final PaymentGatewayService gateway;
    private final FineractService fineract;
    private final SmsService sms;
    private final AuditService audit;
    private final LanguagePreferenceService languages;
    private final InkomokoProperties props;
    private final TransactionTemplate tx;
    private final CountryService countries;
    private final MessageService messages;

    public RepaymentService(RepaymentTransactionRepository repo, PaymentGatewayService gateway,
                            FineractService fineract, SmsService sms, AuditService audit,
                            LanguagePreferenceService languages, InkomokoProperties props,
                            PlatformTransactionManager txManager, CountryService countries,
                            MessageService messages) {
        this.repo = repo;
        this.gateway = gateway;
        this.fineract = fineract;
        this.sms = sms;
        this.audit = audit;
        this.languages = languages;
        this.props = props;
        this.tx = new TransactionTemplate(txManager);
        this.countries = countries;
        this.messages = messages;
    }

    /** Omnichannel: the Payment Hub posts the repayment to the loan and texts the customer. */
    private boolean hubPosts() {
        String mode = props.getPayment().getMode();
        return mode != null && "omnichannel".equalsIgnoreCase(mode.trim());
    }

    // ══════════════════════════════════════════════════════════════════════
    //  1. Initiation (from USSD confirm screen)
    // ══════════════════════════════════════════════════════════════════════

    public InitOutcome initiate(UssdSession s) {
        LocalDateTime now = DateUtility.nowKigali();
        String payer = s.getPayerMsisdn() == null || s.getPayerMsisdn().isBlank() ? s.getMsisdn() : s.getPayerMsisdn();
        boolean pendingExists = repo.existsByMsisdnAndStatusInAndCreatedAtAfter(payer,
                List.of(ETransactionStatus.INITIATED, ETransactionStatus.PENDING),
                now.minusMinutes(props.getRepayment().getDuplicateWindowMinutes()));
        if (pendingExists) {
            audit.ussd(s.getMsisdn(), s.getSessionId(), "REPAYMENT_BLOCKED_DUPLICATE", String.valueOf(s.getLoanId()),
                    false, "Another repayment is still pending");
            return new InitOutcome(InitStatus.DUPLICATE_PENDING, null);
        }

        RepaymentTransaction t = new RepaymentTransaction();
        t.setReference(Utility.generateRepaymentReference());
        t.setSessionId(s.getSessionId());
        t.setMsisdn(payer);
        t.setClientId(s.getClientId());
        t.setLoanId(s.getLoanId());
        t.setLoanAccountNo(s.getLoanAccountNo());
        t.setAmount(s.getAmount());
        t.setCurrency(countries.currency());
        t.setTelecom(s.getTelecom());
        t.setStatus(ETransactionStatus.INITIATED);
        t.setConfirmedAt(now);
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        t = repo.save(t);
        final String ref = t.getReference();

        PaymentInitResult result;
        try {
            result = gateway.requestCollection(t);
        } catch (Exception e) {
            log.error("Gateway push error ref={} error={}", ref, e.getMessage(), e);
            result = new PaymentInitResult(false, null, e.getMessage());
        }
        final PaymentInitResult r = result;

        // Only move INITIATED → PENDING/FAILED; a fast callback may already have advanced it.
        RepaymentTransaction updated = tx.execute(st -> {
            RepaymentTransaction locked = repo.lockByReference(ref).orElseThrow();
            if (locked.getStatus() == ETransactionStatus.INITIATED) {
                locked.setGatewayRef(r.gatewayRef());
                locked.setStatus(r.accepted() ? ETransactionStatus.PENDING : ETransactionStatus.FAILED);
                if (!r.accepted()) locked.setFailureReason(trim(r.message()));
                locked.setUpdatedAt(DateUtility.nowKigali());
            }
            return repo.save(locked);
        });

        audit.ussd(s.getMsisdn(), s.getSessionId(), "REPAYMENT_INITIATED", ref, r.accepted(),
                "loanId=" + s.getLoanId() + " amount=" + s.getAmount() + " telecom=" + s.getTelecom());
        return new InitOutcome(r.accepted() ? InitStatus.ACCEPTED : InitStatus.GATEWAY_REJECTED, updated);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  2. Gateway result (callback or status query)
    // ══════════════════════════════════════════════════════════════════════

    /** Idempotent: repeated or late callbacks for a finished payment are acknowledged and ignored. */
    public String handleGatewayResult(String reference, PaymentStatusResult result) {
        CallbackOutcome outcome = tx.execute(st -> {
            RepaymentTransaction t = repo.lockByReference(reference).orElse(null);
            if (t == null) return CallbackOutcome.NOT_FOUND;
            boolean awaiting = t.getStatus() == ETransactionStatus.INITIATED
                    || t.getStatus() == ETransactionStatus.PENDING
                    || (t.getStatus() == ETransactionStatus.NEEDS_REVIEW && t.getPaidAt() == null);
            if (!awaiting) return CallbackOutcome.ALREADY_PROCESSED;

            LocalDateTime now = DateUtility.nowKigali();
            if (result.gatewayRef() != null) t.setGatewayRef(result.gatewayRef());
            if (result.telecomRef() != null) t.setTelecomRef(result.telecomRef());
            t.setUpdatedAt(now);

            CallbackOutcome o;
            if (result.isSuccess()) {
                if (result.amount() != null && result.amount().compareTo(t.getAmount()) != 0) {
                    t.setStatus(ETransactionStatus.NEEDS_REVIEW);
                    t.setPaidAt(now);
                    t.setFailureReason("Amount mismatch: gateway reported " + result.amount());
                    o = CallbackOutcome.REVIEW;
                } else {
                    t.setStatus(ETransactionStatus.PAID);
                    t.setPaidAt(now);
                    o = CallbackOutcome.PAID;
                }
            } else if (result.isFailed()) {
                t.setStatus(ETransactionStatus.FAILED);
                t.setFailureReason(trim(result.message()));
                o = CallbackOutcome.FAILED;
            } else {
                o = CallbackOutcome.STILL_PENDING;
            }
            repo.save(t);
            return o;
        });

        audit.system("PAYMENT_RESULT_" + outcome, reference,
                outcome == CallbackOutcome.PAID || outcome == CallbackOutcome.ALREADY_PROCESSED,
                "gatewayStatus=" + result.status());

        switch (outcome) {
            case PAID -> postToFineract(reference);
            case FAILED -> {
                if (!hubPosts()) repo.findByReference(reference).ifPresent(this::sendFailureSms);
            }
            default -> { }
        }
        return outcome.name();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  3. Posting to Fineract
    // ══════════════════════════════════════════════════════════════════════

    /** Posts a PAID / POSTING_FAILED repayment to Fineract. Returns true when POSTED. */
    public boolean postToFineract(String reference) {
        RepaymentTransaction result = tx.execute(st -> {
            RepaymentTransaction t = repo.lockByReference(reference).orElse(null);
            if (t == null) return null;
            if (t.getStatus() != ETransactionStatus.PAID && t.getStatus() != ETransactionStatus.POSTING_FAILED) {
                return null;
            }
            t.setPostingAttempts(t.getPostingAttempts() + 1);
            LocalDateTime now = DateUtility.nowKigali();
            if (hubPosts()) {
                t.setStatus(ETransactionStatus.POSTED);
                t.setPostedAt(now);
                t.setFailureReason(null);
                try {
                    t.setBalanceAfter(fineract.getLoanDetails(t.getLoanId()).totalOutstanding());
                } catch (Exception e) {
                    log.warn("Balance refresh after payment failed ref={}: {}", reference, e.getMessage());
                }
                t.setUpdatedAt(now);
                return repo.save(t);
            }
            try {
                String txnId = fineract.findRepaymentByExternalId(t.getLoanId(), t.getReference())
                        .orElseGet(() -> fineract.postRepayment(t.getLoanId(), t.getAmount(),
                                DateUtility.todayKigali(), t.getReference(),
                                t.getTelecomRef() != null ? t.getTelecomRef() : t.getReference(),
                                "USSD MoMo repayment " + (t.getTelecom() == null ? "" : t.getTelecom())));
                t.setFineractTxnId(txnId);
                t.setStatus(ETransactionStatus.POSTED);
                t.setPostedAt(now);
                t.setFailureReason(null);
                try {
                    t.setBalanceAfter(fineract.getLoanDetails(t.getLoanId()).totalOutstanding());
                } catch (Exception e) {
                    log.warn("Balance refresh after posting failed ref={}: {}", reference, e.getMessage());
                }
            } catch (Exception e) {
                log.error("Fineract posting failed ref={} attempt={} error={}", reference, t.getPostingAttempts(),
                        e.getMessage());
                boolean exhausted = t.getPostingAttempts() >= props.getRepayment().getMaxPostingAttempts();
                t.setStatus(exhausted ? ETransactionStatus.NEEDS_REVIEW : ETransactionStatus.POSTING_FAILED);
                t.setFailureReason(trim("Fineract: " + e.getMessage()));
            }
            t.setUpdatedAt(now);
            return repo.save(t);
        });

        if (result == null) return false;
        boolean posted = result.getStatus() == ETransactionStatus.POSTED;
        audit.system(posted ? "REPAYMENT_POSTED" : "REPAYMENT_POSTING_FAILED", reference, posted,
                posted ? "fineractTxnId=" + result.getFineractTxnId() : result.getFailureReason());
        if (posted && !hubPosts()) sendReceiptSms(result);
        return posted;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  4. Scheduled reconciliation
    // ══════════════════════════════════════════════════════════════════════

    @Scheduled(fixedDelayString = "${inkomoko.repayment.reconciliation-interval-ms:60000}",
               initialDelayString = "${inkomoko.repayment.reconciliation-interval-ms:60000}")
    public void reconcile() {
        LocalDateTime now = DateUtility.nowKigali();
        InkomokoProperties.Repayment cfg = props.getRepayment();

        for (RepaymentTransaction t : repo.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                ETransactionStatus.POSTING_FAILED, now.minusMinutes(1))) {
            postToFineract(t.getReference());
        }
        // PAID rows left behind (e.g. crash between callback and posting)
        for (RepaymentTransaction t : repo.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                ETransactionStatus.PAID, now.minusMinutes(2))) {
            postToFineract(t.getReference());
        }

        LocalDateTime checkBefore = now.minusMinutes(cfg.getPendingCheckAfterMinutes());
        List<RepaymentTransaction> stale = new java.util.ArrayList<>();
        stale.addAll(repo.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(ETransactionStatus.PENDING, checkBefore));
        stale.addAll(repo.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(ETransactionStatus.INITIATED, checkBefore));
        for (RepaymentTransaction t : stale) {
            PaymentStatusResult status;
            try {
                status = gateway.checkStatus(t);
            } catch (Exception e) {
                status = new PaymentStatusResult("UNKNOWN", null, null, null, e.getMessage());
            }
            if (status.isSuccess() || status.isFailed()) {
                handleGatewayResult(t.getReference(), status);
            } else if (t.getCreatedAt().isBefore(now.minusMinutes(cfg.getPendingExpiryMinutes()))) {
                escalate(t.getReference(), "No payment confirmation after " + cfg.getPendingExpiryMinutes() + " min");
            }
        }
    }

    private void escalate(String reference, String reason) {
        tx.executeWithoutResult(st -> repo.lockByReference(reference).ifPresent(t -> {
            if (t.getStatus() == ETransactionStatus.PENDING || t.getStatus() == ETransactionStatus.INITIATED) {
                t.setStatus(ETransactionStatus.NEEDS_REVIEW);
                t.setFailureReason(reason);
                t.setUpdatedAt(DateUtility.nowKigali());
                repo.save(t);
            }
        }));
        audit.system("REPAYMENT_ESCALATED", reference, true, reason);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Staff actions
    // ══════════════════════════════════════════════════════════════════════

    /** Asks the telecom for the latest result. A newly paid collection is posted to the loan. */
    public TelecomCheck checkTelecom(String reference, String by) {
        RepaymentTransaction current = require(reference);
        PaymentStatusResult status;
        try {
            status = gateway.checkStatus(current);
        } catch (Exception e) {
            throw new IllegalStateException("Telecom status check failed: " + e.getMessage());
        }
        boolean open = current.getStatus() == ETransactionStatus.INITIATED
                || current.getStatus() == ETransactionStatus.PENDING
                || (current.getStatus() == ETransactionStatus.NEEDS_REVIEW && current.getPaidAt() == null);
        if (open && (status.isSuccess() || status.isFailed())) {
            handleGatewayResult(reference, status);
        }
        RepaymentTransaction after = require(reference);
        audit.admin(by, "TELECOM_STATUS_CHECKED", reference, true,
                "telecom=" + status.status() + " repayment=" + after.getStatus());
        String message = status.message() == null ? "Telecom status: " + status.status() : status.message();
        return new TelecomCheck(status.status(), after.getStatus().name(), message);
    }

    /** Posts a collected repayment onto the loan. Safe to repeat: the reference is the external id. */
    public RepaymentTransaction postCollected(String reference, String by) {
        RepaymentTransaction t = require(reference);
        if (t.getStatus() == ETransactionStatus.POSTED) return t;
        if (t.getPaidAt() == null && t.getStatus() != ETransactionStatus.PAID
                && t.getStatus() != ETransactionStatus.POSTING_FAILED) {
            throw new IllegalStateException("The telecom has not confirmed this collection, so it cannot be posted");
        }
        if (t.getStatus() == ETransactionStatus.NEEDS_REVIEW) {
            tx.executeWithoutResult(st -> repo.lockByReference(reference).ifPresent(row -> {
                if (row.getStatus() == ETransactionStatus.NEEDS_REVIEW) {
                    row.setStatus(ETransactionStatus.POSTING_FAILED);
                    row.setUpdatedAt(DateUtility.nowKigali());
                    repo.save(row);
                }
            }));
        }
        boolean posted = postToFineract(reference);
        RepaymentTransaction after = require(reference);
        if (!posted) {
            throw new IllegalStateException(after.getFailureReason() == null
                    ? "Core banking did not accept the posting" : after.getFailureReason());
        }
        audit.admin(by, "REPAYMENT_POSTED_MANUAL", reference, true, "fineractTxnId=" + after.getFineractTxnId());
        return after;
    }

    /**
     * Undoes a posted repayment: reverses the loan entry, then asks the telecom to refund the client.
     * If the refund is refused, the row stays in review with the loan already reversed.
     */
    public RepaymentTransaction reversePosted(String reference, String by) {
        if (hubPosts()) {
            throw new IllegalStateException("Reversals of Omnichannel payments are handled by Inkomoko in Omnichannel");
        }
        RepaymentTransaction t = require(reference);
        if (t.getStatus() != ETransactionStatus.POSTED) {
            throw new IllegalStateException("Only a posted repayment can be reversed");
        }
        if (t.getFineractTxnId() == null || t.getFineractTxnId().isBlank()) {
            throw new IllegalStateException("This repayment has no core-banking transaction to reverse");
        }
        fineract.reverseRepayment(t.getLoanId(), t.getFineractTxnId());
        PaymentReversalResult refund = gateway.requestReversal(t);
        RepaymentTransaction saved = tx.execute(st -> {
            RepaymentTransaction row = repo.lockByReference(reference).orElseThrow();
            LocalDateTime now = DateUtility.nowKigali();
            if (refund.accepted()) {
                row.setStatus(ETransactionStatus.REVERSED);
                row.setFailureReason("Reversed: loan undone and client refunded");
            } else {
                row.setStatus(ETransactionStatus.NEEDS_REVIEW);
                row.setFailureReason(trim("Loan reversed, telecom refund failed: " + refund.message()));
            }
            row.setUpdatedAt(now);
            return repo.save(row);
        });
        audit.admin(by, "REPAYMENT_REVERSED", reference, refund.accepted(), saved.getFailureReason());
        return saved;
    }

    private RepaymentTransaction require(String reference) {
        return repo.findByReference(reference)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + reference));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SMS
    // ══════════════════════════════════════════════════════════════════════

    private void sendReceiptSms(RepaymentTransaction t) {
        String l = languages.preferredLanguage(t.getMsisdn());
        String amount = GeneralMethods.rwf(t.getAmount());
        String balance = t.getBalanceAfter() == null ? "-" : GeneralMethods.rwf(t.getBalanceAfter());
        sms.send(t.getMsisdn(), messages.text(l, "sms.repay.success", java.util.Map.of("amount", amount,
                "account", String.valueOf(t.getLoanAccountNo()), "balance", balance, "ref", t.getReference())));
    }

    private void sendFailureSms(RepaymentTransaction t) {
        String l = languages.preferredLanguage(t.getMsisdn());
        String amount = GeneralMethods.rwf(t.getAmount());
        sms.send(t.getMsisdn(), messages.text(l, "sms.repay.failed",
                java.util.Map.of("amount", amount, "ref", t.getReference())));
    }

    private static String trim(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    public BigDecimal minAmount() {
        return countries.minRepayment();
    }
}
