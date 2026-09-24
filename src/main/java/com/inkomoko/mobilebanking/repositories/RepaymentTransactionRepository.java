package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.enums.ETransactionStatus;
import com.inkomoko.mobilebanking.models.RepaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RepaymentTransactionRepository extends JpaRepository<RepaymentTransaction, Long> {

    Optional<RepaymentTransaction> findByReference(String reference);

    /** Row lock so concurrent / duplicate callbacks for the same payment are processed one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RepaymentTransaction r where r.reference = :reference")
    Optional<RepaymentTransaction> lockByReference(@Param("reference") String reference);

    boolean existsByMsisdnAndStatusInAndCreatedAtAfter(String msisdn, Collection<ETransactionStatus> statuses,
                                                       LocalDateTime after);

    List<RepaymentTransaction> findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(ETransactionStatus status,
                                                                                      LocalDateTime before);

    Page<RepaymentTransaction> findByStatusOrderByCreatedAtDesc(ETransactionStatus status, Pageable pageable);

    Page<RepaymentTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("select r.status, count(r), coalesce(sum(r.amount), 0) from RepaymentTransaction r " +
           "where r.createdAt between :from and :to group by r.status")
    List<Object[]> summarizeByStatus(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
