package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.enums.ESessionEndReason;
import com.inkomoko.mobilebanking.models.UssdSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UssdSessionRepository extends JpaRepository<UssdSession, Long> {

    Optional<UssdSession> findBySessionIdAndMsisdn(String sessionId, String msisdn);

    /** Closes any other open sessions for this phone (a new dial supersedes them). */
    @Modifying
    @Transactional
    @Query("update UssdSession s set s.closeCurrentSession = true, s.endReason = coalesce(s.endReason, :reason), " +
           "s.endedAt = :now where s.msisdn = :msisdn and s.sessionId <> :sessionId and s.closeCurrentSession = false")
    int closeOtherOpenSessions(@Param("msisdn") String msisdn, @Param("sessionId") String sessionId,
                               @Param("reason") ESessionEndReason reason, @Param("now") LocalDateTime now);

    /** Closes sessions idle since {@code cutoff}; ones that never reached a goal screen become ABANDONED. */
    @Modifying
    @Transactional
    @Query("update UssdSession s set s.closeCurrentSession = true, s.endReason = coalesce(s.endReason, :reason), " +
           "s.endedAt = :now where s.closeCurrentSession = false and s.updatedAt < :cutoff")
    int closeIdleSessions(@Param("cutoff") LocalDateTime cutoff, @Param("reason") ESessionEndReason reason,
                          @Param("now") LocalDateTime now);

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    long countByCreatedAtBetweenAndEndReason(LocalDateTime from, LocalDateTime to, ESessionEndReason reason);

    long countByCreatedAtBetweenAndAuthenticatedTrue(LocalDateTime from, LocalDateTime to);

    @Query("select coalesce(sum(s.requestCount), 0) from UssdSession s where s.createdAt between :from and :to")
    long sumRequests(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(s.totalProcessingMs), 0) from UssdSession s where s.createdAt between :from and :to")
    long sumProcessingMs(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
