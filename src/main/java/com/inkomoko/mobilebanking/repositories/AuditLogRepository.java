package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.models.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    long countByActionAndLoggedAtBetween(String action, LocalDateTime from, LocalDateTime to);

    Page<AuditLog> findByActorOrderByLoggedAtDesc(String actor, Pageable pageable);

    Page<AuditLog> findAllByOrderByLoggedAtDesc(Pageable pageable);
}
