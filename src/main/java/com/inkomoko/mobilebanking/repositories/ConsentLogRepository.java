package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.models.ConsentLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentLogRepository extends JpaRepository<ConsentLog, Long> {
}
