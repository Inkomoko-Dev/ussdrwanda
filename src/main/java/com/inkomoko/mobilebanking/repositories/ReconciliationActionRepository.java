package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.enums.EApprovalStatus;
import com.inkomoko.mobilebanking.models.ReconciliationAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconciliationActionRepository extends JpaRepository<ReconciliationAction, Long> {

    boolean existsByTransactionReferenceAndStatus(String reference, EApprovalStatus status);

    List<ReconciliationAction> findByStatusOrderByCreatedAtAsc(EApprovalStatus status);

    List<ReconciliationAction> findByTransactionReferenceOrderByCreatedAtDesc(String reference);
}
