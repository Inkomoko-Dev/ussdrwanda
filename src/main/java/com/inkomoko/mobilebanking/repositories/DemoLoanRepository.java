package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.models.demo.DemoLoan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DemoLoanRepository extends JpaRepository<DemoLoan, Long> {

    List<DemoLoan> findByClientIdOrderByIdAsc(Long clientId);

    boolean existsByAccountNo(String accountNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from DemoLoan l where l.id = :id")
    Optional<DemoLoan> lockById(@Param("id") Long id);
}
