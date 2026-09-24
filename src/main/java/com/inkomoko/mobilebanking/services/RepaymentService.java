package com.inkomoko.mobilebanking.services;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.enums.ELanguage;
import com.inkomoko.mobilebanking.enums.ETransactionStatus;
import com.inkomoko.mobilebanking.models.RepaymentTransaction;
import com.inkomoko.mobilebanking.models.UssdSession;
import com.inkomoko.mobilebanking.models.dto.PaymentInitResult;
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

    private enum CallbackOutcome { NOT_FOUND, ALREADY_PROCESSED, PAID, FAILED, REVIEW, STILL_PENDING }

    private final RepaymentTransactionRepository repo;
    private final PaymentGatewayService gateway;
    private final FineractService fineract;
    private final SmsService sms;
    private final AuditService audit;
    private final LanguagePreferenceService languages;
    private final InkomokoProperties props;
    private final TransactionTemplate tx;

    public RepaymentService(RepaymentTransactionRepository repo, PaymentGatewayService gateway,
                            FineractService fineract, SmsService sms, AuditService audit,
                            LanguagePreferenceService languages, InkomokoProperties props,
                            PlatformTransactionManager txManager) {
        this.repo = repo;
        this.gateway = gateway;
        this.fineract = fineract;
        this.sms = sms;
        this.audit = audit;
        this.languages = languages;
        this.props = props;
        this.tx = new TransactionTemplate(txManager);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  1. Initiation (from USSD confirm screen)
    // ══════════════════════════════════════════════════════════════════════

    public InitOutcome initiate(UssdSession s) {
        LocalDateTime now = DateUtility.nowKigali();
        boolean pendingExists = repo.existsByMsisdnAndStatusInAndCreatedAtAfter(s.getMsisdn(),
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
        t.setMsisdn(s.getMsisdn());
        t.setClientId(s.getClientId());
        t.setLoanId(s.getLoanId());
        t.setLoanAccountNo(s.getLoanAccountNo());
        t.setAmount(s.getAmount());
        t.setCurrency(props.getRepayment().getCurrency());
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
            case FAILED -> repo.findByReference(reference).ifPresent(this::sendFailureSms);
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
            try {
                String txnId = fineract.findRepaymentByExternalId(t.getLoanId(), t.getReference())
                        .orElseGet(() -> fineract.postRepayment(t.getLoanId(), t.getAmount(),
                                DateUtility.todayKigali(), t.getReference(),
                                t.getTelecomRef() != null ? t.getTelecomRef() : t.getReference(),
                                "USSD *311# MoMo repayment " + (t.getTelecom() == null ? "" : t.getTelecom())));
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
        if (posted) sendReceiptSms(result);
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
    //  SMS
    // ══════════════════════════════════════════════════════════════════════

    private void sendReceiptSms(RepaymentTransaction t) {
        ELanguage l = languages.preferredLanguage(t.getMsisdn());
        String amount = GeneralMethods.rwf(t.getAmount());
        String balance = t.getBalanceAfter() == null ? "-" : GeneralMethods.rwf(t.getBalanceAfter());
        sms.send(t.getMsisdn(), l.pick(
                "Inkomoko: Kwishyura " + amount + " ku ideni " + t.getLoanAccountNo() + " byagenze neza. Amafaranga asigaye: "
                        + balance + ". Ref: " + t.getReference(),
                "Inkomoko: Payment of " + amount + " to loan " + t.getLoanAccountNo() + " received. New balance: "
                        + balance + ". Ref: " + t.getReference(),
                "Inkomoko: Paiement de " + amount + " recu pour le pret " + t.getLoanAccountNo() + ". Nouveau solde: "
                        + balance + ". Ref: " + t.getReference()));
    }

    private void sendFailureSms(RepaymentTransaction t) {
        ELanguage l = languages.preferredLanguage(t.getMsisdn());
        String amount = GeneralMethods.rwf(t.getAmount());
        sms.send(t.getMsisdn(), l.pick(
                "Inkomoko: Kwishyura " + amount + " ntibyakunze. Nta mafaranga yakuwe kuri konti yawe. Ref: " + t.getReference(),
                "Inkomoko: Your payment of " + amount + " was not completed. You have not been charged. Ref: " + t.getReference(),
                "Inkomoko: Votre paiement de " + amount + " n'a pas abouti. Aucun montant debite. Ref: " + t.getReference()));
    }

    private static String trim(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    public BigDecimal minAmount() {
        return props.getRepayment().getMinAmount();
    }
}
