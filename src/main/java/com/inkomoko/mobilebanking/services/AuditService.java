package com.inkomoko.mobilebanking.services;

import com.inkomoko.mobilebanking.enums.EChannel;
import com.inkomoko.mobilebanking.models.AuditLog;
import com.inkomoko.mobilebanking.repositories.AuditLogRepository;
import com.inkomoko.mobilebanking.utils.DateUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes append-only audit records. Failures are logged, never propagated to the caller. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String actor, EChannel channel, String action, String resource, String sessionId,
                    boolean success, String detail) {
        try {
            repo.save(AuditLog.builder()
                    .actor(actor == null ? "UNKNOWN" : actor)
                    .channel(channel)
                    .action(action)
                    .resource(resource)
                    .sessionId(sessionId)
                    .success(success)
                    .detail(detail != null && detail.length() > 500 ? detail.substring(0, 500) : detail)
                    .loggedAt(DateUtility.nowKigali())
                    .build());
        } catch (Exception e) {
            log.error("Audit write failed action={} actor={} error={}", action, actor, e.getMessage());
        }
    }

    public void ussd(String msisdn, String sessionId, String action, String resource, boolean success, String detail) {
        log(msisdn, EChannel.USSD, action, resource, sessionId, success, detail);
    }

    public void system(String action, String resource, boolean success, String detail) {
        log("SYSTEM", EChannel.SYSTEM, action, resource, null, success, detail);
    }

    public void admin(String user, String action, String resource, boolean success, String detail) {
        log(user, EChannel.WEB, action, resource, null, success, detail);
    }
}
