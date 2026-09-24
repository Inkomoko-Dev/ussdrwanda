package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.enums.EPinStatus;
import com.inkomoko.mobilebanking.models.ClientPin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ClientPinRepository extends JpaRepository<ClientPin, Long> {
    Optional<ClientPin> findByMsisdn(String msisdn);

    long countByStatus(EPinStatus status);

    long countByLockedAtBetween(LocalDateTime from, LocalDateTime to);
}
