package com.inkomoko.mobilebanking.services;

import com.inkomoko.mobilebanking.enums.ESessionEndReason;
import com.inkomoko.mobilebanking.models.UssdSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Read-only queries against the local PostgreSQL database (inkomoko_db). */
@SpringBootTest
class SessionAnalyticsServiceTest {

    @Autowired
    SessionAnalyticsService service;

    private final LocalDateTime from = LocalDate.now().minusYears(1).atStartOfDay();
    private final LocalDateTime to = LocalDate.now().plusDays(1).atStartOfDay();

    @Test
    void queriesRunAndAgree() {
        var summary = service.summary(from, to);
        long byStatus = summary.byStatus().values().stream().mapToLong(Long::longValue).sum();
        assertEquals(summary.sessions(), byStatus);
        assertEquals(summary.sessions(), summary.byDay().stream().mapToLong(SessionAnalyticsService.DayCount::sessions).sum());

        var phones = service.visitsByMsisdn(from, to, null, PageRequest.of(0, 500));
        assertEquals(summary.uniquePhones(), phones.getTotalElements());
        assertEquals(summary.sessions(), phones.getContent().stream().mapToLong(SessionAnalyticsService.MsisdnVisits::sessions).sum());

        var log = service.sessions(from, to, null, null, PageRequest.of(0, 10));
        assertEquals(summary.sessions(), log.getTotalElements());
        service.sessions(from, to, "0785", "IN_PROGRESS", PageRequest.of(0, 10));
        service.sessions(from, to, null, "abandoned", PageRequest.of(0, 10));

        assertTrue(service.sessionsCsv(from, to, null, null, 10).startsWith("started_at,"));
        assertTrue(service.visitsCsv(from, to, null, 10).startsWith("msisdn,"));
    }

    @Test
    void statusAndLastScreen() {
        assertEquals("IN_PROGRESS", SessionAnalyticsService.status(null, false));
        assertEquals("ENDED", SessionAnalyticsService.status(null, true));
        assertEquals("ABANDONED", SessionAnalyticsService.status(ESessionEndReason.ABANDONED, true));

        UssdSession s = new UssdSession();
        s.setLevel(3);
        s.setStep("PIN_CONFIRM");
        assertEquals("PIN setup", SessionAnalyticsService.lastScreen(s));
        s.setLevel(5);
        s.setService("repay");
        s.setStep("CONFIRM_AMOUNT");
        assertEquals("Repay loan > Confirm amount", SessionAnalyticsService.lastScreen(s));
    }
}
