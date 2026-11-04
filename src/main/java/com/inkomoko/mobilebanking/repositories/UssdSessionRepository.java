package com.inkomoko.mobilebanking.repositories;

import com.inkomoko.mobilebanking.enums.ESessionEndReason;
import com.inkomoko.mobilebanking.models.UssdSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UssdSessionRepository extends JpaRepository<UssdSession, Long>, JpaSpecificationExecutor<UssdSession> {

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

    // ── USSD sessions page / billing ──────────────────────────────────────

    @Query("select count(distinct s.msisdn) from UssdSession s where s.createdAt between :from and :to")
    long countDistinctMsisdn(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** [endReason, closed, sessions] for the period. */
    @Query("select s.endReason, s.closeCurrentSession, count(s) from UssdSession s "
            + "where s.createdAt between :from and :to group by s.endReason, s.closeCurrentSession")
    List<Object[]> countByOutcome(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** [networkCode, sessions, distinct phones] for the period. */
    @Query("select s.networkCode, count(s), count(distinct s.msisdn) from UssdSession s "
            + "where s.createdAt between :from and :to group by s.networkCode order by count(s) desc")
    List<Object[]> countByNetwork(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** [day, sessions, distinct phones] per calendar day. */
    @Query(value = "select cast(created_at as date) as day, count(*), count(distinct msisdn) from ussd_session "
            + "where created_at between :from and :to group by cast(created_at as date) order by day", nativeQuery = true)
    List<Object[]> countByDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Visits per phone number: [msisdn, clientName, sessions, completed, leftOrTimedOut, notRegistered,
     * screens, firstVisit, lastVisit]. {@code msisdnPattern} is a LIKE pattern ("%" for all).
     */
    @Query(value = "select s.msisdn, max(s.clientName), count(s), "
            + "sum(case when s.endReason in (com.inkomoko.mobilebanking.enums.ESessionEndReason.COMPLETED, "
            + "com.inkomoko.mobilebanking.enums.ESessionEndReason.USER_EXIT) then 1 else 0 end), "
            + "sum(case when s.endReason = com.inkomoko.mobilebanking.enums.ESessionEndReason.ABANDONED then 1 else 0 end), "
            + "sum(case when s.endReason = com.inkomoko.mobilebanking.enums.ESessionEndReason.NOT_REGISTERED then 1 else 0 end), "
            + "coalesce(sum(s.requestCount), 0), min(s.createdAt), max(s.createdAt) "
            + "from UssdSession s where s.createdAt between :from and :to and s.msisdn like :msisdnPattern "
            + "group by s.msisdn order by count(s) desc, max(s.createdAt) desc",
            countQuery = "select count(distinct s.msisdn) from UssdSession s "
                    + "where s.createdAt between :from and :to and s.msisdn like :msisdnPattern")
    Page<Object[]> visitsByMsisdn(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                  @Param("msisdnPattern") String msisdnPattern, Pageable pageable);
}
