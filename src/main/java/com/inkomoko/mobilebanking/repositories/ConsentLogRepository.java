package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.models.ConsentLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ConsentLogRepository extends JpaRepository<ConsentLog, Long> {

    List<ConsentLog> findByMsisdnAndAcceptedTrue(String msisdn);

    List<ConsentLog> findByMsisdnInAndAcceptedTrue(Collection<String> msisdns);
}
