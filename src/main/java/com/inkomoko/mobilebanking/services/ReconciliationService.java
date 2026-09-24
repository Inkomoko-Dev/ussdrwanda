package com.inkomoko.mobilebanking.services;

import com.inkomoko.mobilebanking.enums.EApprovalStatus;
import com.inkomoko.mobilebanking.enums.EReconciliationAction;
import com.inkomoko.mobilebanking.enums.ETransactionStatus;
import com.inkomoko.mobilebanking.models.ReconciliationAction;
import com.inkomoko.mobilebanking.models.RepaymentTransaction;
import com.inkomoko.mobilebanking.models.api.ReconciliationProposalRequest;
import com.inkomoko.mobilebanking.repositories.ReconciliationActionRepository;
import com.inkomoko.mobilebanking.repositories.RepaymentTransactionRepository;
import com.inkomoko.mobilebanking.utils.DateUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

/**
 * Maker/checker reconciliation of repayments that automatic processing could not resolve
 * (proposal §3.5, ToR 3.1(viii)). A proposal only takes effect once a different operator approves it.
 */
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private static final Set<ETransactionStatus> RECONCILABLE = EnumSet.of(
            ETransactionStatus.NEEDS_REVIEW, ETransactionStatus.POSTING_FAILED,
            ETransactionStatus.PENDING, ETransactionStatus.INITIATED, ETransactionStatus.PAID);

    private final ReconciliationActionRepository actionRepo;
    private final RepaymentTransactionRepository txRepo;
    private final RepaymentService repaymentService;
    private final AuditService audit;

    public ReconciliationAction propose(String reference, ReconciliationProposalRequest req, String maker) {
        RepaymentTransaction t = txRepo.findByReference(reference)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + reference));
        if (!RECONCILABLE.contains(t.getStatus())) {
            throw new IllegalStateException("Transaction is " + t.getStatus() + " and cannot be reconciled");
        }
        if (actionRepo.existsByTransactionReferenceAndStatus(reference, EApprovalStatus.PENDING_APPROVAL)) {
            throw new IllegalStateException("A reconciliation action is already awaiting approval");
        }
        if (req.getAction() == EReconciliationAction.MARK_POSTED
                && (req.getFineractTxnId() == null || req.getFineractTxnId().isBlank())) {
            throw new IllegalArgumentException("fineractTxnId is required for MARK_POSTED");
        }
        if (req.getAction() == EReconciliationAction.REPOST && t.getPaidAt() == null) {
            throw new IllegalStateException("REPOST requires a confirmed (paid) collection");
        }
        ReconciliationAction a = new ReconciliationAction();
        a.setTransactionReference(reference);
        a.setAction(req.getAction());
        a.setStatus(EApprovalStatus.PENDING_APPROVAL);
        a.setMaker(maker);
        a.setMakerComment(req.getComment());
        a.setFineractTxnId(req.getFineractTxnId());
        a.setCreatedAt(DateUtility.nowKigali());
        a = actionRepo.save(a);
        audit.admin(maker, "RECON_PROPOSED", reference, true, req.getAction() + ": " + req.getComment());
        return a;
    }

    public ReconciliationAction approve(Long actionId, String checker, String comment) {
        ReconciliationAction a = pendingAction(actionId, checker);
        RepaymentTransaction t = txRepo.findByReference(a.getTransactionReference()).orElseThrow();

        String result;
        switch (a.getAction()) {
            case REPOST -> {
                if (t.getStatus() != ETransactionStatus.POSTING_FAILED) {
                    t.setStatus(ETransactionStatus.POSTING_FAILED);
                    t.setUpdatedAt(DateUtility.nowKigali());
                    txRepo.save(t);
                }
                boolean posted = repaymentService.postToFineract(t.getReference());
                result = posted ? "Posted to Fineract" : "Posting failed — see transaction failureReason";
            }
            case MARK_POSTED -> {
                t.setStatus(ETransactionStatus.POSTED);
                t.setFineractTxnId(a.getFineractTxnId());
                t.setPostedAt(DateUtility.nowKigali());
                t.setUpdatedAt(DateUtility.nowKigali());
                txRepo.save(t);
                result = "Marked as posted (Fineract txn " + a.getFineractTxnId() + ")";
            }
            case MARK_FAILED -> {
                t.setStatus(ETransactionStatus.FAILED);
                t.setFailureReason("Closed by reconciliation: " + a.getMakerComment());
                t.setUpdatedAt(DateUtility.nowKigali());
                txRepo.save(t);
                result = "Marked as failed";
            }
            case MARK_REVERSED -> {
                t.setStatus(ETransactionStatus.REVERSED);
                t.setFailureReason("Reversed by reconciliation: " + a.getMakerComment());
                t.setUpdatedAt(DateUtility.nowKigali());
                txRepo.save(t);
                result = "Marked as reversed";
            }
            default -> throw new IllegalStateException("Unsupported action");
        }

        a.setStatus(EApprovalStatus.APPROVED);
        a.setChecker(checker);
        a.setCheckerComment(comment);
        a.setDecidedAt(DateUtility.nowKigali());
        a.setResult(result);
        a = actionRepo.save(a);
        audit.admin(checker, "RECON_APPROVED", a.getTransactionReference(), true, a.getAction() + ": " + result);
        return a;
    }

    public ReconciliationAction reject(Long actionId, String checker, String comment) {
        ReconciliationAction a = pendingAction(actionId, checker);
        a.setStatus(EApprovalStatus.REJECTED);
        a.setChecker(checker);
        a.setCheckerComment(comment);
        a.setDecidedAt(DateUtility.nowKigali());
        a = actionRepo.save(a);
        audit.admin(checker, "RECON_REJECTED", a.getTransactionReference(), true, comment);
        return a;
    }

    private ReconciliationAction pendingAction(Long actionId, String checker) {
        ReconciliationAction a = actionRepo.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Action not found: " + actionId));
        if (a.getStatus() != EApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Action already " + a.getStatus());
        }
        if (a.getMaker().equalsIgnoreCase(checker)) {
            throw new IllegalStateException("Checker must be a different user from the maker");
        }
        return a;
    }
}
