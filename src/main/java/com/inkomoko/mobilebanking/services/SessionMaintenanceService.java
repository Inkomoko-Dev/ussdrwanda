package com.inkomoko.mobilebanking.services;

import com.inkomoko.mobilebanking.config.InkomokoProperties;
import com.inkomoko.mobilebanking.enums.ESessionEndReason;
import com.inkomoko.mobilebanking.repositories.UssdSessionRepository;
import com.inkomoko.mobilebanking.utils.DateUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** Closes sessions that went quiet (network timeout / user walked away) and marks them ABANDONED. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionMaintenanceService {

    private final UssdSessionRepository repo;
    private final InkomokoProperties props;

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void closeIdleSessions() {
        LocalDateTime now = DateUtility.nowKigali();
        // small grace period on top of the configured inactivity timeout
        LocalDateTime cutoff = now.minusSeconds(props.getUssd().getSessionTimeoutSeconds() + 30L);
        int closed = repo.closeIdleSessions(cutoff, ESessionEndReason.ABANDONED, now);
        if (closed > 0) log.info("Marked {} idle USSD session(s) as abandoned", closed);
    }
}
