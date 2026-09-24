package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.models.demo.DemoRepayment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DemoRepaymentRepository extends JpaRepository<DemoRepayment, Long> {

    List<DemoRepayment> findByLoanIdOrderByTxnDateDescIdDesc(Long loanId, Pageable pageable);

    Optional<DemoRepayment> findByLoanIdAndReference(Long loanId, String reference);

    boolean existsByLoanIdAndReference(Long loanId, String reference);
}
