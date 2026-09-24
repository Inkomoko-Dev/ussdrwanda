package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.models.AccountAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountAttemptRepository extends JpaRepository<AccountAttempt, Long> {
    Optional<AccountAttempt> findByMsisdn(String msisdn);
}
